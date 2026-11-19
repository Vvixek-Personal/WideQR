package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WideQRComposeTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun `scanner launches and navigates directly to diagnostics and settings`() {
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("WideQR").assertIsDisplayed()
        composeTestRule.onNodeWithTag("gallery_import_button").assertIsDisplayed()

        // Navigate directly to Camera Diagnostics from Scanner top bar
        composeTestRule.onNodeWithTag("diagnostics_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Camera Diagnostics").assertIsDisplayed()
        composeTestRule.onNodeWithTag("diagnostics_back_button").performClick()
        composeTestRule.waitForIdle()

        // Navigate to Settings from Scanner top bar
        composeTestRule.onNodeWithTag("settings_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Appearance").assertIsDisplayed()
        composeTestRule.onNodeWithText("Privacy").performScrollTo().assertIsDisplayed()

        // Navigate to Camera Diagnostics from Settings
        composeTestRule.onNodeWithTag("settings_diagnostics_row").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Camera Diagnostics").assertIsDisplayed()

        // Navigate back to Scanner
        composeTestRule.onNodeWithTag("diagnostics_back_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_back_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("WideQR").assertIsDisplayed()
    }

    @Test
    fun `scanner shows permission recovery card and gallery fallback when camera permission is missing`() {
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("camera_permission_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("fallback_gallery_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("gallery_import_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("flash_toggle_button").assertDoesNotExist()
    }
}
