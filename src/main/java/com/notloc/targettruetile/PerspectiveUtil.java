/*
 * Copyright (c) 2017, Adam <Adam@sigterm.info>
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

import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.Scene;
import net.runelite.api.coords.LocalPoint;

import javax.annotation.Nonnull;
import java.awt.*;

public class PerspectiveUtil {

    public static Polygon getCanvasTileMarkPoly(@Nonnull Client client, @Nonnull LocalPoint localLocation, int size, int length_128) {
        return getCanvasTileMarkPoly(client, localLocation, size, size, length_128, client.getTopLevelWorldView().getPlane(), 0);
    }

    // Adapted from runelite-api Perspective.java
    // Creates a triangle on the ground instead of a square
    public static Polygon getCanvasTileMarkPoly(@Nonnull Client client, @Nonnull LocalPoint localLocation, int sizeX, int sizeY, int length_128, int plane, int zOffset) {
        int msx = localLocation.getSceneX() + 40;
        int msy = localLocation.getSceneY() + 40;
        if (msx >= 0 && msy >= 0 && msx < 184 && msy < 184) {
            Scene scene = client.getTopLevelWorldView().getScene();
            byte[][][] tileSettings = scene.getExtendedTileSettings();
            int tilePlane = plane;
            if (plane < 3 && (tileSettings[1][msx][msy] & 2) == 2) {
                tilePlane = plane + 1;
            }

            int swX = localLocation.getX() - sizeX * 128 / 2;
            int swY = localLocation.getY() - sizeY * 128 / 2;
            int neX = localLocation.getX() + sizeX * 128 / 2;
            int neY = localLocation.getY() + sizeY * 128 / 2;
            int swHeight = getHeight(scene, swX, swY, tilePlane) - zOffset;
            int nwHeight = getHeight(scene, neX, swY, tilePlane) - zOffset;
            int seHeight = getHeight(scene, swX, neY, tilePlane) - zOffset;

            int eX = swX + length_128;
            int nY = swY + length_128;

            int exHeight = lerpInt128(swHeight, seHeight, length_128/sizeX);
            int nYHeight = lerpInt128(swHeight, nwHeight, length_128/sizeY);

            net.runelite.api.Point p1 = Perspective.localToCanvas(client, swX, swY, swHeight);
            net.runelite.api.Point p2 = Perspective.localToCanvas(client, eX, swY, nYHeight);
            Point p3 = Perspective.localToCanvas(client, swX, nY, exHeight);
            if (p1 != null && p2 != null && p3 != null) {
                Polygon poly = new Polygon();
                poly.addPoint(p1.getX(), p1.getY());
                poly.addPoint(p2.getX(), p2.getY());
                poly.addPoint(p3.getX(), p3.getY());
                return poly;
            } else {
                return null;
            }
        } else {
            return null;
        }
    }

    // Copied from runelite-api Perspective.java
    private static int getHeight(@Nonnull Scene scene, int localX, int localY, int plane) {
        int sceneX = (localX >> 7) + 40;
        int sceneY = (localY >> 7) + 40;
        if (sceneX >= 0 && sceneY >= 0 && sceneX < 184 && sceneY < 184) {
            int[][][] tileHeights = scene.getTileHeights();
            int x = localX & 127;
            int y = localY & 127;
            int var8 = x * tileHeights[plane][sceneX + 1][sceneY] + (128 - x) * tileHeights[plane][sceneX][sceneY] >> 7;
            int var9 = tileHeights[plane][sceneX][sceneY + 1] * (128 - x) + x * tileHeights[plane][sceneX + 1][sceneY + 1] >> 7;
            return (128 - y) * var8 + y * var9 >> 7;
        } else {
            return 0;
        }
    }

    private static int lerpInt128(int a, int b, int t_128) {
        float t = t_128 / 128f;
        return a + (int)((b-a)*t);
    }
}
