package com.example.whatsappfocusblocker.accessibility

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.text.TextUtils

object AccessibilityServiceHelper {

    /**
     * Checks whether [WhatsAppAccessibilityService] is enabled and active in Android Settings.
     */
    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        if (WhatsAppAccessibilityService.isServiceActive.value) {
            return true
        }

        val expectedComponentName = ComponentName(context, WhatsAppAccessibilityService::class.java)
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val colonSplitter = TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServices)

        while (colonSplitter.hasNext()) {
            val componentNameString = colonSplitter.next()
            val enabledComponent = ComponentName.unflattenFromString(componentNameString)
            if (enabledComponent != null && enabledComponent == expectedComponentName) {
                return true
            }
        }
        return false
    }

    /**
     * Intent to open the Android Accessibility Settings page where the user can enable the service.
     */
    fun createOpenAccessibilitySettingsIntent(): Intent {
        return Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }

    /**
     * Intent to open the App Details Settings page.
     * Required on Android 13+ (API 33+) if the accessibility toggle is disabled by "Restricted Settings".
     * Users tap top-right 3-dots -> "Allow restricted settings" before enabling accessibility.
     */
    fun createOpenAppDetailsSettingsIntent(context: Context): Intent {
        return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }
}
