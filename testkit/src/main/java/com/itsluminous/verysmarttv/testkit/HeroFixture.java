package com.itsluminous.verysmarttv.testkit;

import android.graphics.Bitmap;
import android.graphics.Canvas;

import androidx.annotation.NonNull;

import com.itsluminous.verysmarttv.core.contract.HeroContract;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.List;

/**
 * The three carousel titles the stand-in companion ({@code :testkit:hero}) publishes and the launcher's
 * {@code CarouselFocusTest} expects, in rank order. One class, so the app that seeds {@link FakeHeroProvider}
 * and the test that reads the launcher's shelf can never disagree about a title or an id.
 */
public final class HeroFixture {
    private HeroFixture() {}

    /** Titles in carousel order; {@code TITLES[0]} is what the shelf shows first. */
    public static final String[] TITLES = {"Fixture Movie One", "Fixture Show Two", "Fixture Movie Three"};

    /** Trakt ids, parallel to {@link #TITLES}. */
    public static final long[] TRAKT_IDS = {101, 202, 303};

    /** Kinds, parallel to {@link #TITLES}: the middle one is a show, so a show row is on the shelf too. */
    public static final String[] KINDS = {HeroContract.KIND_MOVIE, HeroContract.KIND_SHOW, HeroContract.KIND_MOVIE};

    /** Row ids: the Trakt id, with bit 40 set for the show (the real provider's layout). */
    public static final long[] IDS = {101, (1L << 40) | 202, 303};

    /** Backdrop colours, parallel to {@link #TITLES}: three plain JPEGs the shelf can decode. */
    private static final int[] COLOURS = {0xFF2B5F8A, 0xFF8A3C2B, 0xFF3C8A4F};

    /** The {@code /items} rows, every column filled, in {@link HeroContract#COLUMNS} order. */
    @NonNull
    public static List<Object[]> rows() {
        Object[][] rows = new Object[TITLES.length][];
        for (int i = 0; i < TITLES.length; i++) {
            boolean show = HeroContract.KIND_SHOW.equals(KINDS[i]);
            rows[i] = new Object[] {
                IDS[i], KINDS[i], TITLES[i], 2001 + i, "Synopsis of " + TITLES[i] + ".", "7." + (i + 1), (80 + i) + "%", 70 + i,
                "tt000010" + i, TRAKT_IDS[i], HeroContract.poster(IDS[i]).toString(), HeroContract.backdrop(IDS[i]).toString(),
                show ? "watchlist_shows" : "rec_movies", i, "Fixture meta " + (i + 1), show ? "S1 E2" : "",
            };
        }
        return Arrays.asList(rows);
    }

    /** Seeds {@link FakeHeroProvider} with the rows and a backdrop JPEG per row. */
    public static void seed() {
        FakeHeroProvider.seed(rows());
        for (int i = 0; i < IDS.length; i++) FakeHeroProvider.seedImage(IDS[i], jpeg(COLOURS[i]));
    }

    /** A 320x180 JPEG of one colour: enough for the shelf to decode, fade and show. */
    @NonNull
    static byte[] jpeg(int colour) {
        Bitmap bitmap = Bitmap.createBitmap(320, 180, Bitmap.Config.ARGB_8888);
        new Canvas(bitmap).drawColor(colour);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out);
        bitmap.recycle();
        return out.toByteArray();
    }
}
