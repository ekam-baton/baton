package com.ekam.baton.ui.auth

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.assertIsDisplayed
import org.junit.Rule
import org.junit.Test

class SplashScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun splashScreen_displaysLogoAndText() {
        composeTestRule.setContent {
            BatonSplashScreen()
        }

        composeTestRule.onNodeWithContentDescription("Baton Logo").assertIsDisplayed()
        composeTestRule.onNodeWithText("from").assertIsDisplayed()
        composeTestRule.onNodeWithText("EKAM").assertIsDisplayed()
    }
}
