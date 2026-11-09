package com.itsluminous.verysmarttv.core.contract;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.net.Uri;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Arrays;
import java.util.HashSet;

@RunWith(RobolectricTestRunner.class)
public class HeroContractTest {
    @Test
    public void authorityAndPermissionAreUnderTheCompanionPackage() {
        assertEquals("com.itsluminous.traktshelf", HeroContract.PACKAGE);
        assertEquals("com.itsluminous.traktshelf.hero", HeroContract.AUTHORITY);
        assertEquals("com.itsluminous.traktshelf.permission.READ_HERO", HeroContract.PERMISSION_READ);
        assertEquals("com.itsluminous.traktshelf", HeroContract.DETAILS_ACTIVITY.getPackageName());
        assertEquals("com.itsluminous.traktshelf.DetailsActivity", HeroContract.DETAILS_ACTIVITY.getClassName());
    }

    @Test
    public void itemsUriIsTheCarriedOne() {
        assertEquals("content://com.itsluminous.traktshelf.hero/items", HeroContract.ITEMS.toString());
    }

    @Test
    public void imageUrisCarryTheIdAsTheLastSegment() {
        Uri backdrop = HeroContract.backdrop(1L << 40 | 42);
        Uri poster = HeroContract.poster(42);
        assertEquals("content://com.itsluminous.traktshelf.hero/image/backdrop/1099511627818", backdrop.toString());
        assertEquals("content://com.itsluminous.traktshelf.hero/image/poster/42", poster.toString());
        assertEquals("42", poster.getLastPathSegment());
    }

    @Test
    public void isImageAcceptsOnlyThisProvidersImagePaths() {
        assertTrue(HeroContract.isImage(HeroContract.backdrop(1)));
        assertTrue(HeroContract.isImage(HeroContract.poster(1)));
        assertFalse(HeroContract.isImage(HeroContract.ITEMS));
        assertFalse(HeroContract.isImage(Uri.parse("content://com.itsluminous.livescore.scores/image/team/1")));
        assertFalse(HeroContract.isImage(Uri.parse("https://image.tmdb.org/t/p/w780/x.jpg")));
        assertFalse(HeroContract.isImage(null));
    }

    @Test
    public void columnsAreTheCarriedSixteenWithoutDuplicates() {
        String[] carried = {"_id", "kind", "title", "year", "synopsis", "imdb_rating", "rt_rating", "trakt_rating",
                "imdb_id", "trakt_id", "poster_path", "backdrop_path", "source_row", "rank", "meta", "next_episode"};
        assertEquals(Arrays.asList(carried), Arrays.asList(HeroContract.COLUMNS));
        assertEquals(HeroContract.COLUMNS.length, new HashSet<>(Arrays.asList(HeroContract.COLUMNS)).size());
    }

    @Test
    public void extrasMatchTheCompanionsDetailsActivity() {
        assertEquals("trakt_id", HeroContract.EXTRA_TRAKT_ID);
        assertEquals("kind", HeroContract.EXTRA_KIND);
        assertEquals("movie", HeroContract.KIND_MOVIE);
        assertEquals("show", HeroContract.KIND_SHOW);
    }
}
