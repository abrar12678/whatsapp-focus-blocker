package com.example.whatsappfocusblocker.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusContactTest {

    @Test
    fun canBeUnblockedByApp_returnsTrue_onlyWhenStatusIsBlockedByApp() {
        val appBlockedContact = FocusContact(
            id = "+1234567890",
            name = "Test User",
            phoneNumber = "+1234567890",
            status = BlockStatus.BLOCKED_BY_APP,
            blockedByAppTimestamp = System.currentTimeMillis()
        )
        assertTrue(
            "Contact blocked by this app must be eligible for unblocking",
            appBlockedContact.canBeUnblockedByApp()
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
        assertFalse(
            "Already unblocked contact should not be eligible for unblocking",
            unblockedContact.canBeUnblockedByApp()
        )

        val failedContact = FocusContact(
            id = "+5566778899",
            name = "Failed User",
            phoneNumber = "+5566778899",
            status = BlockStatus.FAILED
        )
        assertFalse(
            "Contact with failed action must not be unblocked automatically",
            failedContact.canBeUnblockedByApp()
        )
    }

    @Test
    fun canBeBlockedForFocus_returnsTrue_onlyForUnblockedOrFailed() {
        val unblocked = FocusContact(id = "1", name = "A", phoneNumber = "1", status = BlockStatus.UNBLOCKED)
        val failed = FocusContact(id = "2", name = "B", phoneNumber = "2", status = BlockStatus.FAILED)
        val alreadyBlockedByApp = FocusContact(id = "3", name = "C", phoneNumber = "3", status = BlockStatus.BLOCKED_BY_APP)
        val manuallyBlocked = FocusContact(id = "4", name = "D", phoneNumber = "4", status = BlockStatus.MANUALLY_BLOCKED_EXTERNAL)

        assertTrue(unblocked.canBeBlockedForFocus())
        assertTrue(failed.canBeBlockedForFocus())
        assertFalse(alreadyBlockedByApp.canBeBlockedForFocus())
        assertFalse(manuallyBlocked.canBeBlockedForFocus())
    }
}
