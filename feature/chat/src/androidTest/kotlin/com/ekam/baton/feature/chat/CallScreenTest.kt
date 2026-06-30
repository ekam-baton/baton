package com.ekam.baton.feature.chat

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertIsDisplayed
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertTrue

class CallScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun callScreen_initialState_displaysCorrectly() {
        composeTestRule.setContent {
            CallScreen(agentName = "Agent Smith", onEndCall = {})
        }

        composeTestRule.onNodeWithText("Connected").assertIsDisplayed()
        composeTestRule.onNodeWithText("Agent Smith").assertIsDisplayed()
        composeTestRule.onNodeWithText("A").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Mute").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("End Call").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Speaker").assertIsDisplayed()
    }

    @Test
    fun callScreen_endCallButtonClicked_triggersCallback() {
        var endCallClicked = false
        composeTestRule.setContent {
            CallScreen(agentName = "Agent Smith", onEndCall = { endCallClicked = true })
        }

        composeTestRule.onNodeWithContentDescription("End Call").performClick()
        assertTrue(endCallClicked)
    }
}
