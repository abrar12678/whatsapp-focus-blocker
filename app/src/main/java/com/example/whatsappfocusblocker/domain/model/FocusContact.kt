package com.example.whatsappfocusblocker.domain.model

/**
 * Represents a contact tracked by WhatsApp Focus Blocker.
 *
 * @param id Unique identifier (e.g., normalized phone or lookup key)
 * @param name Contact display name
 * @param phoneNumber Formatted/raw phone number
 * @param isSelected Whether contact is currently selected for focus session actions
 * @param status Current block state and ownership
 * @param blockedByAppTimestamp Epoch millisecond timestamp when blocked by this app, or null
 * @param lastActionMessage Diagnostic message or reason for current status
 */
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
     * Invariant: Only contacts that were successfully blocked by this application
     * can be unblocked by automated focus restoration.
     */
    fun canBeUnblockedByApp(): Boolean {
        return status == BlockStatus.BLOCKED_BY_APP
    }

    /**
     * Whether this contact is safe and eligible to be blocked for a focus session.
     */
    fun canBeBlockedForFocus(): Boolean {
        return status == BlockStatus.UNBLOCKED || status == BlockStatus.FAILED
    }
}
