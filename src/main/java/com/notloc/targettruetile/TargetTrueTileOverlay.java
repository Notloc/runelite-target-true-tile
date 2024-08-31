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
        setPriority(PRIORITY_HIGH);
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

    private Polygon renderTrueTileForNpc(Graphics2D graphics, NPC npc, Color color, Color innerColor, Color cornerColor, int cornerLength, int borderSize) {
        if (npc.getComposition() == null) {
            return null;
        }

        if (!isNpcStillInWorld(npc)) {
            return null;
        }

        WorldPoint target = npc.getWorldLocation();
        LocalPoint point = LocalPoint.fromWorld(client, target);
        if (point == null) {
            return null;
        }

        int size = npc.getComposition().getSize();

        // 128 units per square, offset position to align larger enemies
        LocalPoint renderPoint = new LocalPoint(point.getX() + 128*size/2 - 64, point.getY() + 128*size/2 - 64);
        Polygon poly;

        if (config.showCorner() && (!config.showCornerOnlyLarge() || size > 1)) {
            // Marks the SW corner of the tile
            poly = PerspectiveUtil.getCanvasTileMarkPoly(client, renderPoint, size, cornerLength * size);
            if (poly != null) {
                OverlayUtil.renderPolygon(graphics, poly, cornerColor, cornerColor, new BasicStroke(borderSize));
            }
        }

        poly = Perspective.getCanvasTileAreaPoly(client, renderPoint, size);
        if (poly != null) {
            OverlayUtil.renderPolygon(graphics, poly, color, innerColor, new BasicStroke(borderSize));
        }

        return poly;
    }

    private static boolean isNpcStillInWorld(NPC npc) {
        WorldView worldView = npc.getWorldView();
        if (worldView == null) {
            return false;
        }
        // An ugly check that ensures the NPC is still in the world view
        return worldView.npcs().getSparse()[npc.getIndex()] != null;
    }
}
