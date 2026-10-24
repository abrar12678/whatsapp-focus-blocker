package com.example.whatsappfocusblocker.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusContactTest {

    @Test
    fun canBeUnblockedByApp_returnsTrue_onlyForConfirmedAppBlocks() {
        val confirmedBlocked = FocusContact(
            id = "+1234567890",
            name = "Test User",
            phoneNumber = "+1234567890",
            status = BlockStatus.BLOCKED_CONFIRMED,
            blockedByAppTimestamp = System.currentTimeMillis()
        )
        assertTrue(
            "Contact confirmed blocked by this app must be eligible for unblocking",
            confirmedBlocked.canBeUnblockedByApp()
        )

        val appBlocked = FocusContact(
            id = "+1234567891",
            name = "Test User 2",
            phoneNumber = "+1234567891",
            status = BlockStatus.BLOCKED_BY_APP,
            blockedByAppTimestamp = System.currentTimeMillis()
        )
        assertTrue(appBlocked.canBeUnblockedByApp())

        val autoBlocked = FocusContact(
            id = "+1234567899",
            name = "Auto Blocked User",
            phoneNumber = "+1234567899",
            status = BlockStatus.AUTO_BLOCKED_BY_APP,
            blockedByAppTimestamp = System.currentTimeMillis()
        )
        assertTrue(
            "Contact auto-blocked by app must be eligible for unblocking",
            autoBlocked.canBeUnblockedByApp()
        )

        val needsManualVerification = FocusContact(
            id = "+1234567898",
            name = "Unverified Auto User",
            phoneNumber = "+1234567898",
            status = BlockStatus.NEEDS_MANUAL_VERIFICATION
        )
        assertFalse(
            "Contact in NEEDS_MANUAL_VERIFICATION must not be automatically unblocked",
            needsManualVerification.canBeUnblockedByApp()
        )

        val actionRequired = FocusContact(
            id = "+1234567892",
            name = "Unverified User",
            phoneNumber = "+1234567892",
            status = BlockStatus.ACTION_REQUIRED
        )
        assertFalse(
            "Contact in ACTION_REQUIRED must never be considered verified or unblockable by app",
            actionRequired.canBeUnblockedByApp()
        )

        val manuallyBlockedContact = FocusContact(
            id = "+9876543210",
            name = "Manually Blocked User",
            phoneNumber = "+9876543210",
            status = BlockStatus.MANUALLY_BLOCKED_EXTERNAL
        )
        assertFalse(
            "Contact blocked outside this app MUST NEVER be unblocked automatically",
            manuallyBlockedContact.canBeUnblockedByApp()
        )

        val unblockedContact = FocusContact(
            id = "+1122334455",
            name = "Unblocked User",
            phoneNumber = "+1122334455",
            status = BlockStatus.UNBLOCKED
        )
        assertFalse(unblockedContact.canBeUnblockedByApp())

        val failedContact = FocusContact(
            id = "+5566778899",
            name = "Failed User",
            phoneNumber = "+5566778899",
            status = BlockStatus.FAILED
        )
        assertFalse(failedContact.canBeUnblockedByApp())

        val pendingContact = FocusContact(
            id = "+6677889900",
            name = "Pending User",
            phoneNumber = "+6677889900",
            status = BlockStatus.PENDING
        )
        assertFalse(pendingContact.canBeUnblockedByApp())
    }

    @Test
    fun canBeBlockedForFocus_returnsTrue_onlyWhenNotAlreadyBlocked() {
        val unblocked = FocusContact(id = "1", name = "A", phoneNumber = "1", status = BlockStatus.UNBLOCKED)
        val selected = FocusContact(id = "2", name = "B", phoneNumber = "2", status = BlockStatus.SELECTED)
        val actionRequired = FocusContact(id = "3", name = "C", phoneNumber = "3", status = BlockStatus.ACTION_REQUIRED)
        val failed = FocusContact(id = "4", name = "D", phoneNumber = "4", status = BlockStatus.FAILED)
        val confirmedBlocked = FocusContact(id = "5", name = "E", phoneNumber = "5", status = BlockStatus.BLOCKED_CONFIRMED)
        val manuallyBlocked = FocusContact(id = "6", name = "F", phoneNumber = "6", status = BlockStatus.MANUALLY_BLOCKED_EXTERNAL)

        assertTrue(unblocked.canBeBlockedForFocus())
        assertTrue(selected.canBeBlockedForFocus())
        assertTrue(actionRequired.canBeBlockedForFocus())
        assertTrue(failed.canBeBlockedForFocus())
        assertFalse(confirmedBlocked.canBeBlockedForFocus())
        assertFalse(manuallyBlocked.canBeBlockedForFocus())
    }
}
