package com.example.whatsappfocusblocker.domain.model

import kotlinx.serialization.Serializable

/**
 * Represents the block ownership and current state of a contact.
 *
 * CRITICAL SAFETY INVARIANT:
 * "Unblock All" must ONLY target contacts with status [BLOCKED_BY_APP].
 * Contacts marked as [MANUALLY_BLOCKED_EXTERNAL] or [UNBLOCKED] must NEVER be unblocked by automated actions.
 */
@Serializable
enum class BlockStatus {
    /** Contact is active and not blocked. */
    UNBLOCKED,

    /** Contact was successfully blocked through this app during a focus session. Eligible for "Unblock All". */
    BLOCKED_BY_APP,

    /** Contact was manually blocked by the user prior to or outside of this app. Must NEVER be unblocked by the app. */
    MANUALLY_BLOCKED_EXTERNAL,

    /** An action is currently pending to block this contact. */
    PENDING_BLOCK,

    /** An action is currently pending to unblock this contact. */
    PENDING_UNBLOCK,

    /** The attempted block/unblock action failed or was not completed. */
    FAILED
}
