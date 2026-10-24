package com.example.whatsappfocusblocker.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Android Accessibility Service for WhatsApp Focus Blocker.
 *
 * Strict Privacy & Safety Invariants:
 * 1. Scoped strictly to WhatsApp packages ("com.whatsapp", "com.whatsapp.w4b").
 * 2. Never reads message contents, chat transcripts, or media.
 * 3. Never captures, transmits, or stores user data externally.
 * 4. Used exclusively to assist the user by detecting expected contact blocking UI
 *    and performing supervised, user-authorized actions.
 */
class WhatsAppAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        _isServiceActive.value = true
        activeServiceInstance = this

        try {
            val info = serviceInfo ?: AccessibilityServiceInfo()
            info.flags = info.flags or
                    AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            serviceInfo = info
        } catch (e: Exception) {
            Log.w(TAG, "Failed to set FLAG_REPORT_VIEW_IDS on serviceInfo: ${e.message}")
        }

        Log.i(TAG, "WhatsAppAccessibilityService connected and active with FLAG_REPORT_VIEW_IDS.")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        activeServiceInstance = this

        val pkgName = event.packageName?.toString() ?: return
        // Guardrail: Ignore all events outside WhatsApp
        if (pkgName != WHATSAPP_PKG && pkgName != WHATSAPP_BUSINESS_PKG) {
            return
        }

        // Privacy-safe diagnostic logging (Event type & class name only, NO message content)
        val eventTypeStr = AccessibilityEvent.eventTypeToString(event.eventType)
        Log.d(TAG, "[WABlocker] WhatsApp event: $eventTypeStr (class=${event.className})")

        // Forward to registered listener (Phase 2 state machine hook)
        eventListener?.invoke(event)
    }

    override fun onInterrupt() {
        Log.w(TAG, "WhatsAppAccessibilityService interrupted by system.")
    }

    override fun onDestroy() {
        super.onDestroy()
        _isServiceActive.value = false
        if (activeServiceInstance === this) {
            activeServiceInstance = null
        }
        Log.i(TAG, "WhatsAppAccessibilityService destroyed.")
    }

    companion object {
        private const val TAG = "WhatsAppAccService"
        const val WHATSAPP_PKG = "com.whatsapp"
        const val WHATSAPP_BUSINESS_PKG = "com.whatsapp.w4b"

        private val _isServiceActive = MutableStateFlow(false)
        val isServiceActive: StateFlow<Boolean> = _isServiceActive.asStateFlow()

        @Volatile
        var activeServiceInstance: WhatsAppAccessibilityService? = null
            private set

        /**
         * Listener invoked on WhatsApp accessibility events for automated state handling.
         */
        @Volatile
        var eventListener: ((AccessibilityEvent) -> Unit)? = null
    }
}
