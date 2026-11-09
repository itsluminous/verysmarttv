package com.itsluminous.verysmarttv.testkit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.ContentResolver;
import android.database.Cursor;
import android.graphics.BitmapFactory;

import com.itsluminous.verysmarttv.core.contract.HeroContract;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

@RunWith(RobolectricTestRunner.class)
public class HeroFixtureTest {
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
    public void everyRowFollowsTheContractAndTheParallelArraysAgree() {
        assertEquals(HeroFixture.TITLES.length, HeroFixture.TRAKT_IDS.length);
        assertEquals(HeroFixture.TITLES.length, HeroFixture.KINDS.length);
        assertEquals(HeroFixture.TITLES.length, HeroFixture.IDS.length);
        Set<String> titles = new HashSet<>();
        for (Object[] row : HeroFixture.rows()) {
            assertEquals(HeroContract.COLUMNS.length, row.length);
            assertTrue("titles must differ so a Left/Right move is visible", titles.add((String) row[2]));
        }
    }

    @Test
    public void seedPublishesThreeRowsInOrderAndADecodableBackdropEach() throws Exception {
        HeroFixture.seed();
        try (Cursor c = resolver.query(HeroContract.ITEMS, null, null, null, null)) {
            assertNotNull(c);
            assertEquals(HeroFixture.TITLES.length, c.getCount());
            for (int i = 0; c.moveToNext(); i++) {
                assertEquals(HeroFixture.TITLES[i], c.getString(c.getColumnIndexOrThrow(HeroContract.TITLE)));
                assertEquals(HeroFixture.KINDS[i], c.getString(c.getColumnIndexOrThrow(HeroContract.KIND)));
                assertEquals(HeroFixture.TRAKT_IDS[i], c.getLong(c.getColumnIndexOrThrow(HeroContract.TRAKT_ID)));
                assertEquals(i, c.getInt(c.getColumnIndexOrThrow(HeroContract.RANK)));
            }
        }
        for (long id : HeroFixture.IDS) {
            try (InputStream in = resolver.openInputStream(HeroContract.backdrop(id))) {
                assertNotNull(in);
                assertNotNull("backdrop " + id + " does not decode", BitmapFactory.decodeStream(in));
            }
        }
    }
}
