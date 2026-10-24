package com.example.whatsappfocusblocker.domain.model

import kotlinx.serialization.Serializable

/**
 * Represents a contact tracked by WhatsApp Focus Blocker.
 *
 * @param id Unique identifier (e.g., normalized phone or lookup key)
 * @param name Contact display name
 * @param phoneNumber Formatted/raw phone number
 * @param isSelected Whether contact is currently selected for focus session actions
 * @param status Current explicit block state and ownership
 * @param blockedByAppTimestamp Epoch millisecond timestamp when blocked by this app, or null
 * @param lastActionMessage Diagnostic message or reason for current status
 */
@Serializable
data class FocusContact(
    val id: String,
    val name: String,
    val phoneNumber: String,
    val isSelected: Boolean = false,
    val status: BlockStatus = BlockStatus.UNBLOCKED,
    val blockedByAppTimestamp: Long? = null,
    val lastActionMessage: String? = null
) {
    /**
     * Invariant: Only contacts that were genuinely confirmed as blocked through this application
     * can be unblocked by automated focus restoration.
     * Never unblock contacts in ACTION_REQUIRED, MANUALLY_BLOCKED_EXTERNAL, or FAILED.
     */
    fun canBeUnblockedByApp(): Boolean {
        return status == BlockStatus.USER_CONFIRMED_BLOCKED ||
                status == BlockStatus.BLOCKED_CONFIRMED ||
                status == BlockStatus.BLOCKED_BY_APP
    }

    /**
     * Whether this contact is safe and eligible to be targeted for a focus session block attempt.
     */
    fun canBeBlockedForFocus(): Boolean {
        return status == BlockStatus.UNBLOCKED ||
                status == BlockStatus.SELECTED ||
                status == BlockStatus.ACTION_REQUIRED ||
                status == BlockStatus.USER_CONFIRMED_UNBLOCKED ||
                status == BlockStatus.UNBLOCKED_CONFIRMED ||
                status == BlockStatus.FAILED
    }
}
