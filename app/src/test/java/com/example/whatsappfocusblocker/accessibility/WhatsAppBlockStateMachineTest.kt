package com.example.whatsappfocusblocker.accessibility

import com.example.whatsappfocusblocker.domain.model.BlockStatus
import com.example.whatsappfocusblocker.domain.model.FocusContact
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WhatsAppBlockStateMachineTest {

    private val targetContact = FocusContact(
        id = "c1",
        name = "Alice Smith",
        phoneNumber = "+1 (555) 123-4567",
        status = BlockStatus.UNBLOCKED
    )

    @Test
    fun start_transitionsToOpeningChat() {
        val sm = WhatsAppBlockStateMachine(targetContact)
        assertEquals(BlockAutomationState.IDLE, sm.state)

        val action = sm.start(currentTimeMillis = 1000L)
        assertEquals(BlockAutomationState.OPENING_CHAT, sm.state)
        assertEquals(1000L, sm.stateEnterTimestamp)
        assertTrue(action is WhatsAppBlockAction.None)
    }

    @Test
    fun normalFlow_navigatesMenuAndSucceedsWithVerifiedBanner() {
        val sm = WhatsAppBlockStateMachine(targetContact)
        sm.start(currentTimeMillis = 1000L)

        // 1. WhatsApp opens with Alice Smith's chat and overflow button
        val chatSnapshot = WhatsAppScreenSnapshot(
            packageName = WhatsAppAccessibilityService.WHATSAPP_PKG,
            chatTitle = "Alice Smith",
            hasOverflowButton = true
        )
        val action1 = sm.onScreenSnapshot(chatSnapshot, currentTimeMillis = 1100L)
        assertEquals(BlockAutomationState.OPENING_OVERFLOW_MENU, sm.state)
        assertTrue(action1 is WhatsAppBlockAction.OpenOverflowMenu)

        // 2. Overflow menu opens showing "More"
        val menuSnapshot = WhatsAppScreenSnapshot(
            packageName = WhatsAppAccessibilityService.WHATSAPP_PKG,
            chatTitle = "Alice Smith",
            isOverflowMenuVisible = true,
            menuItems = listOf("Media, links, and docs", "Search", "Mute notifications", "More")
        )
        val action2 = sm.onScreenSnapshot(menuSnapshot, currentTimeMillis = 1200L)
        assertEquals(BlockAutomationState.SELECTING_MORE, sm.state)
        assertTrue(action2 is WhatsAppBlockAction.ClickMoreMenuItem)

        // 3. Submenu opens showing "Block"
        val submenuSnapshot = WhatsAppScreenSnapshot(
            packageName = WhatsAppAccessibilityService.WHATSAPP_PKG,
            chatTitle = "Alice Smith",
            isOverflowMenuVisible = true,
            menuItems = listOf("Report", "Block", "Clear chat", "Export chat")
        )
        val action3 = sm.onScreenSnapshot(submenuSnapshot, currentTimeMillis = 1300L)
        assertEquals(BlockAutomationState.AWAITING_BLOCK_DIALOG, sm.state)
        assertTrue(action3 is WhatsAppBlockAction.ClickBlockMenuItem)

        // 4. Block dialog opens with "Report to WhatsApp" checkbox CHECKED by default
        val dialogWithReportChecked = WhatsAppScreenSnapshot(
            packageName = WhatsAppAccessibilityService.WHATSAPP_PKG,
            isBlockDialogVisible = true,
            blockDialogTitle = "Block Alice Smith?",
            hasReportCheckbox = true,
            isReportCheckboxChecked = true,
            hasPositiveBlockButton = true
        )
        val action4 = sm.onScreenSnapshot(dialogWithReportChecked, currentTimeMillis = 1400L)
        // Guardrail: must NOT click block; must request unchecking the checkbox!
        assertEquals(BlockAutomationState.INSPECTING_REPORT_CHECKBOX, sm.state)
        assertTrue(action4 is WhatsAppBlockAction.UncheckReportCheckbox)

        // 5. Checkbox is now unchecked -> safely click Confirm Block
        val dialogWithReportUnchecked = WhatsAppScreenSnapshot(
            packageName = WhatsAppAccessibilityService.WHATSAPP_PKG,
            isBlockDialogVisible = true,
            blockDialogTitle = "Block Alice Smith?",
            hasReportCheckbox = true,
            isReportCheckboxChecked = false,
            hasPositiveBlockButton = true
        )
        val action5 = sm.onScreenSnapshot(dialogWithReportUnchecked, currentTimeMillis = 1500L)
        assertTrue(action5 is WhatsAppBlockAction.ClickConfirmBlockButton)

        // 6. Dialog dismissed, system feedback banner visible
        val verifiedBlockedSnapshot = WhatsAppScreenSnapshot(
            packageName = WhatsAppAccessibilityService.WHATSAPP_PKG,
            chatTitle = "Alice Smith",
            hasBlockedStatusBanner = true
        )
        val action6 = sm.onScreenSnapshot(verifiedBlockedSnapshot, currentTimeMillis = 1600L)
        assertEquals(BlockAutomationState.COMPLETED_SUCCESS, sm.state)
        assertTrue(action6 is WhatsAppBlockAction.CompletedSuccess)
        assertEquals("c1", (action6 as WhatsAppBlockAction.CompletedSuccess).contactId)
    }

    @Test
    fun safetyBanner_shortcutsDirectlyToBlockDialog() {
        val sm = WhatsAppBlockStateMachine(targetContact)
        sm.start(currentTimeMillis = 1000L)

        // Screen has safety tools banner with direct "Block" button
        val safetySnapshot = WhatsAppScreenSnapshot(
            packageName = WhatsAppAccessibilityService.WHATSAPP_PKG,
            chatTitle = "Alice Smith",
            hasSafetyBannerBlock = true
        )
        val action = sm.onScreenSnapshot(safetySnapshot, currentTimeMillis = 1100L)
        assertEquals(BlockAutomationState.AWAITING_BLOCK_DIALOG, sm.state)
        assertTrue(action is WhatsAppBlockAction.ClickSafetyBannerBlock)
    }

    @Test
    fun contactMismatch_pausesForManualIntervention() {
        val sm = WhatsAppBlockStateMachine(targetContact)
        sm.start(currentTimeMillis = 1000L)

        // Opened chat for Bob instead of Alice
        val mismatchSnapshot = WhatsAppScreenSnapshot(
            packageName = WhatsAppAccessibilityService.WHATSAPP_PKG,
            chatTitle = "Bob Jones",
            hasOverflowButton = true
        )
        val action = sm.onScreenSnapshot(mismatchSnapshot, currentTimeMillis = 1100L)
        assertEquals(BlockAutomationState.PAUSED_FOR_MANUAL_INTERVENTION, sm.state)
        assertTrue(action is WhatsAppBlockAction.PauseForUser)
        assertTrue(sm.pauseReason!!.contains("does not match"))
    }

    @Test
    fun inviteToWhatsApp_pausesImmediately() {
        val sm = WhatsAppBlockStateMachine(targetContact)
        sm.start(currentTimeMillis = 1000L)

        val inviteSnapshot = WhatsAppScreenSnapshot(
            packageName = WhatsAppAccessibilityService.WHATSAPP_PKG,
            hasInviteToWhatsApp = true
        )
        val action = sm.onScreenSnapshot(inviteSnapshot, currentTimeMillis = 1100L)
        assertEquals(BlockAutomationState.PAUSED_FOR_MANUAL_INTERVENTION, sm.state)
        assertTrue(action is WhatsAppBlockAction.PauseForUser)
        assertTrue(sm.pauseReason!!.contains("Invite"))
    }

    @Test
    fun unexpectedPackage_pausesImmediately() {
        val sm = WhatsAppBlockStateMachine(targetContact)
        sm.start(currentTimeMillis = 1000L)

        val wrongAppSnapshot = WhatsAppScreenSnapshot(
            packageName = "com.facebook.katana",
            chatTitle = "Alice Smith"
        )
        val action = sm.onScreenSnapshot(wrongAppSnapshot, currentTimeMillis = 1100L)
        assertEquals(BlockAutomationState.PAUSED_FOR_MANUAL_INTERVENTION, sm.state)
        assertTrue(action is WhatsAppBlockAction.PauseForUser)
    }

    @Test
    fun ambiguousReportCheckbox_pausesToPreventAccidentalSpamReporting() {
        val sm = WhatsAppBlockStateMachine(targetContact)
        sm.start(currentTimeMillis = 1000L)

        // Fast forward to block dialog with an ambiguous checkbox
        val ambiguousSnapshot = WhatsAppScreenSnapshot(
            packageName = WhatsAppAccessibilityService.WHATSAPP_PKG,
            chatTitle = "Alice Smith",
            isBlockDialogVisible = true,
            hasReportCheckbox = true,
            isReportCheckboxAmbiguous = true,
            hasPositiveBlockButton = true
        )
        sm.onScreenSnapshot(
            WhatsAppScreenSnapshot(
                packageName = WhatsAppAccessibilityService.WHATSAPP_PKG,
                chatTitle = "Alice Smith",
                hasSafetyBannerBlock = true
            ),
            1100L
        )

        val action = sm.onScreenSnapshot(ambiguousSnapshot, currentTimeMillis = 1200L)
        assertEquals(BlockAutomationState.PAUSED_FOR_MANUAL_INTERVENTION, sm.state)
        assertTrue(action is WhatsAppBlockAction.PauseForUser)
        assertTrue(sm.pauseReason!!.contains("Ambiguous"))
    }

    @Test
    fun watchdogTimeout_pausesWhenScreenStalls() {
        val sm = WhatsAppBlockStateMachine(targetContact, timeoutMillis = 3500L)
        sm.start(currentTimeMillis = 1000L)

        // Move to opening overflow menu at 1100L
        sm.onScreenSnapshot(
            WhatsAppScreenSnapshot(
                packageName = WhatsAppAccessibilityService.WHATSAPP_PKG,
                chatTitle = "Alice Smith",
                hasOverflowButton = true
            ),
            1100L
        )
        assertEquals(BlockAutomationState.OPENING_OVERFLOW_MENU, sm.state)

        // Watchdog tick before timeout
        val tick1 = sm.onWatchdogTick(currentTimeMillis = 4000L)
        assertTrue(tick1 is WhatsAppBlockAction.None)
        assertEquals(BlockAutomationState.OPENING_OVERFLOW_MENU, sm.state)

        // Watchdog tick past 3500ms timeout (1100 + 3501 = 4601L)
        val tick2 = sm.onWatchdogTick(currentTimeMillis = 4650L)
        assertEquals(BlockAutomationState.PAUSED_FOR_MANUAL_INTERVENTION, sm.state)
        assertTrue(tick2 is WhatsAppBlockAction.PauseForUser)
        assertTrue(sm.pauseReason!!.contains("Watchdog timeout"))
    }

    @Test
    fun watchdogTimeout_marksNeedsManualVerification_ifBannerMissingAfterBlockConfirmed() {
        val sm = WhatsAppBlockStateMachine(targetContact, timeoutMillis = 3500L)
        sm.start(currentTimeMillis = 1000L)

        // Move to CONFIRMING_BLOCK
        sm.onScreenSnapshot(
            WhatsAppScreenSnapshot(
                packageName = WhatsAppAccessibilityService.WHATSAPP_PKG,
                chatTitle = "Alice Smith",
                hasSafetyBannerBlock = true
            ),
            1100L
        )
        sm.onScreenSnapshot(
            WhatsAppScreenSnapshot(
                packageName = WhatsAppAccessibilityService.WHATSAPP_PKG,
                isBlockDialogVisible = true,
                hasPositiveBlockButton = true
            ),
            1200L
        )
        assertEquals(BlockAutomationState.VERIFYING_BLOCKED_STATUS, sm.state)

        // Timeout expires without banner appearance
        val action = sm.onWatchdogTick(currentTimeMillis = 5000L)
        assertEquals(BlockAutomationState.NEEDS_MANUAL_VERIFICATION, sm.state)
        assertTrue(action is WhatsAppBlockAction.CompletedNeedsVerification)
    }

    @Test
    fun userCancellation_haltsImmediately() {
        val sm = WhatsAppBlockStateMachine(targetContact)
        sm.start(currentTimeMillis = 1000L)

        val action = sm.cancel("User pressed emergency STOP")
        assertEquals(BlockAutomationState.CANCELLED_BY_USER, sm.state)
        assertTrue(action is WhatsAppBlockAction.Cancel)
        assertEquals("User pressed emergency STOP", sm.cancelReason)

        // Subsequent snapshots do not trigger transitions
        val action2 = sm.onScreenSnapshot(
            WhatsAppScreenSnapshot(
                packageName = WhatsAppAccessibilityService.WHATSAPP_PKG,
                chatTitle = "Alice Smith",
                hasOverflowButton = true
            )
        )
        assertTrue(action2 is WhatsAppBlockAction.None)
        assertEquals(BlockAutomationState.CANCELLED_BY_USER, sm.state)
    }

    @Test
    fun phoneSuffixMatching_acceptsMatchingPhoneNumbers() {
        val sm = WhatsAppBlockStateMachine(targetContact)

        assertTrue(sm.validateContactIdentity("Alice Smith"))
        assertTrue(sm.validateContactIdentity("Alice"))
        assertTrue(sm.validateContactIdentity("+1 555 123-4567"))
        assertTrue(sm.validateContactIdentity("1234567"))
        assertFalse(sm.validateContactIdentity("Bob"))
        assertFalse(sm.validateContactIdentity("+1 999 999-9999"))
        assertFalse(sm.validateContactIdentity(null))
        assertFalse(sm.validateContactIdentity(""))
    }
}
