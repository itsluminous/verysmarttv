package com.itsluminous.verysmarttv.testkit.score;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.itsluminous.verysmarttv.core.contract.ScoreContract;
import com.itsluminous.verysmarttv.testkit.FakeScoreProvider;
import com.itsluminous.verysmarttv.testkit.ScoreFixture;

import java.util.List;

/**
 * {@link FakeScoreProvider} seeded with {@link ScoreFixture}'s live row when the system creates it: the stand-in
 * companion's only component. The launcher queries it like the real provider, through the signature permission. A
 * test in the launcher's process, which cannot reach the fake's static setters here, switches the row with
 * {@code call(ScoreFixture.METHOD_FIXTURE, name)} and reads the last restored list with
 * {@code call(ScoreFixture.METHOD_RESTORED)}; {@code call("restore")} is the fake's own.
 */
public final class FixtureScoreProvider extends FakeScoreProvider {
    @Override
    public boolean onCreate() {
        ScoreFixture.seedFresh();
        return super.onCreate();
    }

    @Override
    @NonNull
    public Bundle call(@NonNull String method, @Nullable String arg, @Nullable Bundle extras) {
        Bundle result = new Bundle();
        if (ScoreFixture.METHOD_FIXTURE.equals(method)) {
            ScoreFixture.seed(arg == null ? ScoreFixture.EMPTY : arg);
            Context context = getContext();
            if (context != null) context.getContentResolver().notifyChange(ScoreContract.PILL, null);
            result.putBoolean(ScoreContract.RESULT_OK, true);
            return result;
        }
        if (ScoreFixture.METHOD_RESTORED.equals(method)) {
            List<String> restored = FakeScoreProvider.restored();
            result.putBoolean(ScoreContract.RESULT_OK, true);
            result.putInt("count", restored.size());
            if (!restored.isEmpty()) result.putString(ScoreContract.EXTRA_JSON, restored.get(restored.size() - 1));
            return result;
        }
        return super.call(method, arg, extras);
    }
}
