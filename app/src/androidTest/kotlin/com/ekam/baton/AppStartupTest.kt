package com.ekam.baton

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppStartupTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun testAppStartsWithoutCrashing() {
        // Just by arriving here, the MainActivity has launched without a crash.
        // For a basic test, we assert the activity is not null.
        assert(composeTestRule.activity != null)
    }
}
