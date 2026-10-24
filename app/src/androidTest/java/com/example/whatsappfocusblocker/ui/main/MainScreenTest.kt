package com.example.whatsappfocusblocker.ui.main

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.example.whatsappfocusblocker.theme.WhatsAppFocusBlockerTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** UI tests for [com.example.whatsappfocusblocker.ui.main.MainScreen]. */
class MainScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun setup() {
        composeTestRule.setContent {
            WhatsAppFocusBlockerTheme {
                MainContent(
                    uiState = FocusUiState(),
                    onSelectContacts = {},
                    onBlockSelected = {},
                    onUnblockAll = {},
                    onToggleContact = { _, _ -> }
                )
            }
        }
    }

    @Test
    fun appHeaderAndStatus_areDisplayed() {
        composeTestRule.onNodeWithText("WhatsApp Focus Blocker").assertIsDisplayed()
        composeTestRule.onNodeWithText("Current Status").assertIsDisplayed()
        composeTestRule.onNodeWithText("Ready / Standby").assertIsDisplayed()
    }

    @Test
    fun emptyState_isDisplayedWhenNoContacts() {
        composeTestRule.onNodeWithText("No Contacts Added Yet").assertIsDisplayed()
        composeTestRule.onNodeWithText("Notice: Real Blocking Not Yet Connected").assertIsDisplayed()
    }
}
