/*
 * Copyright (c) 2021, LeikvollE
 * Copyright (c) 2018, Tomas Slusny <slusnucky@gmail.com>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.notloc.targettruetile;

import java.awt.*;

import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;

// Taken from Improved Tile Indicators
// https://github.com/LeikvollE/tileindicators

// Allows us to display tile indicators beneath actors
public class ImprovedTileIndicatorsUtil {
    public static void removeActor(final Client client, final Graphics2D graphics, final Actor actor) {
        final int clipX1 = client.getViewportXOffset();
        final int clipY1 = client.getViewportYOffset();
        final int clipX2 = client.getViewportWidth() + clipX1;
        final int clipY2 = client.getViewportHeight() + clipY1;
        Object origAA = graphics.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        graphics.setRenderingHint(
                RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_OFF);
        Model model = actor.getModel();
        int vCount = model.getVerticesCount();
        int[] x3d = model.getVerticesX();
        int[] y3d = model.getVerticesY();
        int[] z3d = model.getVerticesZ();

        int[] x2d = new int[vCount];
        int[] y2d = new int[vCount];

        int size = 1;
        if (actor instanceof NPC)
        {
            NPCComposition composition = ((NPC) actor).getTransformedComposition();
            if (composition != null)
            {
                size = composition.getSize();
            }
        }

        final LocalPoint lp = actor.getLocalLocation();

        final int localX = lp.getX();
        final int localY = lp.getY();
        final int northEastX = lp.getX() + Perspective.LOCAL_TILE_SIZE * (size - 1) / 2;
        final int northEastY = lp.getY() + Perspective.LOCAL_TILE_SIZE * (size - 1) / 2;
        final LocalPoint northEastLp = new LocalPoint(northEastX, northEastY);
        int localZ = Perspective.getTileHeight(client, northEastLp, client.getPlane());
        int rotation = actor.getCurrentOrientation();

        Perspective.modelToCanvas(client, vCount, localX, localY, localZ, rotation, x3d, z3d, y3d, x2d, y2d);

        boolean anyVisible = false;

        for (int i = 0; i < vCount; i++) {
            int x = x2d[i];
            int y = y2d[i];

            boolean visibleX = x >= clipX1 && x < clipX2;
            boolean visibleY = y >= clipY1 && y < clipY2;
            anyVisible |= visibleX && visibleY;
        }

        if (!anyVisible) return;

        int tCount = model.getFaceCount();
        int[] tx = model.getFaceIndices1();
        int[] ty = model.getFaceIndices2();
        int[] tz = model.getFaceIndices3();

        final byte[] triangleTransparencies = model.getFaceTransparencies();

        Composite orig = graphics.getComposite();
        graphics.setComposite(AlphaComposite.Clear);
        graphics.setColor(Color.WHITE);
        for (int i = 0; i < tCount; i++) {
            // Cull tris facing away from the camera
            if (getTriDirection(x2d[tx[i]], y2d[tx[i]], x2d[ty[i]], y2d[ty[i]], x2d[tz[i]], y2d[tz[i]]) >= 0)
            {
                continue;
            }
            if (triangleTransparencies == null || (triangleTransparencies[i] & 255) < 254) {
                Polygon p = new Polygon(
                        new int[]{x2d[tx[i]], x2d[ty[i]], x2d[tz[i]]},
                        new int[]{y2d[tx[i]], y2d[ty[i]], y2d[tz[i]]},
                        3);
                graphics.fill(p);
            }
        }
        graphics.setComposite(orig);
        graphics.setRenderingHint(
                RenderingHints.KEY_ANTIALIASING,
                origAA);
    }

    private static int getTriDirection(int x1, int y1, int x2, int y2, int x3, int y3) {
        int x4 = x2 - x1;
        int y4 = y2 - y1;
        int x5 = x3 - x1;
        int y5 = y3 - y1;
        return x4 * y5 - y4 * x5;
    }
}
