package com.itsluminous.livescore

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import android.view.View
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.hasDescendant
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.itsluminous.verysmarttv.core.contract.ScoreContract
import org.hamcrest.Matchers.allOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Follow the IPL in the picker, then read `/pill` through the ContentResolver (the test APK shares the debug key). */
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
            onView(allOf(withId(R.id.tick), hasSiblingText("Indian Premier League"))).check(matches(withText("\u2611")))
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

    /** The repo refreshes through its executor; wait until the scripted event is in the cache. */
    private fun awaitFetch(repo: ScoreRepo) {
        repo.refreshAll(null)
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline && repo.event("ipl-live") == null) Thread.sleep(50)
        assertTrue("fetch did not land", repo.event("ipl-live") != null)
    }
}
