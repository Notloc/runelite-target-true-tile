import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.Scene;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;

import javax.annotation.Nonnull;
import java.awt.Polygon;

/**
 * Renders a small southwest-corner marker triangle instead of the usual full-tile square.
 */
class PerspectiveUtil
{
	static Polygon getCanvasTileMarkPoly(@Nonnull Client client, @Nonnull WorldView worldView, @Nonnull LocalPoint localLocation, int size, int length128)
	{
		int msx = localLocation.getSceneX() + 40;
		int msy = localLocation.getSceneY() + 40;
		if (msx < 0 || msy < 0 || msx >= 184 || msy >= 184)
		{
			return null;
		}

		Scene scene = worldView.getScene();
		byte[][][] tileSettings = scene.getExtendedTileSettings();
		int plane = worldView.getPlane();
		int tilePlane = plane;
		if (plane < 3 && (tileSettings[1][msx][msy] & 2) == 2)
		{
			tilePlane = plane + 1;
		}

		int swX = localLocation.getX() - size * 128 / 2;
		int swY = localLocation.getY() - size * 128 / 2;
		int neX = localLocation.getX() + size * 128 / 2;
		int neY = localLocation.getY() + size * 128 / 2;
		int swHeight = getHeight(scene, swX, swY, tilePlane);
		int nwHeight = getHeight(scene, neX, swY, tilePlane);
		int seHeight = getHeight(scene, swX, neY, tilePlane);

		int eX = swX + length128;
		int nY = swY + length128;

		int exHeight = lerpInt128(swHeight, seHeight, length128 / size);
		int nYHeight = lerpInt128(swHeight, nwHeight, length128 / size);

		Point p1 = Perspective.localToCanvas(client, swX, swY, swHeight);
		Point p2 = Perspective.localToCanvas(client, eX, swY, nYHeight);
		Point p3 = Perspective.localToCanvas(client, swX, nY, exHeight);
		if (p1 == null || p2 == null || p3 == null)
		{
			return null;
		}

		Polygon poly = new Polygon();
		poly.addPoint(p1.getX(), p1.getY());
		poly.addPoint(p2.getX(), p2.getY());
		poly.addPoint(p3.getX(), p3.getY());
		return poly;
	}

	private static int getHeight(@Nonnull Scene scene, int localX, int localY, int plane)
	{
		int sceneX = (localX >> 7) + 40;
		int sceneY = (localY >> 7) + 40;
		if (sceneX < 0 || sceneY < 0 || sceneX >= 184 || sceneY >= 184)
		{
			return 0;
		}

		int[][][] tileHeights = scene.getTileHeights();
		int x = localX & 127;
		int y = localY & 127;
		int var8 = (x * tileHeights[plane][sceneX + 1][sceneY] + (128 - x) * tileHeights[plane][sceneX][sceneY]) >> 7;
		int var9 = (tileHeights[plane][sceneX][sceneY + 1] * (128 - x) + x * tileHeights[plane][sceneX + 1][sceneY + 1]) >> 7;
		return ((128 - y) * var8 + y * var9) >> 7;
	}

	private static int lerpInt128(int a, int b, int t128)
	{
		float t = t128 / 128f;
		return a + (int) ((b - a) * t);
	}
}
