package com.example.whatsappfocusblocker.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

sealed interface WhatsAppLaunchResult {
    data object Success : WhatsAppLaunchResult
    data object WhatsAppNotInstalled : WhatsAppLaunchResult
    data class InvalidPhoneNumber(val message: String) : WhatsAppLaunchResult
    data class Error(val throwable: Throwable) : WhatsAppLaunchResult
}

object WhatsAppLauncher {

    /**
     * Cleans and normalizes phone numbers for WhatsApp deep links.
     * WhatsApp expects digits only with country code (e.g. 88017XXXXXXXX).
     */
    fun cleanPhoneNumber(rawNumber: String): String? {
        val digits = rawNumber.replace(Regex("[^0-9]"), "")
        if (digits.length < 5) return null

        // If local Bangladeshi number (11 digits starting with 01), prepend country code 880
        if (digits.startsWith("01") && digits.length == 11) {
            return "880" + digits.substring(1)
        }

        // If number begins with double-zero international prefix (e.g. 00880...), strip leading 00
        if (digits.startsWith("00")) {
            val stripped = digits.trimStart('0')
            return if (stripped.length >= 7) stripped else null
        }

        // If already full international number without leading 0
        if (!digits.startsWith("0") && digits.length >= 10) {
            return digits
        }

        // Fallback: strip leading zeros if present
        val stripped = digits.trimStart('0')
        return if (stripped.isNotEmpty()) stripped else null
    }

    /**
     * Launches WhatsApp chat using the official deep link.
     *
     * @param context Android context
     * @param rawNumber Contact's raw phone number
     * @return [WhatsAppLaunchResult] indicating success, missing app, or invalid number
     */
    fun openChat(context: Context, rawNumber: String): WhatsAppLaunchResult {
        val cleanNumber = cleanPhoneNumber(rawNumber)
            ?: return WhatsAppLaunchResult.InvalidPhoneNumber("Phone number '$rawNumber' does not contain enough digits.")

        val primaryUri = Uri.parse("whatsapp://send?phone=$cleanNumber")
        val primaryIntent = Intent(Intent.ACTION_VIEW, primaryUri).apply {
            setPackage("com.whatsapp")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        return try {
            context.startActivity(primaryIntent)
            WhatsAppLaunchResult.Success
        } catch (e: ActivityNotFoundException) {
            // Fallback to web universal link or Play Store
            tryWebFallback(context, cleanNumber)
        } catch (e: SecurityException) {
            WhatsAppLaunchResult.Error(e)
        } catch (e: Exception) {
            WhatsAppLaunchResult.Error(e)
        }
    }

    private fun tryWebFallback(context: Context, cleanNumber: String): WhatsAppLaunchResult {
        val webUri = Uri.parse("https://api.whatsapp.com/send?phone=$cleanNumber")
        val webIntent = Intent(Intent.ACTION_VIEW, webUri).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        return try {
            context.startActivity(webIntent)
            WhatsAppLaunchResult.Success
        } catch (e: Exception) {
            WhatsAppLaunchResult.WhatsAppNotInstalled
        }
    }

    /**
     * Launches the Google Play Store page for WhatsApp.
     */
    fun openPlayStoreForWhatsApp(context: Context) {
        val playStoreUri = Uri.parse("market://details?id=com.whatsapp")
        val intent = Intent(Intent.ACTION_VIEW, playStoreUri).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            val webIntent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://play.google.com/store/apps/details?id=com.whatsapp")
            ).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
            context.startActivity(webIntent)
        }
    }
}
