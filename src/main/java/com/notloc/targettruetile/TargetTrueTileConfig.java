package com.notloc.targettruetile;

import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.Perspective;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;

import javax.inject.Inject;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

class TargetTrueTileOverlay extends Overlay
{
	private final Client client;
	private final TargetTrueTilePlugin plugin;
	private final TargetTrueTileConfig config;
	private final ImprovedTileIndicatorsUtil improvedTileIndicatorsUtil = new ImprovedTileIndicatorsUtil();

	// Reused every frame instead of allocated fresh - cleared, not replaced.
	private final List<NPC> renderList = new ArrayList<>();
	private final List<Polygon> renderPolyList = new ArrayList<>();

	@Inject
	private TargetTrueTileOverlay(Client client, TargetTrueTilePlugin plugin, TargetTrueTileConfig config)
	{
		this.client = client;
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
		setPriority(0.6f);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		WorldView worldView = client.getTopLevelWorldView();
		if (worldView == null)
		{
			return null;
		}

		renderList.clear();
		renderPolyList.clear();

		Set<Integer> targetIndices = plugin.getTargetRegistry().getTrackedIndices();
		for (int npcIndex : targetIndices)
		{
			NPC npc = worldView.npcs().byIndex(npcIndex);
			if (npc == null)
			{
				continue;
			}
			Polygon poly = renderTrueTile(graphics, worldView, npc);
			if (poly != null)
			{
				renderList.add(npc);
				renderPolyList.add(poly);
			}
		}

		if (config.highlightOnHover())
		{
			NPC mousedNpc = plugin.findNpcUnderMouse();
			if (mousedNpc != null && !targetIndices.contains(mousedNpc.getIndex()))
			{
				Polygon p = renderTrueTile(graphics, worldView, mousedNpc);
				if (p != null)
				{
					renderList.add(mousedNpc);
					renderPolyList.add(p);
				}
			}
		}

		for (int npcIndex : plugin.getTaggedNpcIndices())
		{
			if (targetIndices.contains(npcIndex))
			{
				continue;
			}
			NPC npc = worldView.npcs().byIndex(npcIndex);
			if (npc == null)
			{
				continue;
			}
			Polygon p = renderTrueTile(graphics, worldView, npc);
			if (p != null)
			{
				renderList.add(npc);
				renderPolyList.add(p);
			}
		}

		if (client.isGpu() && config.improvedTileRendering())
		{
			for (NPC npc : renderList)
			{
				improvedTileIndicatorsUtil.removeNpcFast(client, graphics, npc, renderPolyList);
			}
			improvedTileIndicatorsUtil.removePlayerFast(client, graphics, client.getLocalPlayer(), renderPolyList);
		}

		return null;
	}

	private Polygon renderTrueTile(Graphics2D graphics, WorldView worldView, NPC npc)
	{
		if (npc.getComposition() == null)
		{
			return null;
		}

		WorldPoint worldPoint = npc.getWorldLocation();
		LocalPoint point = LocalPoint.fromWorld(worldView, worldPoint);
		if (point == null)
		{
			return null;
		}

		int size = npc.getComposition().getSize();

		// 128 units per square, offset position to align larger enemies
		LocalPoint renderPoint = new LocalPoint(point.getX() + 128 * size / 2 - 64, point.getY() + 128 * size / 2 - 64, worldView);

		if (config.showCorner() && (!config.showCornerOnlyLarge() || size > 1))
		{
			Polygon cornerPoly = PerspectiveUtil.getCanvasTileMarkPoly(client, worldView, renderPoint, size, config.tileCornerLength() * size);
			if (cornerPoly != null)
			{
				OverlayUtil.renderPolygon(graphics, cornerPoly, config.tileCornerColor(), config.tileCornerColor(), new BasicStroke(config.borderSize()));
			}
		}

		Polygon poly = Perspective.getCanvasTileAreaPoly(client, renderPoint, size);
		if (poly != null)
		{
			switch (config.borderStyle())
			{
				case OUTLINE:
					OverlayUtil.renderPolygon(graphics, poly, config.tileColor(), config.tileFillColor(), new BasicStroke(config.borderSize()));
					break;
				case CORNERS:
					Color noBorder = new Color(0, 0, 0, 0);
					OverlayUtil.renderPolygon(graphics, poly, noBorder, config.tileFillColor(), new BasicStroke(config.borderSize()));
					renderCornersForTile(graphics, poly, config.tileColor(), config.borderSize());
					break;
			}
		}

		return poly;
	}

	private void renderCornersForTile(Graphics2D graphics, Polygon tilePoly, Color color, int borderSize)
	{
		graphics.setColor(color);
		graphics.setStroke(new BasicStroke(borderSize));

		float length = config.borderLength() / 100.0f;

		for (int i = 0; i < tilePoly.npoints; i++)
		{
			int x = tilePoly.xpoints[i];
			int y = tilePoly.ypoints[i];

			int prevX = i - 1 < 0 ? tilePoly.xpoints[tilePoly.npoints - 1] : tilePoly.xpoints[i - 1];
			int prevY = i - 1 < 0 ? tilePoly.ypoints[tilePoly.npoints - 1] : tilePoly.ypoints[i - 1];
			int nextX = i + 1 >= tilePoly.npoints ? tilePoly.xpoints[0] : tilePoly.xpoints[i + 1];
			int nextY = i + 1 >= tilePoly.npoints ? tilePoly.ypoints[0] : tilePoly.ypoints[i + 1];

			renderPartialLine(graphics, x, y, prevX, prevY, length);
			renderPartialLine(graphics, x, y, nextX, nextY, length);
		}
	}

	private static void renderPartialLine(Graphics2D graphics, int x1, int y1, int x2, int y2, float length)
	{
		int deltaX = Math.round((x2 - x1) * length);
		int deltaY = Math.round((y2 - y1) * length);
		graphics.drawLine(x1, y1, x1 + deltaX, y1 + deltaY);
	}
}
