package com.example.whatsappfocusblocker.domain.model

import kotlinx.serialization.Serializable

/**
 * Explicit states representing contact status and action tracking.
 *
 * CRITICAL SAFETY INVARIANT:
 * "Unblock All" must ONLY target contacts with status [BLOCKED_CONFIRMED] or [BLOCKED_BY_APP].
 * Contacts in [ACTION_REQUIRED], [MANUALLY_BLOCKED_EXTERNAL], [FAILED], or [UNBLOCKED] must NEVER be unblocked.
 */
@Serializable
enum class BlockStatus {
    /** Contact is active and unblocked. */
    UNBLOCKED,

    /** Selected for focus session, awaiting action confirmation. */
    SELECTED,

    /** Action is currently pending or queued. */
    PENDING,

    /** Integration not configured; real WhatsApp blocking action required. */
    ACTION_REQUIRED,

    /** Contact was confirmed blocked through this app. Eligible for "Unblock All". */
    BLOCKED_CONFIRMED,

    /** Backward-compatible alias for BLOCKED_CONFIRMED. */
    BLOCKED_BY_APP,

    /** Contact was confirmed unblocked through this app. */
    UNBLOCKED_CONFIRMED,

    /** Contact was manually blocked outside of this app. Must NEVER be unblocked automatically. */
    MANUALLY_BLOCKED_EXTERNAL,

    /** Attempted action failed or could not be verified. */
    FAILED
}
