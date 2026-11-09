package com.itsluminous.verysmarttv;

import android.content.Context;
import android.graphics.Bitmap;
import android.widget.FrameLayout;
import android.widget.ImageView;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.io.File;
import java.io.FileOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The single wallpaper behind Home (DESIGN.md §3: the fork's static-wallpaper path, carried into {@link Wallpaper}):
 * shown from onCreate, released under memory pressure, and shown again when Home resumes, as the fork's player did.
 */
@RunWith(RobolectricTestRunner.class)
public class WallpaperTest {
    private Context ctx;
    private FrameLayout root;
    private File picture;

    @Before
    public void aPictureAndARoot() throws Exception {
        ctx = RuntimeEnvironment.getApplication();
        root = new FrameLayout(ctx);
        root.setBackgroundColor(0xFF101010);
        picture = File.createTempFile("wallpaper", ".jpg");
        picture.deleteOnExit();
        try (FileOutputStream out = new FileOutputStream(picture)) {
            Bitmap.createBitmap(64, 36, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.JPEG, 90, out);
        }
    }

    @Test
    public void thePictureIsBehindEverythingFromTheStart() {
        Wallpaper w = new Wallpaper(ctx, root, picture, 0xFF101010, false, 0);
        assertTrue(w.shown());
        assertEquals(1, root.getChildCount());
        assertNotNull(((ImageView) root.getChildAt(0)).getDrawable());
        assertNull("the picture covers the root, so its colour would be overdraw", root.getBackground());
    }

    @Test
    public void releasedUnderMemoryPressureAndShownAgainOnResume() {
        Wallpaper w = new Wallpaper(ctx, root, picture, 0xFF101010, true, 30);
        w.releaseForMemoryPressure();
        assertFalse(w.shown());
        assertNull(((ImageView) root.getChildAt(0)).getDrawable());
        assertNotNull("the plain colour is back while the picture is gone", root.getBackground());

        w.restore();   // what onResume calls
        assertTrue(w.shown());
        assertNotNull(((ImageView) root.getChildAt(0)).getDrawable());
        assertNull(root.getBackground());
        assertEquals("one image view, however often it is restored", 1, root.getChildCount());
    }

    @Test
    public void noPictureMeansThePlainColourAndNothingToRestore() {
        Wallpaper none = new Wallpaper(ctx, root, null, 0xFF101010, false, 0);
        assertFalse(none.shown());
        assertEquals(0, root.getChildCount());
        none.releaseForMemoryPressure();
        none.restore();
        assertFalse(none.shown());
        assertEquals(0, root.getChildCount());
        assertNotNull(root.getBackground());
        // An unreadable file is not tested here: Robolectric's BitmapFactory shadow decodes any path to a stand-in.
    }

    @Test
    public void closedStaysClosed() {
        Wallpaper w = new Wallpaper(ctx, root, picture, 0xFF101010, false, 0);
        w.close();
        w.restore();
        assertFalse(w.shown());
    }
}
