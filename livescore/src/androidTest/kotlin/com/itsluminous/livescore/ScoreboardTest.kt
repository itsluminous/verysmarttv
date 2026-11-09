package com.itsluminous.livescore

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.itsluminous.verysmarttv.core.contract.ScoreContract
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** A scripted cricket Test match → the innings table rows, opened the way the launcher's pill opens it. */
@RunWith(AndroidJUnit4::class)
class ScoreboardTest {
    @After
    fun tearDown() = TestRepo.reset()

    @Test
    fun testMatchShowsBothInningsPerSide() {
        val repo = TestRepo.install(listOf(TestRepo.testMatch()), followsJson = "{\"version\":1,\"follows\":[{\"kind\":\"team\",\"sport\":\"cricket\",\"competition\":\"\",\"team\":\"25\",\"event\":\"\",\"label\":\"Bangladesh\",\"expires\":0}]}")
        repo.refreshAll(null)
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline && repo.event("test-stumps") == null) Thread.sleep(50)
        assertTrue(repo.event("test-stumps") != null)
        val intent = Intent(Intent.ACTION_MAIN)
            .setComponent(ScoreContract.SCOREBOARD_ACTIVITY)
            .putExtra(ScoreContract.EXTRA_EVENT_ID, "test-stumps")
        InstrumentationRegistry.getInstrumentation().targetContext.packageName
        ActivityScenario.launch<ScoreboardActivity>(intent).use {
            onView(withId(R.id.kicker)).check(matches(withText("STUMPS")))
            onView(withText("Bangladesh · batting")).check(matches(isDisplayed()))
            onView(withText("386 (111.5 ov)")).check(matches(isDisplayed()))
            onView(withText("49/2 (14 ov)")).check(matches(isDisplayed()))
            onView(withText("356 (106.4 ov)")).check(matches(isDisplayed()))
            onView(withText("398/6d (93 ov)")).check(matches(isDisplayed()))
            onView(withText("Bangladesh require 320 runs")).check(matches(isDisplayed()))
            onView(withId(R.id.title)).check(matches(withText("BAN 386 & 49/2 (14 ov, target 369) · WI 356 & 398/6d")))
        }
    }

    @Test
    fun actionOpenWithAnEventIdLandsOnTheScoreboard() {
        val repo = TestRepo.install(listOf(TestRepo.testMatch()), followsJson = "{\"version\":1,\"follows\":[{\"kind\":\"sport\",\"sport\":\"cricket\",\"competition\":\"\",\"team\":\"\",\"event\":\"\",\"label\":\"Cricket\",\"expires\":0}]}")
        repo.refreshAll(null)
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline && repo.event("test-stumps") == null) Thread.sleep(50)
        val intent = Intent(ScoreContract.ACTION_OPEN)
            .setPackage(ScoreContract.PACKAGE)
            .putExtra(ScoreContract.EXTRA_EVENT_ID, "test-stumps")
        ActivityScenario.launch<MainActivity>(intent).use {
            onView(withId(R.id.kicker)).check(matches(withText("STUMPS")))
            onView(withText("398/6d (93 ov)")).check(matches(isDisplayed()))
        }
    }
}
