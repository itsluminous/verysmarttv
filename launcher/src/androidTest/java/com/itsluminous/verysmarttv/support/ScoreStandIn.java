package com.itsluminous.verysmarttv.support;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.content.Context;
import android.content.pm.ProviderInfo;
import android.os.Bundle;

import androidx.annotation.Nullable;

import com.itsluminous.verysmarttv.core.contract.ScoreContract;
import com.itsluminous.verysmarttv.testkit.ScoreFixture;

/**
 * The live-score companion stand-in from a launcher test ({@code :testkit:score}, ADR-037): it must be the app that
 * answers {@link ScoreContract#AUTHORITY} on the emulator, and its pill row is switched through the provider's own
 * {@code call}, since the fake's static setters live in the stand-in's process.
 */
public final class ScoreStandIn {
    public static final String PROVIDER = "com.itsluminous.verysmarttv.testkit.score.FixtureScoreProvider";

    private ScoreStandIn() {}

    /** Fails with the install command when the stand-in is not the provider on this emulator. */
    public static void assertInstalled(Context target) {
        ProviderInfo provider = target.getPackageManager().resolveContentProvider(ScoreContract.AUTHORITY, 0);
        assertNotNull("nothing answers " + ScoreContract.AUTHORITY + "; install the stand-in: ./gradlew :testkit:score:installDebug", provider);
        assertEquals("the real companion answers " + ScoreContract.AUTHORITY + ", not the stand-in; run ./gradlew :testkit:score:installDebug",
                PROVIDER, provider.name);
    }

    /** Switches the stand-in's pill row to the named {@link ScoreFixture} (live, break, upcoming, final, empty); it notifies /pill. */
    public static void fixture(Context target, String name) {
        Bundle result = target.getContentResolver().call(ScoreContract.PILL, ScoreFixture.METHOD_FIXTURE, name, null);
        assertNotNull("the stand-in answered the fixture call", result);
    }

    /** The last JSON a restore delivered to the stand-in, or null when none did. */
    @Nullable
    public static String lastRestored(Context target) {
        Bundle result = target.getContentResolver().call(ScoreContract.PILL, ScoreFixture.METHOD_RESTORED, null, null);
        return result == null ? null : result.getString(ScoreContract.EXTRA_JSON);
    }
}
