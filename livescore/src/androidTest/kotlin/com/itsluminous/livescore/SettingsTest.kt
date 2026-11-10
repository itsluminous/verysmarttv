package com.itsluminous.livescore

import android.view.View
import android.widget.ListView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Settings → Badminton source (ADR-049): SportMicro by default with the key row under it; picking the experimental BWF
 * website saves the preference, says what it costs and hides the key row; Off says badminton is not fetched or shown.
 */
@RunWith(AndroidJUnit4::class)
class SettingsTest {
    @After
    fun tearDown() = TestRepo.reset()

    @Test
    fun badmintonSourceRowOffersTheThreeSourcesAndTheKeyRowFollowsSportMicro() {
        val repo = TestRepo.install(emptyList())
        ActivityScenario.launch(SettingsActivity::class.java).use {
            onView(withText(R.string.badminton_source)).check(matches(isDisplayed()))
            onView(withText(R.string.badminton_source_sportmicro_value)).check(matches(isDisplayed()))
            onView(withText(R.string.badminton_key)).check(matches(isDisplayed()))

            onView(withText(R.string.badminton_source)).perform(pressOk())
            awaitDialog()
            onView(withText(R.string.badminton_source_bwf)).inRoot(isDialog()).perform(chooseItem())
            onView(withText(R.string.ok)).inRoot(isDialog()).perform(pressButton())
            onView(withText(R.string.badminton_source_bwf_value)).check(matches(isDisplayed()))
            onView(withText(R.string.badminton_key)).check(doesNotExist())
            assertEquals(BadmintonSource.BWF_WEBVIEW, repo.prefs.badmintonSource)

            onView(withText(R.string.badminton_source)).perform(pressOk())
            awaitDialog()
            onView(withText(R.string.badminton_source_none)).inRoot(isDialog()).perform(chooseItem())
            onView(withText(R.string.ok)).inRoot(isDialog()).perform(pressButton())
            onView(withText(R.string.badminton_source_none_value)).check(matches(isDisplayed()))
            assertEquals(BadmintonSource.NONE, repo.prefs.badmintonSource)

            onView(withText(R.string.badminton_source)).perform(pressOk())
            awaitDialog()
            onView(withText(R.string.badminton_source_sportmicro)).inRoot(isDialog()).perform(chooseItem())
            onView(withText(R.string.ok)).inRoot(isDialog()).perform(pressButton())
            onView(withText(R.string.badminton_source_sportmicro_value)).check(matches(isDisplayed()))
            onView(withText(R.string.badminton_key)).check(matches(isDisplayed()))
            assertEquals(BadmintonSource.SPORTMICRO, repo.prefs.badmintonSource)
        }
    }

    /** OK on a settings row: focus the row the name sits in and click it, the way the remote does (no tap: touch mode would break the launcher's suite). */
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

    /** OK on a dialog's list item: the list's own item click, so the single-choice state follows. */
    private fun chooseItem() = object : ViewAction {
        override fun getConstraints() = isDisplayed()
        override fun getDescription() = "choose the list item"
        override fun perform(uiController: UiController, view: View) {
            val list = view.parent as ListView
            val position = list.getPositionForView(view)
            list.performItemClick(view, position, list.getItemIdAtPosition(position))
            uiController.loopMainThreadUntilIdle()
        }
    }

    private fun pressButton() = object : ViewAction {
        override fun getConstraints() = isDisplayed()
        override fun getDescription() = "focus the button and press OK"
        override fun perform(uiController: UiController, view: View) {
            view.requestFocus()
            view.performClick()
            uiController.loopMainThreadUntilIdle()
        }
    }

    /** The dialog gets its window focus a moment after it is shown; Espresso picks its root only then. */
    private fun awaitDialog() {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        Thread.sleep(DIALOG_FOCUS_MS)
    }

    private companion object {
        const val DIALOG_FOCUS_MS = 700L
    }
}
