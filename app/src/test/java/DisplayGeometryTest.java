import android.app.Application;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.Color;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36, application = Application.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class DisplayGeometryTest {
    @Test public void fixedLandscapeAppGetsWideDisplayBeforeLaunch() {
        assertArrayEquals(new int[]{3168, 1440}, DisplayGeometry.orient(1440, 3168, "auto", ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE));
        assertArrayEquals(new int[]{1440, 3168}, DisplayGeometry.orient(3168, 1440, "auto", ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));
        assertArrayEquals(new int[]{3168, 1440}, DisplayGeometry.orient(1440, 3168, "landscape", ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));
    }
    @Test public void rejectsRightBottomEdgesNaNAndOldOrientation() throws Exception {
        DisplayGeometry g = new DisplayGeometry(320, 144, 1);
        assertTrue(g.contains(319, 143));
        assertFalse(g.contains(320, 100)); assertFalse(g.contains(100, 144));
        assertFalse(g.contains(Double.NaN, 1)); assertFalse(g.contains(1, Double.POSITIVE_INFINITY));
        assertFalse(g.matches(new DisplayGeometry(144, 320, 0).json()));
        assertTrue(g.matches(g.json()));
    }
    @Test public void inverseProjectionRestoresLandscapePixelsWithoutStretching() {
        Bitmap physical = Bitmap.createBitmap(2, 3, Bitmap.Config.ARGB_8888);
        // A 3x2 logical picture projected clockwise into the natural 2x3 surface.
        physical.setPixel(1, 0, Color.RED); physical.setPixel(1, 2, Color.BLUE);
        Bitmap logical = new DisplayGeometry(3, 2, 1).logicalBitmap(physical);
        assertEquals(3, logical.getWidth()); assertEquals(2, logical.getHeight());
        assertEquals(Color.RED, logical.getPixel(0, 0)); assertEquals(Color.BLUE, logical.getPixel(2, 0));
        logical.recycle(); physical.recycle();
    }
    @Test public void refusesUnknownProjectionInsteadOfInventingCoordinates() {
        Bitmap bitmap = Bitmap.createBitmap(2, 3, Bitmap.Config.ARGB_8888);
        try { assertThrows(IllegalStateException.class, () -> new DisplayGeometry(9, 4, 0).logicalBitmap(bitmap)); }
        finally { bitmap.recycle(); }
    }
}
