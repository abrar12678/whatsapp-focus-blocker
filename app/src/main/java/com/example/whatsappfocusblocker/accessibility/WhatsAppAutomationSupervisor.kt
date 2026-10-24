package com.example.whatsappfocusblocker.accessibility

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.whatsappfocusblocker.domain.model.FocusContact
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Supervised coordinator managing single-contact automation sessions.
 */
object WhatsAppAutomationSupervisor {
    private const val TAG = "WhatsAppSupervisor"

    private val _currentState = MutableStateFlow(BlockAutomationState.IDLE)
    val currentState: StateFlow<BlockAutomationState> = _currentState.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>("Ready")
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    @Volatile
    var activeStateMachine: WhatsAppBlockStateMachine? = null
        private set

    private var watchdogHandler: Handler? = null
    private var watchdogRunnable: Runnable? = null

    var onAutomationCompleted: ((FocusContact, Boolean, String) -> Unit)? = null

    fun startSingleContactBlock(contact: FocusContact) {
        stopAutomation("Starting new session")

        val stateMachine = WhatsAppBlockStateMachine(contact)
        activeStateMachine = stateMachine
        _currentState.value = BlockAutomationState.OPENING_CHAT
        _statusMessage.value = "Opening chat for ${contact.name}..."

        // Register event listener with Accessibility Service
        WhatsAppAccessibilityService.eventListener = { event ->
            handleAccessibilityEvent()
        }

        startWatchdog()
        stateMachine.start()
    }

    fun stopAutomation(reason: String = "Stopped by user") {
        stopWatchdog()
        val sm = activeStateMachine
        if (sm != null && !WhatsAppBlockStateMachine.isTerminalState(sm.state)) {
            val action = sm.cancel(reason)
            _currentState.value = BlockAutomationState.CANCELLED_BY_USER
            _statusMessage.value = reason
            WhatsAppAccessibilityService.eventListener = null
            activeStateMachine = null
            onAutomationCompleted?.invoke(sm.targetContact, false, reason)
        } else {
            WhatsAppAccessibilityService.eventListener = null
            activeStateMachine = null
            _currentState.value = BlockAutomationState.IDLE
            _statusMessage.value = reason
        }
    }

    private fun handleAccessibilityEvent() {
        val sm = activeStateMachine ?: return
        val service = WhatsAppAccessibilityService.activeServiceInstance ?: return
        val rootNode = service.rootInActiveWindow ?: return

        val snapshot = WhatsAppAccessibilityInspector.inspectCurrentScreen(
            rootNode,
            WhatsAppAccessibilityService.WHATSAPP_PKG
        )

        val action = sm.onScreenSnapshot(snapshot)
        _currentState.value = sm.state
        executeAction(action, service)
    }

    private fun executeAction(action: WhatsAppBlockAction, service: WhatsAppAccessibilityService) {
        val sm = activeStateMachine ?: return
        val root = service.rootInActiveWindow

        when (action) {
            WhatsAppBlockAction.None -> Unit

            WhatsAppBlockAction.OpenOverflowMenu -> {
                _statusMessage.value = "Opening options menu..."
                WhatsAppAccessibilityInspector.findAndClickOverflow(root)
            }

            WhatsAppBlockAction.ClickSafetyBannerBlock -> {
                _statusMessage.value = "Selecting Block from safety banner..."
                WhatsAppAccessibilityInspector.findAndClickSafetyBannerBlock(root)
            }

            WhatsAppBlockAction.ClickMoreMenuItem -> {
                _statusMessage.value = "Opening 'More' submenu..."
                WhatsAppAccessibilityInspector.findAndClickMenuItem(root, "More")
            }

            WhatsAppBlockAction.ClickBlockMenuItem -> {
                _statusMessage.value = "Selecting 'Block'..."
                WhatsAppAccessibilityInspector.findAndClickMenuItem(root, "Block")
            }

            WhatsAppBlockAction.UncheckReportCheckbox -> {
                _statusMessage.value = "Guardrail: Unchecking 'Report to WhatsApp' checkbox..."
                WhatsAppAccessibilityInspector.findAndUncheckReportCheckbox(root)
            }

            WhatsAppBlockAction.ClickConfirmBlockButton -> {
                _statusMessage.value = "Confirming block dialog..."
                WhatsAppAccessibilityInspector.findAndClickPositiveBlock(root)
            }

            is WhatsAppBlockAction.PauseForUser -> {
                _statusMessage.value = "Automation paused: ${action.reason}"
                stopWatchdog()
            }

            is WhatsAppBlockAction.Cancel -> {
                _statusMessage.value = "Cancelled: ${action.reason}"
                stopAutomation(action.reason)
            }

            is WhatsAppBlockAction.CompletedSuccess -> {
                _statusMessage.value = "Block verified successfully via banner!"
                stopWatchdog()
                WhatsAppAccessibilityService.eventListener = null
                activeStateMachine = null
                onAutomationCompleted?.invoke(sm.targetContact, true, "Verified via accessibility banner")
            }

            is WhatsAppBlockAction.CompletedNeedsVerification -> {
                _statusMessage.value = "Action attempted: ${action.reason}"
                stopWatchdog()
                WhatsAppAccessibilityService.eventListener = null
                activeStateMachine = null
                onAutomationCompleted?.invoke(sm.targetContact, false, action.reason)
            }
        }
    }

    private fun startWatchdog() {
        stopWatchdog()
        try {
            val mainLooper = Looper.getMainLooper() ?: return
            val handler = Handler(mainLooper)
            watchdogHandler = handler

            val runnable = object : Runnable {
                override fun run() {
                    val sm = activeStateMachine
                    if (sm != null && !WhatsAppBlockStateMachine.isTerminalState(sm.state)) {
                        val service = WhatsAppAccessibilityService.activeServiceInstance
                        val action = sm.onWatchdogTick()
                        _currentState.value = sm.state

                        if (service != null && action !is WhatsAppBlockAction.None) {
                            executeAction(action, service)
                        }

                        if (!WhatsAppBlockStateMachine.isTerminalState(sm.state) &&
                            sm.state != BlockAutomationState.PAUSED_FOR_MANUAL_INTERVENTION
                        ) {
                            handler.postDelayed(this, 300L)
                        }
                    }
                }
            }
            watchdogRunnable = runnable
            handler.postDelayed(runnable, 300L)
        } catch (e: Exception) {
            Log.w(TAG, "Watchdog could not start on thread without looper: ${e.message}")
        }
    }

    private fun stopWatchdog() {
        watchdogRunnable?.let { watchdogHandler?.removeCallbacks(it) }
        watchdogRunnable = null
        watchdogHandler = null
    }
}
