package com.itsluminous.verysmarttv.testkit;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;

import com.itsluminous.verysmarttv.core.contract.HeroContract;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.Arrays;

@RunWith(RobolectricTestRunner.class)
public class FakeHeroProviderTest {
    private ContentResolver resolver;

    @Before
    public void setUp() {
        Robolectric.setupContentProvider(FakeHeroProvider.class, HeroContract.AUTHORITY);
        resolver = RuntimeEnvironment.getApplication().getContentResolver();
    }

    @After
    public void tearDown() {
        FakeHeroProvider.reset();
    }

    @Test
    public void unseededItemsIsAnEmptyCursorWithTheContractColumns() {
        try (Cursor c = resolver.query(HeroContract.ITEMS, null, null, null, null)) {
            assertEquals(0, c.getCount());
            assertArrayEquals(HeroContract.COLUMNS, c.getColumnNames());
        }
        assertEquals(1, FakeHeroProvider.queryCount());
    }

    @Test
    public void seededRowsComeBackInOrderWithImageUrisPointingHere() {
        long showId = (1L << 40) | 7;
        FakeHeroProvider.seed(Arrays.asList(
                FakeHeroProvider.row(showId, HeroContract.KIND_SHOW, "Severance", "S2 E4 · Up next"),
                FakeHeroProvider.row(99, HeroContract.KIND_MOVIE, "Heat", "1995 · Recommended")));
        try (Cursor c = resolver.query(HeroContract.ITEMS, null, null, null, null)) {
            assertEquals(2, c.getCount());
            c.moveToFirst();
            assertEquals(showId, c.getLong(c.getColumnIndexOrThrow(HeroContract.ID)));
            assertEquals("Severance", c.getString(c.getColumnIndexOrThrow(HeroContract.TITLE)));
            assertEquals(7, c.getLong(c.getColumnIndexOrThrow(HeroContract.TRAKT_ID)));
            assertEquals(HeroContract.poster(showId).toString(), c.getString(c.getColumnIndexOrThrow(HeroContract.POSTER_PATH)));
            c.moveToNext();
            assertEquals("Heat", c.getString(c.getColumnIndexOrThrow(HeroContract.TITLE)));
            assertEquals(1, c.getInt(c.getColumnIndexOrThrow(HeroContract.RANK)));
        }
    }

    @Test
    public void seedRefusesARowOfTheWrongWidth() {
        try {
            FakeHeroProvider.seed(Arrays.<Object[]>asList(new Object[] {1L, "movie"}));
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // the fake is strict so a test cannot drift from the contract
        }
    }

    @Test
    public void unknownUriReturnsNull() {
        assertNull(resolver.query(Uri.parse("content://" + HeroContract.AUTHORITY + "/other"), null, null, null, null));
        assertNull(resolver.getType(Uri.parse("content://" + HeroContract.AUTHORITY + "/other")));
        assertEquals(HeroContract.TYPE_ITEMS, resolver.getType(HeroContract.ITEMS));
    }

    @Test
    public void openFileServesSeededBytesForPosterAndBackdrop() throws Exception {
        byte[] jpeg = {(byte) 0xFF, (byte) 0xD8, 1, 2, 3};
        FakeHeroProvider.seedImage(42, jpeg);
        for (Uri uri : new Uri[] {HeroContract.poster(42), HeroContract.backdrop(42)}) {
            try (InputStream in = resolver.openInputStream(uri)) {
                byte[] read = new byte[jpeg.length + 1];
                int n = in.read(read);
                assertEquals(jpeg.length, n);
                assertArrayEquals(jpeg, Arrays.copyOf(read, n));
            }
        }
    }

    @Test
    public void openFileRefusesUnseededIdsAndWriteMode() throws Exception {
        try {
            resolver.openInputStream(HeroContract.poster(5));
            fail("expected FileNotFoundException");
        } catch (FileNotFoundException expected) {
            // nothing seeded for id 5
        }
        FakeHeroProvider.seedImage(5, new byte[] {1});
        try {
            resolver.openFileDescriptor(HeroContract.poster(5), "w");
            fail("expected FileNotFoundException");
        } catch (FileNotFoundException expected) {
            // read-only, like the real provider
        }
    }
}
