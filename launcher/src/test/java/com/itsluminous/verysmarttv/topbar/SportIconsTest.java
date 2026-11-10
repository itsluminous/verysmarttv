package com.itsluminous.verysmarttv.topbar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.itsluminous.verysmarttv.R;
import com.itsluminous.verysmarttv.core.contract.ScoreContract;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.HashSet;
import java.util.Set;

/**
 * The sport-to-icon map (ADR-042): every value the contract enumerates has its own icon and its own name, no two
 * sports share one, and anything else (null, empty, a sport the launcher has not learnt) gets the trophy and "Sport".
 * Robolectric only because the contract's URIs are built on load; the map itself is pure.
 */
@RunWith(RobolectricTestRunner.class)
public class SportIconsTest {
    @Test
    public void everyContractSportHasItsOwnIconAndName() {
        Set<Integer> icons = new HashSet<>();
        Set<Integer> names = new HashSet<>();
        for (String sport : ScoreContract.SPORTS) {
            int icon = SportIcons.forSport(sport);
            assertNotEquals(sport + " must not fall back", R.drawable.ic_sport_other, icon);
            assertTrue(sport + " shares an icon", icons.add(icon));
            int name = SportIcons.nameOf(sport);
            assertNotEquals(sport + " must have its own name", R.string.sport_other, name);
            assertTrue(sport + " shares a name", names.add(name));
        }
        assertEquals(7, icons.size());
    }

    @Test
    public void theMapIsTheOneOfTheDesign() {
        assertEquals(R.drawable.ic_sport_cricket, SportIcons.forSport(ScoreContract.SPORT_CRICKET));
        assertEquals(R.drawable.ic_sport_football, SportIcons.forSport(ScoreContract.SPORT_FOOTBALL));
        assertEquals(R.drawable.ic_sport_f1, SportIcons.forSport(ScoreContract.SPORT_F1));
        assertEquals(R.drawable.ic_sport_tennis, SportIcons.forSport(ScoreContract.SPORT_TENNIS));
        assertEquals(R.drawable.ic_sport_badminton, SportIcons.forSport(ScoreContract.SPORT_BADMINTON));
        assertEquals(R.drawable.ic_sport_basketball, SportIcons.forSport(ScoreContract.SPORT_BASKETBALL));
        assertEquals(R.drawable.ic_sport_hockey, SportIcons.forSport(ScoreContract.SPORT_HOCKEY));
        assertEquals(R.string.sport_f1, SportIcons.nameOf(ScoreContract.SPORT_F1));
        assertEquals(R.string.sport_hockey, SportIcons.nameOf(ScoreContract.SPORT_HOCKEY));
    }

    @Test
    public void anythingElseIsTheTrophy() {
        for (String s : new String[] {null, "", "kabaddi", "Cricket", "CRICKET", " cricket"}) {
            assertEquals(String.valueOf(s), R.drawable.ic_sport_other, SportIcons.forSport(s));
            assertEquals(String.valueOf(s), R.string.sport_other, SportIcons.nameOf(s));
        }
    }
}
