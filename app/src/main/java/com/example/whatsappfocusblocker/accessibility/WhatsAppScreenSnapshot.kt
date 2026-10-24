package com.example.whatsappfocusblocker.accessibility

/**
 * Screen snapshot representation extracted from WhatsApp's accessibility window hierarchy.
 *
 * Privacy Invariants:
 * 1. Strictly captures control elements and header identifiers required for block validation.
 * 2. Never captures message text, chat history, attachments, or personal conversation contents.
 */
data class WhatsAppScreenSnapshot(
    val packageName: String,
    val chatTitle: String? = null,
    val hasInviteToWhatsApp: Boolean = false,
    val hasSafetyBannerBlock: Boolean = false,
    val hasOverflowButton: Boolean = false,
    val isOverflowMenuVisible: Boolean = false,
    val menuItems: List<String> = emptyList(),
    val isBlockDialogVisible: Boolean = false,
    val blockDialogTitle: String? = null,
    val hasReportCheckbox: Boolean = false,
    val isReportCheckboxChecked: Boolean = false,
    val isReportCheckboxAmbiguous: Boolean = false,
    val hasPositiveBlockButton: Boolean = false,
    val hasBlockedStatusBanner: Boolean = false
)
