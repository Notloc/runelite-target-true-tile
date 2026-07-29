package com.notloc.targettruetile;

import com.google.inject.Provides;
import javax.inject.Inject;

import com.google.inject.Provides;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.MenuEntry;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.WorldView;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.InteractingChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.npchighlight.NpcIndicatorsConfig;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.Text;
import net.runelite.client.util.WildcardMatcher;

import javax.inject.Inject;
import java.util.HashSet;
import java.util.Set;

/**
 * A rewrite of Notloc's Target True Tile.
 * <p>
 * The original tracked NPCs by holding live {@code NPC} object references in long-lived
 * {@code Set}/{@code Map} fields, and re-derived a full-model, per-frame occlusion mesh
 * (via {@link ImprovedTileIndicatorsUtil}) using fresh collections every frame. Under a
 * big-model boss that's a lot of retained/garbage-generating state, and it's what got the
 * plugin disabled for destabilizing the client. This version tracks NPCs purely by index
 * (see {@link TargetRegistry}) so nothing outlives its NPC, and reuses its rendering
 * scratch buffers instead of reallocating them every frame.
 */
@Slf4j
@PluginDescriptor(
	name = "Improved Target True Tile",
	description = "Dynamically highlights the true tile of enemies during combat. Compatible with NPC Indicator's tags."
)
public class TargetTrueTilePlugin extends Plugin
{
	private static final float GAME_TICK_SECONDS = 0.6f;

	@Provides
	TargetTrueTileConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(TargetTrueTileConfig.class);
	}

	@Inject
	private Client client;
	@Inject
	private TargetTrueTileConfig config;
	@Inject
	private TargetTrueTileOverlay overlay;
	@Inject
	private OverlayManager overlayManager;
	@Inject
	private ConfigManager configManager;
	@Inject
	private ClientThread clientThread;

	@Getter
	private final TargetRegistry targetRegistry = new TargetRegistry();

	// NPC indices tagged either manually (via the right-click Tag option) or inherited
	// from NPC Indicators' name list. Indices only - never a cached NPC reference.
	@Getter
	private final Set<Integer> taggedNpcIndices = new HashSet<>();

	private final Set<Integer> manuallyTaggedIndices = new HashSet<>();
	private final Set<String> taggedNames = new HashSet<>();

	private Integer currentTargetIndex;
	private int tickCount;

	@Override
	protected void startUp()
	{
		tickCount = 0;
		overlayManager.add(overlay);
		clientThread.invokeLater(this::rebuildTaggedTargets);
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(overlay);
		targetRegistry.forgetAll();
		taggedNpcIndices.clear();
		manuallyTaggedIndices.clear();
		currentTargetIndex = null;
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged gameStateChanged)
	{
		GameState state = gameStateChanged.getGameState();
		if (state == GameState.LOGIN_SCREEN || state == GameState.HOPPING)
		{
			currentTargetIndex = null;
			targetRegistry.forgetAll();
			taggedNpcIndices.clear();
			manuallyTaggedIndices.clear();
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		String group = event.getGroup();
		if (!group.equals(TargetTrueTileConfig.CONFIG_GROUP) && !group.equals(NpcIndicatorsConfig.GROUP))
		{
			return;
		}
		clientThread.invokeLater(this::rebuildTaggedTargets);
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		tickCount++;
		processCurrentTarget();

		WorldView worldView = client.getTopLevelWorldView();
		int timeoutTicks = Math.round(config.targetTimeout() / GAME_TICK_SECONDS);
		targetRegistry.pruneExpired(worldView, tickCount, timeoutTicks);
	}

	@Subscribe
	public void onNpcSpawned(NpcSpawned npcSpawned)
	{
		NPC npc = npcSpawned.getNpc();
		if (isTaggedNpc(npc))
		{
			taggedNpcIndices.add(npc.getIndex());
		}
	}

	@Subscribe
	public void onNpcDespawned(NpcDespawned npcDespawned)
	{
		int index = npcDespawned.getNpc().getIndex();
		taggedNpcIndices.remove(index);
		targetRegistry.forget(index);
		if (currentTargetIndex != null && currentTargetIndex == index)
		{
			currentTargetIndex = null;
		}
	}

	@Subscribe
	public void onInteractingChanged(InteractingChanged interactingChanged)
	{
		Actor source = interactingChanged.getSource();
		if (source != client.getLocalPlayer())
		{
			return;
		}

		Actor newTarget = interactingChanged.getTarget();
		currentTargetIndex = newTarget instanceof NPC ? ((NPC) newTarget).getIndex() : null;
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		boolean doTag = "Tag".equals(event.getMenuOption());
		if (!doTag && !"Un-tag".equals(event.getMenuOption()))
		{
			return;
		}

		int index = event.getId();
		clientThread.invokeLater(() -> {
			WorldView worldView = client.getTopLevelWorldView();
			if (worldView == null)
			{
				return;
			}

			NPC npc = worldView.npcs().byIndex(index);
			if (npc != null)
			{
				toggleIndexTag(npc, doTag);
			}
		});
	}

	private void toggleIndexTag(NPC npc, boolean state)
	{
		if (state)
		{
			manuallyTaggedIndices.add(npc.getIndex());
		}
		else
		{
			manuallyTaggedIndices.remove(npc.getIndex());
		}
		rebuildTaggedTargets();
	}

	private void rebuildTaggedTargets()
	{
		taggedNpcIndices.clear();
		taggedNames.clear();

		taggedNames.addAll(Text.fromCSV(config.npcNames()));

		NpcIndicatorsConfig npcIndicatorsConfig = configManager.getConfig(NpcIndicatorsConfig.class);
		if (npcIndicatorsConfig != null)
		{
			taggedNames.addAll(Text.fromCSV(npcIndicatorsConfig.getNpcToHighlight()));
		}

		GameState state = client.getGameState();
		if (state != GameState.LOGGED_IN && state != GameState.LOADING)
		{
			return;
		}

		WorldView worldView = client.getTopLevelWorldView();
		if (worldView == null)
		{
			return;
		}

		worldView.npcs().forEach(npc -> {
			if (isTaggedNpc(npc))
			{
				taggedNpcIndices.add(npc.getIndex());
			}
		});
	}

	private void processCurrentTarget()
	{
		if (currentTargetIndex == null)
		{
			return;
		}

		WorldView worldView = client.getTopLevelWorldView();
		NPC npc = worldView == null ? null : worldView.npcs().byIndex(currentTargetIndex);

		if (isValidTarget(npc))
		{
			if (!config.allowMultipleTargets())
			{
				targetRegistry.forgetAll();
			}
			targetRegistry.acknowledge(currentTargetIndex, tickCount);
		}
		else
		{
			targetRegistry.forget(currentTargetIndex);
			currentTargetIndex = null;
		}
	}

	NPC findNpcUnderMouse()
	{
		MenuEntry[] menuEntries = client.getMenu().getMenuEntries();
		if (menuEntries.length == 0)
		{
			return null;
		}

		MenuEntry entry = menuEntries[menuEntries.length - 1];
		NPC npc = entry.getNpc();
		return isValidTarget(npc) ? npc : null;
	}

	private boolean isTaggedNpc(NPC npc)
	{
		if (!config.inheritTaggedNpcs())
		{
			return false;
		}

		if (manuallyTaggedIndices.contains(npc.getIndex()))
		{
			return true;
		}

		String name = npc.getName();
		if (name != null)
		{
			for (String manual : taggedNames)
			{
				if (WildcardMatcher.matches(manual, name))
				{
					return true;
				}
			}
		}
		return false;
	}

	private boolean isValidTarget(NPC npc)
	{
		if (npc == null)
		{
			return false;
		}
		return config.highlightFriendlies() || isNpcAttackable(npc);
	}

	private boolean isNpcAttackable(NPC npc)
	{
		NPCComposition composition = npc.getComposition();
		if (composition == null || composition.getActions() == null)
		{
			return false;
		}
		// Actions is a sparse array, the second element represents the attack option.
		String[] actions = composition.getActions();
		return actions.length > 1 && actions[1] != null;
	}
}
