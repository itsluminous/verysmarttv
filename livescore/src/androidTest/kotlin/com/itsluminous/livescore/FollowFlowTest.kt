package com.itsluminous.livescore

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import android.view.View
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.pressImeActionButton
import androidx.test.espresso.action.ViewActions.typeText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.hasDescendant
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.itsluminous.livescore.model.Sport
import com.itsluminous.verysmarttv.core.contract.ScoreContract
import org.hamcrest.Matchers.allOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Follow the IPL in the picker, then read `/pill` through the ContentResolver (the test APK shares the debug key);
 * badminton's key gate (ADR-044): enabling it with no key opens the dialog, Cancel leaves it unfollowed, OK with a
 * key follows it, Settings shows the key's tail and Remove keeps the follows while Home and the picker say a key is
 * needed.
 */
@RunWith(AndroidJUnit4::class)
class FollowFlowTest {
    @After
    fun tearDown() = TestRepo.reset()

    @Test
    fun followingTheIplMakesItsLiveMatchThePill() {
        val repo = TestRepo.install(listOf(TestRepo.iplLive()), followsJson = "{\"version\":1,\"follows\":[]}")
        val resolver = InstrumentationRegistry.getInstrumentation().targetContext.contentResolver
        resolver.query(ScoreContract.PILL, null, null, null, null)!!.use { c ->
            c.moveToFirst()
            assertEquals(ScoreContract.STATE_NONE, c.getString(c.getColumnIndexOrThrow(ScoreContract.STATE)))
        }
        ActivityScenario.launch(FollowActivity::class.java).use {
            onView(withText("Indian Premier League")).check(matches(isDisplayed()))
            onView(allOf(withId(R.id.label), withText("Indian Premier League"))).perform(pressOk())
            onView(allOf(withId(R.id.tick), hasSiblingText("Indian Premier League"))).check(matches(withContentDescription(R.string.check_all)))
        }
        assertTrue(repo.follows.all().any { it.competitionId == TestRepo.IPL })
        awaitFetch(repo)
        resolver.query(ScoreContract.PILL, null, null, null, null)!!.use { c ->
            c.moveToFirst()
            assertEquals(ScoreContract.STATE_LIVE, c.getString(c.getColumnIndexOrThrow(ScoreContract.STATE)))
            assertEquals("LIVE", c.getString(c.getColumnIndexOrThrow(ScoreContract.KICKER)))
            assertEquals("T20 · CSK 145/3 (16.2) v MI", c.getString(c.getColumnIndexOrThrow(ScoreContract.LINE)))
            assertEquals("ipl-live", c.getString(c.getColumnIndexOrThrow(ScoreContract.EVENT_ID)))
        }
        resolver.query(ScoreContract.EVENTS, null, null, null, null)!!.use { assertEquals(1, it.count) }
    }

    @Test
    fun badmintonListsTheCachedTournamentAndItsPlayersAndAPlayerFollowReachesThePill() {
        val repo = TestRepo.install(listOf(TestRepo.badmintonLive()), followsJson = "{\"version\":1,\"follows\":[]}")
        repo.prefs.badmintonKey = ""
        ActivityScenario.launch(FollowActivity::class.java).use {
            // No key and nothing cached: the competitions column says what badminton needs instead of listing tournaments.
            onView(allOf(withId(R.id.label), withText("Badminton"))).perform(focusRow())
            onView(withText(R.string.badminton_needs_key)).check(matches(isDisplayed()))
        }
        // A key, as a user whose fetch landed has one; without it the player's OK would open the key dialog (the case below).
        repo.prefs.badmintonKey = "test-key-0001"
        awaitSport(repo)
        ActivityScenario.launch(FollowActivity::class.java).use {
            onView(allOf(withId(R.id.label), withText("Badminton"))).perform(focusRow())
            onView(allOf(withId(R.id.label), withText("Japan Masters"))).check(matches(isDisplayed())).perform(focusRow())
            onView(allOf(withId(R.id.label), withText("Sindhu P. V."))).check(matches(isDisplayed()))
            onView(allOf(withId(R.id.label), withText("An S. Y."))).check(matches(isDisplayed()))
            onView(allOf(withId(R.id.label), withText("Match · SIN 21 11 · AN 18 9"))).check(matches(isDisplayed()))
            onView(allOf(withId(R.id.label), withText("Sindhu P. V."))).perform(pressOk())
        }
        assertTrue(repo.follows.all().any { it.teamId == "5001" && it.sport == Sport.BADMINTON })
        val resolver = InstrumentationRegistry.getInstrumentation().targetContext.contentResolver
        resolver.query(ScoreContract.PILL, null, null, null, null)!!.use { c ->
            c.moveToFirst()
            assertEquals(ScoreContract.STATE_LIVE, c.getString(c.getColumnIndexOrThrow(ScoreContract.STATE)))
            assertEquals(ScoreContract.SPORT_BADMINTON, c.getString(c.getColumnIndexOrThrow(ScoreContract.SPORT)))
            assertEquals("Japan Masters · Sindhu 21-18 11-9 An", c.getString(c.getColumnIndexOrThrow(ScoreContract.LINE)))
            assertEquals("bwf-live", c.getString(c.getColumnIndexOrThrow(ScoreContract.EVENT_ID)))
        }
    }

    @Test
    fun enablingBadmintonWithNoKeyAsksForIt_cancelLeavesItUnfollowed_okFollows_settingsShowsTheTailAndRemoves() {
        val repo = TestRepo.install(emptyList(), followsJson = "{\"version\":1,\"follows\":[]}")
        repo.prefs.badmintonKey = ""
        ActivityScenario.launch(FollowActivity::class.java).use {
            onView(allOf(withId(R.id.label), withText("Badminton"))).perform(pressOk())
            awaitDialog()
            // The key dialog, with the field and the text that says whose key it is and what it may cost.
            onView(withId(R.id.badmintonKeyField)).check(matches(isDisplayed()))
            onView(withText(R.string.badminton_key_explain)).check(matches(isDisplayed()))
            onView(withId(R.id.badmintonKeyField)).perform(closeSoftKeyboard())
            onView(withText(R.string.cancel)).perform(pressButton())
            box("Badminton").check(matches(withContentDescription(R.string.check_none)))
            assertTrue("Cancel leaves badminton unfollowed", repo.follows.all().none { it.sport == Sport.BADMINTON })
            assertEquals("", repo.prefs.badmintonKey)
            // OK with a key: the key is saved and the follow lands.
            onView(allOf(withId(R.id.label), withText("Badminton"))).perform(pressOk())
            awaitDialog()
            // The keyboard's Done key is OK: the user never has to find the button under the keyboard.
            onView(withId(R.id.badmintonKeyField)).perform(typeText("dummy-key-1234"), pressImeActionButton())
            box("Badminton").check(matches(withContentDescription(R.string.check_all)))
            assertEquals("dummy-key-1234", repo.prefs.badmintonKey)
            assertTrue(repo.follows.all().any { it.sport == Sport.BADMINTON })
            // With a key, a second badminton follow asks nothing.
            onView(allOf(withId(R.id.label), withText("Badminton"))).perform(pressOk())
            box("Badminton").check(matches(withContentDescription(R.string.check_none)))
            onView(allOf(withId(R.id.label), withText("Badminton"))).perform(pressOk())
            box("Badminton").check(matches(withContentDescription(R.string.check_all)))
        }
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        ActivityScenario.launch(SettingsActivity::class.java).use {
            // The row shows the key's last four characters, never more; Remove clears it and keeps the follows.
            onView(withText(target.getString(R.string.badminton_key_set, "1234"))).check(matches(isDisplayed()))
            onView(withText(R.string.badminton_key)).perform(pressOk())
            awaitDialog()
            onView(withId(R.id.badmintonKeyField)).perform(closeSoftKeyboard())
            onView(withText(R.string.badminton_key_remove)).perform(pressButton())
            onView(withText(R.string.badminton_key_unset)).check(matches(isDisplayed()))
        }
        assertEquals("", repo.prefs.badmintonKey)
        assertTrue("the follows stay when the key goes", repo.follows.all().any { it.sport == Sport.BADMINTON })
        // Home says the badminton rows need a key; the picker's column too.
        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withText(R.string.badminton_needs_key_row)).check(matches(isDisplayed()))
        }
        ActivityScenario.launch(FollowActivity::class.java).use {
            onView(allOf(withId(R.id.label), withText("Badminton"))).perform(focusRow())
            onView(withText(R.string.badminton_needs_key)).check(matches(isDisplayed()))
        }
    }

    @Test
    fun aSectionBoxFollowsItsChildrenAndOkOnItSelectsThenClearsAll() {
        val repo = TestRepo.install(emptyList(), followsJson = "{\"version\":1,\"follows\":[]}")
        ActivityScenario.launch(FollowActivity::class.java).use {
            onView(allOf(withId(R.id.label), withText("Cricket"))).perform(focusRow())
            onView(allOf(withId(R.id.label), withText("International matches"))).perform(focusRow())
            box("Cricket").check(matches(withContentDescription(R.string.check_none)))
            box("International matches").check(matches(withContentDescription(R.string.check_none)))
            // One child: the competition and the sport above it show the bar.
            onView(allOf(withId(R.id.label), withText("India"))).perform(pressOk())
            box("India").check(matches(withContentDescription(R.string.check_all)))
            box("International matches").check(matches(withContentDescription(R.string.check_partial)))
            box("Cricket").check(matches(withContentDescription(R.string.check_partial)))
            // Every child: the competition is ticked; the sport stays barred (the IPL is not followed).
            onView(allOf(withId(R.id.label), withText("Pakistan"))).perform(pressOk())
            box("International matches").check(matches(withContentDescription(R.string.check_all)))
            box("Cricket").check(matches(withContentDescription(R.string.check_partial)))
            assertTrue(repo.follows.all().map { it.teamId }.containsAll(listOf("6", "7")))
            // OK on the ticked competition clears it and its children.
            onView(allOf(withId(R.id.label), withText("International matches"))).perform(pressOk())
            box("International matches").check(matches(withContentDescription(R.string.check_none)))
            box("India").check(matches(withContentDescription(R.string.check_none)))
            box("Pakistan").check(matches(withContentDescription(R.string.check_none)))
            box("Cricket").check(matches(withContentDescription(R.string.check_none)))
            assertTrue(repo.follows.all().isEmpty())
            // OK on the empty competition follows the whole competition; the children's own boxes stay empty.
            onView(allOf(withId(R.id.label), withText("International matches"))).perform(pressOk())
            box("International matches").check(matches(withContentDescription(R.string.check_all)))
            box("India").check(matches(withContentDescription(R.string.check_none)))
            box("Cricket").check(matches(withContentDescription(R.string.check_partial)))
            assertTrue(repo.follows.all().single().competitionId == TestRepo.INTERNATIONAL)
            // OK on the barred sport follows the whole sport; OK again clears every cricket follow.
            onView(allOf(withId(R.id.label), withText("Cricket"))).perform(pressOk())
            box("Cricket").check(matches(withContentDescription(R.string.check_all)))
            box("All Cricket").check(matches(withContentDescription(R.string.check_all)))
            assertEquals(2, repo.follows.all().size)
            onView(allOf(withId(R.id.label), withText("Cricket"))).perform(pressOk())
            box("Cricket").check(matches(withContentDescription(R.string.check_none)))
            box("All Cricket").check(matches(withContentDescription(R.string.check_none)))
            box("International matches").check(matches(withContentDescription(R.string.check_none)))
            assertTrue(repo.follows.all().isEmpty())
        }
    }

    @Test
    fun homeShowsTheFollowedRowAndTheEmptyStateLeadsToFollow() {
        val repo = TestRepo.install(listOf(TestRepo.iplLive()))
        awaitFetch(repo)
        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withText("CSK 145/3 (16.2/20 ov) · MI –")).check(matches(isDisplayed()))
            onView(withId(R.id.empty)).check(matches(org.hamcrest.Matchers.not(isDisplayed())))
        }
        repo.follows.clear()
        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withId(R.id.empty)).check(matches(isDisplayed()))
            onView(withId(R.id.followPill)).check(matches(androidx.test.espresso.matcher.ViewMatchers.hasFocus()))
        }
    }

    private fun hasSiblingText(text: String) = androidx.test.espresso.matcher.ViewMatchers.hasSibling(withText(text))

    /** The box of the row whose label is [text]. */
    private fun box(text: String) = onView(allOf(withId(R.id.tick), hasSiblingText(text)))

    /** What OK on the remote does to the row a label sits in: focus it, then click it (a tap would only focus a focusableInTouchMode row). */
    private fun pressOk() = object : ViewAction {
        override fun getConstraints() = isDisplayed()
        override fun getDescription() = "focus the row and press OK"
        override fun perform(uiController: UiController, view: View) {
            val row = view.parent as View
            row.requestFocus()
            row.performClick()
            uiController.loopMainThreadUntilIdle()
        }
    }

    /**
     * A dialog opened by the OK before this gets its window focus from the window manager a moment after it is shown;
     * Espresso picks its root only once it has it, and otherwise waits on the activity's root, which never gets it back.
     */
    private fun awaitDialog() {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        Thread.sleep(DIALOG_FOCUS_MS)
    }

    /**
     * OK on a dialog button: focus and click it without a tap, since a tap puts the emulator in touch mode, where no
     * view takes D-pad focus and the launcher's suite, run after this one, fails (docs/BUILDING.md).
     */
    private fun pressButton() = object : ViewAction {
        override fun getConstraints() = isDisplayed()
        override fun getDescription() = "focus the button and press OK"
        override fun perform(uiController: UiController, view: View) {
            view.requestFocus()
            view.performClick()
            uiController.loopMainThreadUntilIdle()
        }
    }

    /** Focus the row a label sits in, the way the D-pad would: the picker fills the next column on focus. */
    private fun focusRow() = object : ViewAction {
        override fun getConstraints() = isDisplayed()
        override fun getDescription() = "focus the row"
        override fun perform(uiController: UiController, view: View) {
            (view.parent as View).requestFocus()
            uiController.loopMainThreadUntilIdle()
        }
    }

    /** The one-sport fetch the picker uses for a sport without a catalogue; wait until the badminton match is cached. */
    private fun awaitSport(repo: ScoreRepo) {
        repo.refreshSport(Sport.BADMINTON, null)
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline && repo.event("bwf-live") == null) Thread.sleep(50)
        assertTrue("fetch did not land", repo.event("bwf-live") != null)
    }

    private companion object {
        const val DIALOG_FOCUS_MS = 700L
    }

    /** The repo refreshes through its executor; wait until the scripted event is in the cache. */
    private fun awaitFetch(repo: ScoreRepo) {
        repo.refreshAll(null)
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline && repo.event("ipl-live") == null) Thread.sleep(50)
        assertTrue("fetch did not land", repo.event("ipl-live") != null)
    }
}
