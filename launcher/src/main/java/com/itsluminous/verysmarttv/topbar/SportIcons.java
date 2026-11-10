package com.itsluminous.verysmarttv.topbar;

import android.content.res.Resources;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.itsluminous.verysmarttv.R;
import com.itsluminous.verysmarttv.core.contract.ScoreContract;

/**
 * The icon and the name a score line carries for its row's {@link ScoreContract#SPORT} (ADR-042): one bundled vector
 * per value in {@link ScoreContract#SPORTS}, {@code ic_sport_other} for anything else, so a companion may publish a
 * sport the launcher has not learnt and the line still reads. Pure; {@code SportIconsTest} is its table.
 */
public final class SportIcons {
    private SportIcons() {
    }

    /** The drawable for {@code sport}: its own icon for a contract value, the trophy for null, empty or unknown. */
    @DrawableRes
    public static int forSport(@Nullable String sport) {
        if (sport == null) return R.drawable.ic_sport_other;
        switch (sport) {
            case ScoreContract.SPORT_CRICKET:
                return R.drawable.ic_sport_cricket;
            case ScoreContract.SPORT_FOOTBALL:
                return R.drawable.ic_sport_football;
            case ScoreContract.SPORT_F1:
                return R.drawable.ic_sport_f1;
            case ScoreContract.SPORT_TENNIS:
                return R.drawable.ic_sport_tennis;
            case ScoreContract.SPORT_BADMINTON:
                return R.drawable.ic_sport_badminton;
            case ScoreContract.SPORT_BASKETBALL:
                return R.drawable.ic_sport_basketball;
            case ScoreContract.SPORT_HOCKEY:
                return R.drawable.ic_sport_hockey;
            default:
                return R.drawable.ic_sport_other;
        }
    }

    /** The sport's name for the line's content description; "Sport" for an unknown value. */
    @StringRes
    public static int nameOf(@Nullable String sport) {
        if (sport == null) return R.string.sport_other;
        switch (sport) {
            case ScoreContract.SPORT_CRICKET:
                return R.string.sport_cricket;
            case ScoreContract.SPORT_FOOTBALL:
                return R.string.sport_football;
            case ScoreContract.SPORT_F1:
                return R.string.sport_f1;
            case ScoreContract.SPORT_TENNIS:
                return R.string.sport_tennis;
            case ScoreContract.SPORT_BADMINTON:
                return R.string.sport_badminton;
            case ScoreContract.SPORT_BASKETBALL:
                return R.string.sport_basketball;
            case ScoreContract.SPORT_HOCKEY:
                return R.string.sport_hockey;
            default:
                return R.string.sport_other;
        }
    }

    /** The drawable's resource entry name ({@code "ic_sport_cricket"}): the tag the line's icon view carries. */
    @NonNull
    public static String tag(@NonNull Resources res, @Nullable String sport) {
        return res.getResourceEntryName(forSport(sport));
    }
}
