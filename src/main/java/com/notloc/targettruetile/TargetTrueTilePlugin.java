package com.notloc.targettruetile;

import com.google.inject.Provides;
import javax.inject.Inject;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.*;
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

import java.util.*;

@Slf4j
@PluginDescriptor(
	name = "Target True Tile",
	description = "Dynamically highlights the true tile of enemies during combat. Compatible with NPC Indicator's tags."
)
public class TargetTrueTilePlugin extends Plugin
{
	@Provides
	TargetTrueTileConfig provideConfig(ConfigManager configManager) { return configManager.getConfig(TargetTrueTileConfig.class); }

	@Inject private Client client;
	@Inject private TargetTrueTileConfig config;
	@Inject private TargetTrueTileOverlay overlay;
	@Inject private OverlayManager overlayManager;
	@Inject private ConfigManager configManager;
	@Inject private ClientThread clientThread;

	@Getter
	private NPC target;
	@Getter
	private final TargetMemory targetMemory = new TargetMemory();
	@Getter
	private final Set<NPC> taggedNpcs = new HashSet<>();

	// NPCs tagged by name via the NPC Indicators plugin
	private Set<String> taggedNames = new HashSet<>();

	// Specific NPCs tagged during this session.
	private final Set<Integer> taggedIndexes = new HashSet<>();

	@Override
	protected void startUp() {
		overlayManager.add(overlay);
		clientThread.invokeLater(this::rebuildTaggedTargets);
	}

	@Override
	protected void shutDown() {
		overlayManager.remove(overlay);
		targetMemory.forgetAll();
		target = null;
		taggedNpcs.clear();
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged gameStateChanged) {
		if (gameStateChanged.getGameState() == GameState.LOGIN_SCREEN || gameStateChanged.getGameState() == GameState.HOPPING) {
			target = null;
			targetMemory.forgetAll();
			taggedNpcs.clear();
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event) {
		String group = event.getGroup();
		if (!group.equals(TargetTrueTileConfig.CONFIG_GROUP) && !group.equals(NpcIndicatorsConfig.GROUP)) {
			return;
		}
		clientThread.invokeLater(this::rebuildTaggedTargets);
	}

	@Subscribe
	public void onGameTick(GameTick e) {
		processCurrentTarget();
		targetMemory.forgetOldTargets(config.targetTimeout());
	}

	@Subscribe
	public void onNpcSpawned(NpcSpawned npcSpawned)
	{
		final NPC npc = npcSpawned.getNpc();
		if (isTaggedNpc(npc)) {
			taggedNpcs.add(npc);
		}
	}

	@Subscribe
	public void onNpcDespawned(NpcDespawned npcDespawned)
	{
		final NPC npc = npcDespawned.getNpc();
		taggedNpcs.remove(npc);
		targetMemory.forgetTarget(npc);
	}

	@Subscribe
	public void onInteractingChanged(InteractingChanged interactingChanged) {
		Actor source = interactingChanged.getSource();
		Actor newTarget = interactingChanged.getTarget();

		if (source != client.getLocalPlayer()) {
			return;
		}

		this.target = null;
		if (newTarget instanceof NPC) {
			this.target = (NPC)newTarget;
		}
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event) {
		boolean doTag = "Tag".equals(event.getMenuOption());
		if (doTag || "Un-tag".equals(event.getMenuOption())) {
			int index = event.getId();
			clientThread.invokeLater(() -> {
				WorldView worldView = client.getTopLevelWorldView();
				if (worldView == null) {
					return;
				}

				NPC npc = worldView.npcs().byIndex(index);
				if (npc != null) {
					toggleIndexTag(npc, doTag);
				}
			});
		}
	}

	private void toggleIndexTag(NPC npc, boolean state) {
		if (state) {
			taggedIndexes.add(npc.getIndex());
		} else {
			taggedIndexes.remove(npc.getIndex());
		}
		rebuildTaggedTargets();
	}

	private void rebuildTaggedTargets() {
		taggedNpcs.clear();
		taggedNames.clear();

		NpcIndicatorsConfig npcIndicatorsConfig = configManager.getConfig(NpcIndicatorsConfig.class);
		taggedNames = new HashSet<>(Text.fromCSV(npcIndicatorsConfig.getNpcToHighlight()));

		if (client.getGameState() == GameState.LOGGED_IN || client.getGameState() == GameState.LOADING) {
			WorldView worldView = client.getTopLevelWorldView();
			if (worldView == null) {
				return;
			}

			worldView.npcs().forEach(npc -> {
				if (isTaggedNpc(npc)) {
					taggedNpcs.add(npc);
				}
			});
		}
	}

	private void processCurrentTarget() {
		if (target == null) {
			return;
		}

		if (isValidTarget(target)) {
			if (!config.allowMultipleTargets()) {
				targetMemory.forgetAll();
			}
			targetMemory.acknowledgeTarget(target);
		} else {
			targetMemory.forgetTarget(target);
			target = null;
		}
	}

	public NPC findNpcUnderMouse() {
		MenuEntry[] menuEntries = client.getMenu().getMenuEntries();
		if (menuEntries.length == 0) {
			return null;
		}

		MenuEntry entry = menuEntries[menuEntries.length - 1];
		NPC npc = entry.getNpc();
		return isValidTarget(npc) ? npc : null;
	}

	private boolean isTaggedNpc(NPC npc) {
		if (!config.inheritTaggedNpcs()) {
			return false;
		}

		if (taggedIndexes.contains(npc.getIndex())) {
			return true;
		}

		String name = npc.getName();
		if (name != null) {
			for (String manual : taggedNames) {
				if (WildcardMatcher.matches(manual, name)) {
					return true;
				}
			}
		}
		return false;
	}

	private boolean isValidTarget(NPC npc) {
		if (npc == null) {
			return false;
		}
		return (config.highlightFriendlies() || npc.getCombatLevel() > 0);
	}
}
