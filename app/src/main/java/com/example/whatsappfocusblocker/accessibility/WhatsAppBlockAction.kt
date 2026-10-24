package com.example.whatsappfocusblocker.accessibility

/**
 * Supervised actions emitted by the state machine to be performed on WhatsApp UI.
 */
sealed class WhatsAppBlockAction {
    object None : WhatsAppBlockAction()
    object OpenOverflowMenu : WhatsAppBlockAction()
    object ClickSafetyBannerBlock : WhatsAppBlockAction()
    object ClickMoreMenuItem : WhatsAppBlockAction()
    object ClickBlockMenuItem : WhatsAppBlockAction()
    object UncheckReportCheckbox : WhatsAppBlockAction()
    object ClickConfirmBlockButton : WhatsAppBlockAction()
    data class PauseForUser(val reason: String) : WhatsAppBlockAction()
    data class Cancel(val reason: String) : WhatsAppBlockAction()
    data class CompletedSuccess(val contactId: String) : WhatsAppBlockAction()
    data class CompletedNeedsVerification(val contactId: String, val reason: String) : WhatsAppBlockAction()
}
