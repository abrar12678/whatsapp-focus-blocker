package com.example.whatsappfocusblocker.accessibility

import com.example.whatsappfocusblocker.domain.model.FocusContact

/**
 * Safe, guarded sequential state machine for automated single-contact blocking in WhatsApp.
 *
 * Safety & Privacy Guardrails:
 * 1. Single Contact Only: Restricted strictly to one user-selected test contact.
 * 2. Identity Verification: Validates that the active WhatsApp chat header corresponds to the
 *    target contact (by matching contact name or phone number digits).
 * 3. Spam Report Protection: Detects the "Report to WhatsApp" checkbox in the block confirmation
 *    dialog and mandates unchecking it before proceeding. If ambiguous or failed, pauses for manual review.
 * 4. Watchdog Timer: Enforces a per-step timeout (default 3500ms). If a step stalls, it safely pauses
 *    instead of mis-clicking.
 * 5. Evidence-based Verification: Only marks SUCCESS when the "You blocked this contact" banner is
 *    detected; otherwise falls back to NEEDS_MANUAL_VERIFICATION.
 * 6. Immediate STOP: Emergency user cancellation halts state transitions instantly.
 */
class WhatsAppBlockStateMachine(
    val targetContact: FocusContact,
    private val timeoutMillis: Long = 3500L
) {
    var state: BlockAutomationState = BlockAutomationState.IDLE
        private set

    var stateEnterTimestamp: Long = 0L
        private set

    var pauseReason: String? = null
        private set

    var cancelReason: String? = null
        private set

    fun start(currentTimeMillis: Long = System.currentTimeMillis()): WhatsAppBlockAction {
        state = BlockAutomationState.OPENING_CHAT
        stateEnterTimestamp = currentTimeMillis
        pauseReason = null
        cancelReason = null
        return WhatsAppBlockAction.None
    }

    fun cancel(reason: String = "Cancelled by user"): WhatsAppBlockAction {
        state = BlockAutomationState.CANCELLED_BY_USER
        cancelReason = reason
        return WhatsAppBlockAction.Cancel(reason)
    }

    fun onWatchdogTick(currentTimeMillis: Long = System.currentTimeMillis()): WhatsAppBlockAction {
        if (isTerminalState(state) || state == BlockAutomationState.PAUSED_FOR_MANUAL_INTERVENTION) {
            return WhatsAppBlockAction.None
        }

        if (currentTimeMillis - stateEnterTimestamp > timeoutMillis) {
            val elapsed = currentTimeMillis - stateEnterTimestamp
            return if (state == BlockAutomationState.VERIFYING_BLOCKED_STATUS) {
                // Block was confirmed in dialog, but confirmation banner was not detected before timeout.
                // Invariant: NEVER assume success without verified banner evidence.
                transitionTo(BlockAutomationState.NEEDS_MANUAL_VERIFICATION, currentTimeMillis)
                WhatsAppBlockAction.CompletedNeedsVerification(
                    targetContact.id,
                    "Block confirmed in dialog, but blocked banner could not be verified automatically."
                )
            } else {
                pause("Watchdog timeout in state $state after ${elapsed}ms", currentTimeMillis)
            }
        }
        return WhatsAppBlockAction.None
    }

    fun onScreenSnapshot(
        snapshot: WhatsAppScreenSnapshot,
        currentTimeMillis: Long = System.currentTimeMillis()
    ): WhatsAppBlockAction {
        if (isTerminalState(state) || state == BlockAutomationState.PAUSED_FOR_MANUAL_INTERVENTION) {
            return WhatsAppBlockAction.None
        }

        // Guardrail: Package must be WhatsApp
        if (snapshot.packageName != WhatsAppAccessibilityService.WHATSAPP_PKG &&
            snapshot.packageName != WhatsAppAccessibilityService.WHATSAPP_BUSINESS_PKG
        ) {
            return pause("Unexpected foreground application: ${snapshot.packageName}", currentTimeMillis)
        }

        when (state) {
            BlockAutomationState.OPENING_CHAT -> {
                transitionTo(BlockAutomationState.AWAITING_CHAT_UI, currentTimeMillis)
                return onScreenSnapshot(snapshot, currentTimeMillis)
            }

            BlockAutomationState.AWAITING_CHAT_UI -> {
                if (snapshot.hasInviteToWhatsApp) {
                    return pause("Contact is not registered on WhatsApp ('Invite' prompt displayed)", currentTimeMillis)
                }

                if (snapshot.chatTitle != null || snapshot.hasOverflowButton || snapshot.hasSafetyBannerBlock) {
                    transitionTo(BlockAutomationState.VALIDATING_CONTACT_IDENTITY, currentTimeMillis)
                    return onScreenSnapshot(snapshot, currentTimeMillis)
                }
            }

            BlockAutomationState.VALIDATING_CONTACT_IDENTITY -> {
                if (validateContactIdentity(snapshot.chatTitle)) {
                    if (snapshot.hasSafetyBannerBlock) {
                        // Safety tools banner provides direct 'Block' tap
                        transitionTo(BlockAutomationState.AWAITING_BLOCK_DIALOG, currentTimeMillis)
                        return WhatsAppBlockAction.ClickSafetyBannerBlock
                    } else if (snapshot.hasOverflowButton) {
                        transitionTo(BlockAutomationState.OPENING_OVERFLOW_MENU, currentTimeMillis)
                        return WhatsAppBlockAction.OpenOverflowMenu
                    } else {
                        return pause("Unable to find menu overflow or safety banner in verified chat", currentTimeMillis)
                    }
                } else {
                    return pause(
                        "Chat title '${snapshot.chatTitle ?: "Unknown"}' does not match target contact '${targetContact.name}' (${targetContact.phoneNumber})",
                        currentTimeMillis
                    )
                }
            }

            BlockAutomationState.OPENING_OVERFLOW_MENU -> {
                if (snapshot.isOverflowMenuVisible || snapshot.menuItems.isNotEmpty()) {
                    val hasBlock = snapshot.menuItems.any { it.contains("Block", ignoreCase = true) }
                    val hasMore = snapshot.menuItems.any { it.contains("More", ignoreCase = true) }

                    if (hasBlock) {
                        transitionTo(BlockAutomationState.AWAITING_BLOCK_DIALOG, currentTimeMillis)
                        return WhatsAppBlockAction.ClickBlockMenuItem
                    } else if (hasMore) {
                        transitionTo(BlockAutomationState.SELECTING_MORE, currentTimeMillis)
                        return WhatsAppBlockAction.ClickMoreMenuItem
                    } else {
                        return pause("Overflow menu items did not contain expected 'More' or 'Block'", currentTimeMillis)
                    }
                }
            }

            BlockAutomationState.SELECTING_MORE -> {
                val hasBlock = snapshot.menuItems.any { it.contains("Block", ignoreCase = true) }
                if (hasBlock) {
                    transitionTo(BlockAutomationState.AWAITING_BLOCK_DIALOG, currentTimeMillis)
                    return WhatsAppBlockAction.ClickBlockMenuItem
                }
            }

            BlockAutomationState.AWAITING_BLOCK_DIALOG -> {
                if (snapshot.isBlockDialogVisible) {
                    transitionTo(BlockAutomationState.INSPECTING_REPORT_CHECKBOX, currentTimeMillis)
                    return onScreenSnapshot(snapshot, currentTimeMillis)
                }
            }

            BlockAutomationState.INSPECTING_REPORT_CHECKBOX -> {
                if (snapshot.isReportCheckboxAmbiguous) {
                    return pause(
                        "Ambiguous checkbox detected in block dialog. Pausing to avoid accidental spam reporting.",
                        currentTimeMillis
                    )
                }

                if (snapshot.hasReportCheckbox && snapshot.isReportCheckboxChecked) {
                    // Checkbox is checked -> safely request unchecking it before block confirmation
                    return WhatsAppBlockAction.UncheckReportCheckbox
                }

                // Checkbox is either absent or confirmed unchecked -> proceed to confirm block
                if (!snapshot.hasReportCheckbox || !snapshot.isReportCheckboxChecked) {
                    if (snapshot.hasPositiveBlockButton) {
                        transitionTo(BlockAutomationState.VERIFYING_BLOCKED_STATUS, currentTimeMillis)
                        return WhatsAppBlockAction.ClickConfirmBlockButton
                    } else {
                        return pause("Could not locate positive 'Block' confirmation button in dialog", currentTimeMillis)
                    }
                }
            }

            BlockAutomationState.CONFIRMING_BLOCK -> {
                transitionTo(BlockAutomationState.VERIFYING_BLOCKED_STATUS, currentTimeMillis)
                return onScreenSnapshot(snapshot, currentTimeMillis)
            }

            BlockAutomationState.VERIFYING_BLOCKED_STATUS -> {
                if (snapshot.hasBlockedStatusBanner) {
                    transitionTo(BlockAutomationState.COMPLETED_SUCCESS, currentTimeMillis)
                    return WhatsAppBlockAction.CompletedSuccess(targetContact.id)
                }
            }

            else -> {
                // No action in terminal or paused state
            }
        }

        return WhatsAppBlockAction.None
    }

    private fun pause(reason: String, currentTimeMillis: Long): WhatsAppBlockAction {
        state = BlockAutomationState.PAUSED_FOR_MANUAL_INTERVENTION
        stateEnterTimestamp = currentTimeMillis
        pauseReason = reason
        return WhatsAppBlockAction.PauseForUser(reason)
    }

    private fun transitionTo(newState: BlockAutomationState, currentTimeMillis: Long) {
        state = newState
        stateEnterTimestamp = currentTimeMillis
    }

    fun validateContactIdentity(title: String?): Boolean {
        if (title.isNullOrBlank()) return false
        val cleanTitle = title.trim().lowercase()
        val cleanContactName = targetContact.name.trim().lowercase()
        val rawContactPhone = targetContact.phoneNumber.replace(Regex("[^0-9]"), "")
        val titleDigits = title.replace(Regex("[^0-9]"), "")

        // Name match (title contains contact name or vice versa)
        if (cleanContactName.isNotBlank() && (cleanTitle.contains(cleanContactName) || cleanContactName.contains(cleanTitle))) {
            return true
        }

        // Phone suffix match (last 7 or more digits)
        if (rawContactPhone.length >= 7 && titleDigits.length >= 7) {
            val contactSuffix = rawContactPhone.takeLast(7)
            val titleSuffix = titleDigits.takeLast(7)
            if (contactSuffix == titleSuffix) {
                return true
            }
        }

        return false
    }

    companion object {
        fun isTerminalState(state: BlockAutomationState): Boolean {
            return state == BlockAutomationState.IDLE ||
                    state == BlockAutomationState.COMPLETED_SUCCESS ||
                    state == BlockAutomationState.NEEDS_MANUAL_VERIFICATION ||
                    state == BlockAutomationState.CANCELLED_BY_USER ||
                    state == BlockAutomationState.FAILED
        }
    }
}
