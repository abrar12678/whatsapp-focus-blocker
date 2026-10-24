package com.example.whatsappfocusblocker.accessibility

import kotlinx.serialization.Serializable

/**
 * Sequential states for the supervised single-contact WhatsApp block automation.
 */
@Serializable
enum class BlockAutomationState {
    IDLE,
    OPENING_CHAT,
    AWAITING_CHAT_UI,
    VALIDATING_CONTACT_IDENTITY,
    OPENING_OVERFLOW_MENU,
    SELECTING_MORE,
    AWAITING_BLOCK_DIALOG,
    INSPECTING_REPORT_CHECKBOX,
    CONFIRMING_BLOCK,
    VERIFYING_BLOCKED_STATUS,
    COMPLETED_SUCCESS,
    PAUSED_FOR_MANUAL_INTERVENTION,
    NEEDS_MANUAL_VERIFICATION,
    CANCELLED_BY_USER,
    FAILED
}
