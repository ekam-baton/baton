package com.ekam.baton.ui.auth

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertIsDisplayed
import org.junit.Rule
import org.junit.Test

class SignupScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun signupScreen_invalidEmail_showsError() {
        composeTestRule.setContent {
            SignupScreen(onSignupSuccess = { _, _ -> })
        }

        composeTestRule.onNodeWithText("Email Address").performTextInput("invalid-email")
        composeTestRule.onNodeWithText("Phone Number").performTextInput("12345678")
        
        composeTestRule.onNodeWithText("Verify & Sign Up").performClick()
        
        composeTestRule.onNodeWithText("Please enter a valid email address").assertIsDisplayed()
    }
    
    @Test
    fun signupScreen_invalidPhone_showsError() {
        composeTestRule.setContent {
            SignupScreen(onSignupSuccess = { _, _ -> })
        }

        composeTestRule.onNodeWithText("Email Address").performTextInput("test@test.com")
        composeTestRule.onNodeWithText("Phone Number").performTextInput("123")
        
        composeTestRule.onNodeWithText("Verify & Sign Up").performClick()
        
        composeTestRule.onNodeWithText("Please enter a valid phone number").assertIsDisplayed()
    }

    @Test
    fun signupScreen_validInput_notFragmentActivity_showsError() {
        composeTestRule.setContent {
            SignupScreen(onSignupSuccess = { _, _ -> })
        }

        composeTestRule.onNodeWithText("Email Address").performTextInput("test@test.com")
        composeTestRule.onNodeWithText("Phone Number").performTextInput("12345678")
        
        composeTestRule.onNodeWithText("Verify & Sign Up").performClick()
        
        composeTestRule.onNodeWithText("System error: Failed to initialize biometric login context.").assertIsDisplayed()
    }
}
