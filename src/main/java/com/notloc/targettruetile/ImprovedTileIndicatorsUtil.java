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

import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.Model;
import net.runelite.api.NPC;
import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Allows tile indicators to be drawn beneath actors on the GPU renderer by punching a
 * hole in the tile polygon anywhere the actor's model would otherwise occlude it.
 */
class ImprovedTileIndicatorsUtil
{
	// Reused across frames instead of reallocated. Grown lazily, never shrunk.
	private int[] scratchX2d = new int[0];
	private int[] scratchY2d = new int[0];

	private final PolygonBuilder polygonBuilder = new PolygonBuilder();

	void removePlayerFast(Client client, Graphics2D graphics, Player player, List<Polygon> filter)
	{
		WorldView worldView = client.getTopLevelWorldView();
		if (worldView == null || player == null)
		{
			return;
		}
		int localZ = Perspective.getFootprintTileHeight(client, player.getLocalLocation(), worldView.getPlane(), player.getFootprintSize()) - player.getAnimationHeightOffset();
		removeActorFast(client, graphics, player, localZ, filter);
	}

	void removeNpcFast(Client client, Graphics2D graphics, NPC npc, List<Polygon> filter)
	{
		WorldView worldView = client.getTopLevelWorldView();
		if (worldView == null || npc == null || npc.getComposition() == null)
		{
			return;
		}
		int localZ = Perspective.getFootprintTileHeight(client, npc.getLocalLocation(), worldView.getPlane(), npc.getComposition().getFootprintSize()) - npc.getAnimationHeightOffset();
		removeActorFast(client, graphics, npc, localZ, filter);
	}

	private void removeActorFast(Client client, Graphics2D graphics, Actor actor, int localZ, List<Polygon> filter)
	{
		WorldView worldView = client.getTopLevelWorldView();
		if (worldView == null)
		{
			return;
		}

		Model model = actor.getModel();
		if (model == null)
		{
			return;
		}

		int vCount = model.getVerticesCount();
		float[] x3d = model.getVerticesX();
		float[] y3d = model.getVerticesY();
		float[] z3d = model.getVerticesZ();

		int[] x2d = ensureCapacity(scratchX2d, vCount);
		int[] y2d = ensureCapacity(scratchY2d, vCount);
		scratchX2d = x2d;
		scratchY2d = y2d;

		LocalPoint lp = actor.getLocalLocation();
		int localX = lp.getX();
		int localY = lp.getY();
		int rotation = actor.getCurrentOrientation();

		Perspective.modelToCanvas(client, worldView, vCount, localX, localY, localZ, rotation, x3d, z3d, y3d, x2d, y2d);

		int clipX1 = client.getViewportXOffset();
		int clipY1 = client.getViewportYOffset();
		int clipX2 = client.getViewportWidth() + clipX1;
		int clipY2 = client.getViewportHeight() + clipY1;

		boolean anyVisible = false;
		for (int i = 0; i < vCount; i++)
		{
			int x = x2d[i];
			int y = y2d[i];
			boolean visibleX = x >= clipX1 && x < clipX2;
			boolean visibleY = y >= clipY1 && y < clipY2;
			anyVisible |= visibleX && visibleY;
		}

		if (!anyVisible)
		{
			return;
		}

		int tCount = model.getFaceCount();
		int[] tx = model.getFaceIndices1();
		int[] ty = model.getFaceIndices2();
		int[] tz = model.getFaceIndices3();
		byte[] triangleTransparencies = model.getFaceTransparencies();

		polygonBuilder.clear();
		for (int i = 0; i < tCount; i++)
		{
			// Cull tris facing away from the camera
			if (getTriDirection(x2d[tx[i]], y2d[tx[i]], x2d[ty[i]], y2d[ty[i]], x2d[tz[i]], y2d[tz[i]]) >= 0)
			{
				continue;
			}

			// Cull tris that are not in the filter
			if (!isTriInsideFilter(x2d[tx[i]], y2d[tx[i]], x2d[ty[i]], y2d[ty[i]], x2d[tz[i]], y2d[tz[i]], filter))
			{
				continue;
			}

			if (triangleTransparencies == null || (triangleTransparencies[i] & 255) < 254)
			{
				polygonBuilder.addTriangle(
					x2d[tx[i]], y2d[tx[i]],
					x2d[ty[i]], y2d[ty[i]],
					x2d[tz[i]], y2d[tz[i]]
				);
			}
		}

		Object origAA = graphics.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
		Composite orig = graphics.getComposite();
		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
		graphics.setComposite(AlphaComposite.Clear);
		graphics.setColor(Color.WHITE);

		for (Polygon polygon : polygonBuilder.getPolygons())
		{
			graphics.fill(polygon);
		}

		graphics.setComposite(orig);
		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, origAA);
	}

	private static int[] ensureCapacity(int[] array, int minSize)
	{
		return array.length >= minSize ? array : new int[minSize];
	}

	private static int getTriDirection(int x1, int y1, int x2, int y2, int x3, int y3)
	{
		int x4 = x2 - x1;
		int y4 = y2 - y1;
		int x5 = x3 - x1;
		int y5 = y3 - y1;
		return x4 * y5 - y4 * x5;
	}

	private static boolean isTriInsideFilter(int x1, int y1, int x2, int y2, int x3, int y3, List<Polygon> filter)
	{
		int left = Math.min(Math.min(x1, x2), x3);
		int right = Math.max(Math.max(x1, x2), x3);
		int top = Math.min(Math.min(y1, y2), y3);
		int bottom = Math.max(Math.max(y1, y2), y3);

		for (Polygon p : filter)
		{
			if (p.contains(x1, y1) || p.contains(x2, y2) || p.contains(x3, y3))
			{
				return true;
			}
			if (p.intersects(left, top, right - left, bottom - top))
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * Merges triangles into larger polygons to reduce draw calls.
	 * <p>
	 * This is the original algorithm from Improved Tile Indicators, unchanged, except that
	 * the builder itself is now reusable: call {@link #clear()} at the start of each frame
	 * instead of constructing a brand new builder (and its backing HashMap/ArrayList) for
	 * every NPC on every frame. The per-triangle {@link Edge} objects are still allocated
	 * fresh each frame since the geometry genuinely changes frame to frame, but that's cheap
	 * young-gen garbage compared to reallocating and rehashing the containers themselves.
	 */
	private static class PolygonBuilder
	{
		private final List<PolygonPrototype> polygons = new ArrayList<>();
		private final Map<Edge, PolygonPrototype> polygonsByEdge = new HashMap<>();

		void clear()
		{
			polygons.clear();
			polygonsByEdge.clear();
		}

		void addTriangle(int x1, int y1, int x2, int y2, int x3, int y3)
		{
			Edge e1 = new Edge(x1, y1, x2, y2);
			Edge e2 = new Edge(x2, y2, x3, y3);
			Edge e3 = new Edge(x3, y3, x1, y1);

			if (polygonsByEdge.containsKey(e1))
			{
				PolygonPrototype p = polygonsByEdge.get(e1);
				p.insertEdgesAt(e1, e2, e3);
				polygonsByEdge.remove(e1);
				polygonsByEdge.put(e2, p);
				polygonsByEdge.put(e3, p);
			}
			else if (polygonsByEdge.containsKey(e2))
			{
				PolygonPrototype p = polygonsByEdge.get(e2);
				p.insertEdgesAt(e2, e3, e1);
				polygonsByEdge.remove(e2);
				polygonsByEdge.put(e1, p);
				polygonsByEdge.put(e3, p);
			}
			else if (polygonsByEdge.containsKey(e3))
			{
				PolygonPrototype p = polygonsByEdge.get(e3);
				p.insertEdgesAt(e3, e1, e2);
				polygonsByEdge.remove(e3);
				polygonsByEdge.put(e1, p);
				polygonsByEdge.put(e2, p);
			}
			else
			{
				PolygonPrototype p = new PolygonPrototype(e1, e2, e3);
				polygonsByEdge.put(e1, p);
				polygonsByEdge.put(e2, p);
				polygonsByEdge.put(e3, p);
				polygons.add(p);
			}
		}

		List<Polygon> getPolygons()
		{
			List<Polygon> result = new ArrayList<>(polygons.size());
			for (PolygonPrototype p : polygons)
			{
				result.add(p.toPolygon());
			}
			return result;
		}
	}

	private static class PolygonPrototype
	{
		private final java.util.LinkedList<Edge> edges = new java.util.LinkedList<>();

		PolygonPrototype(Edge... edges)
		{
			this.edges.addAll(java.util.Arrays.asList(edges));
		}

		void insertEdgesAt(Edge edgeToReplace, Edge... newEdges)
		{
			int index = edges.indexOf(edgeToReplace);
			if (index == -1)
			{
				return;
			}

			edges.set(index, newEdges[0]);
			for (int i = 1; i < newEdges.length; i++)
			{
				edges.add(index + i, newEdges[i]);
			}
		}

		Polygon toPolygon()
		{
			int[] x = new int[edges.size()];
			int[] y = new int[edges.size()];

			for (int i = 0; i < edges.size(); i++)
			{
				x[i] = edges.get(i).x1;
				y[i] = edges.get(i).y1;
			}

			return new Polygon(x, y, edges.size());
		}
	}

	private static class Edge
	{
		final int x1;
		final int y1;
		final int x2;
		final int y2;

		Edge(int x1, int y1, int x2, int y2)
		{
			this.x1 = x1;
			this.y1 = y1;
			this.x2 = x2;
			this.y2 = y2;
		}

		@Override
		public boolean equals(Object obj)
		{
			if (obj == this)
			{
				return true;
			}
			if (!(obj instanceof Edge))
			{
				return false;
			}
			Edge e = (Edge) obj;
			return (e.x1 == x1 && e.y1 == y1 && e.x2 == x2 && e.y2 == y2) ||
				(e.x1 == x2 && e.y1 == y2 && e.x2 == x1 && e.y2 == y1);
		}

		@Override
		public int hashCode()
		{
			return x1 + y1 + x2 + y2;
		}
	}
}
