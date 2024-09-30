package com.notloc.targettruetile;

import java.awt.*;
import javax.inject.Inject;

import net.runelite.api.*;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

import net.runelite.api.Client;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;
import java.util.List;

class TargetTrueTileOverlay extends Overlay {
    private final Client client;
    private final TargetTrueTilePlugin plugin;
    private final TargetTrueTileConfig config;

    private final List<NPC> renderList = new ArrayList<>();
    private final List<Polygon> renderPolyList = new ArrayList<>();

    @Inject
    private TargetTrueTileOverlay(Client client, TargetTrueTilePlugin plugin, TargetTrueTileConfig config) {
        this.client = client;
        this.plugin = plugin;
        this.config = config;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_SCENE);
        setPriority(0.6f);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        Set<NPC> npcs = plugin.getTargetMemory().getNpcs();
        renderPolyList.addAll(renderTrueTiles(graphics, npcs));
        renderList.addAll(npcs);

        if (config.highlightOnHover()) {
            NPC mousedNpc = plugin.findNpcUnderMouse();
            if (mousedNpc != null && !npcs.contains(mousedNpc)) {
                Polygon p = renderTrueTile(graphics, mousedNpc);
                if (p != null) {
                    renderList.add(mousedNpc);
                    renderPolyList.add(p);
                }
            }
        }

        for (NPC npc : plugin.getTaggedNpcs()) {
            if (!npcs.contains(npc)) {
                Polygon p = renderTrueTile(graphics, npc);
                if (p != null) {
                    renderList.add(npc);
                    renderPolyList.add(p);
                }
            }
        }

        if (client.isGpu() && config.improvedTileRendering()) {
            for (NPC npc : renderList) {
                ImprovedTileIndicatorsUtil.removeActorFast(client, graphics, npc, renderPolyList);
            }
            ImprovedTileIndicatorsUtil.removeActorFast(client, graphics, client.getLocalPlayer(), renderPolyList);
        }

        renderList.clear();
        renderPolyList.clear();
        return null;
    }

    private Polygon renderTrueTile(Graphics2D graphics, NPC npc) {
        return renderTrueTileForNpc(graphics, npc, config.tileColor(), config.tileFillColor(), config.tileCornerColor(), config.tileCornerLength(), config.borderSize());
    }

    private List<Polygon> renderTrueTiles(Graphics2D graphics, Collection<NPC> npcs) {
        List<Polygon> polygons = new ArrayList<>();
        for (NPC npc : npcs) {
            Polygon polygon = renderTrueTileForNpc(graphics, npc, config.tileColor(), config.tileFillColor(), config.tileCornerColor(), config.tileCornerLength(), config.borderSize());
            if (polygon != null) {
                polygons.add(polygon);
            }
        }
        return polygons;
    }

    private Polygon renderTrueTileForNpc(Graphics2D graphics, NPC npc, Color borderColor, Color innerColor, Color swColor, int cornerLength, int borderSize) {
        if (npc.getComposition() == null) {
            return null;
        }

        WorldView worldView = client.getTopLevelWorldView();
        if (worldView == null) {
            return null;
        }

        WorldPoint target = npc.getWorldLocation();
        LocalPoint point = LocalPoint.fromWorld(worldView, target);
        if (point == null) {
            return null;
        }

        int size = npc.getComposition().getSize();

        // 128 units per square, offset position to align larger enemies
        LocalPoint renderPoint = new LocalPoint(point.getX() + 128*size/2 - 64, point.getY() + 128*size/2 - 64, worldView);
        Polygon poly;

        if (config.showCorner() && (!config.showCornerOnlyLarge() || size > 1)) {
            // Marks the SW corner mark of the tile
            poly = PerspectiveUtil.getCanvasTileMarkPoly(client, renderPoint, size, cornerLength * size);
            if (poly != null) {
                OverlayUtil.renderPolygon(graphics, poly, swColor, swColor, new BasicStroke(borderSize));
            }
        }

        poly = Perspective.getCanvasTileAreaPoly(client, renderPoint, size);
        if (poly != null) {
            switch (config.borderStyle()) {
                case OUTLINE:
                    OverlayUtil.renderPolygon(graphics, poly, borderColor, innerColor, new BasicStroke(borderSize));
                    break;
                case CORNERS:
                    Color noBorder = new Color(0, 0, 0, 0);
                    OverlayUtil.renderPolygon(graphics, poly, noBorder, innerColor, new BasicStroke(borderSize));
                    renderCornersForTile(graphics, poly, borderColor, borderSize);
                    break;
            }
        }

        return poly;
    }

    private void renderCornersForTile(Graphics2D graphics, Polygon tilePoly, Color color, int borderSize) {
        graphics.setColor(color);
        graphics.setStroke(new BasicStroke(borderSize));

        float length = config.borderLength() / 100.0f;

        for (int i = 0; i < tilePoly.npoints; i++) {
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

    private static void renderPartialLine(Graphics2D graphics, int x1, int y1, int x2, int y2, float length) {
        int deltaX = Math.round((x2 - x1) * length);
        int deltaY = Math.round((y2 - y1) * length);
        graphics.drawLine(x1, y1, x1 + deltaX, y1 + deltaY);
    }
}
