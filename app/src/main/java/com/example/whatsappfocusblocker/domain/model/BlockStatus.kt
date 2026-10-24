package com.example.whatsappfocusblocker.domain.model

import kotlinx.serialization.Serializable

/**
 * Explicit states representing contact status and action tracking.
 *
 * CRITICAL SAFETY INVARIANT:
 * "Unblock All" must ONLY target contacts with status [USER_CONFIRMED_BLOCKED], [BLOCKED_CONFIRMED], or [BLOCKED_BY_APP].
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

    /** Action required: User must manually complete the action in WhatsApp. */
    ACTION_REQUIRED,

    /** User explicitly confirmed that they manually blocked this contact in WhatsApp through this app. Eligible for Unblock All. */
    USER_CONFIRMED_BLOCKED,

    /** Contact was confirmed blocked through this app. Eligible for Unblock All. */
    BLOCKED_CONFIRMED,

    /** Backward-compatible alias for confirmed app blocks. */
    BLOCKED_BY_APP,

    /** User explicitly confirmed that they manually unblocked this contact in WhatsApp through this app. */
    USER_CONFIRMED_UNBLOCKED,

    /** Contact was confirmed unblocked through this app. */
    UNBLOCKED_CONFIRMED,

    /** Contact was manually blocked outside of this app prior to the session. Must NEVER be unblocked automatically. */
    MANUALLY_BLOCKED_EXTERNAL,

    /** Contact was confirmed blocked via supervised Accessibility Service automation. Eligible for Unblock All. */
    AUTO_BLOCKED_BY_APP,

    /** Automated flow completed or encountered ambiguous screen requiring manual verification. NOT eligible for Unblock All until verified. */
    NEEDS_MANUAL_VERIFICATION,

    /** Attempted action failed, was cancelled, or could not be verified. */
    FAILED
}
