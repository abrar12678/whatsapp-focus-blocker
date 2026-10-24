package com.example.whatsappfocusblocker.util

import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNull
import org.junit.Test

class WhatsAppLauncherTest {

    @Test
    fun cleanPhoneNumber_normalizesInternationalNumbers() {
        assertEquals("12345678901", WhatsAppLauncher.cleanPhoneNumber("+1 (234) 567-8901"))
        assertEquals("8801712345678", WhatsAppLauncher.cleanPhoneNumber("+880 1712-345678"))
        assertEquals("447911123456", WhatsAppLauncher.cleanPhoneNumber("+44 7911 123456"))
    }

    @Test
    fun cleanPhoneNumber_stripsLeadingZeroes() {
        assertEquals("1712345678", WhatsAppLauncher.cleanPhoneNumber("01712345678"))
        assertEquals("1712345678", WhatsAppLauncher.cleanPhoneNumber("001712345678"))
    }

    @Test
    fun cleanPhoneNumber_rejectsInvalidOrTooShortNumbers() {
        assertNull(WhatsAppLauncher.cleanPhoneNumber(""))
        assertNull(WhatsAppLauncher.cleanPhoneNumber("123"))
        assertNull(WhatsAppLauncher.cleanPhoneNumber("abc-def"))
        assertNull(WhatsAppLauncher.cleanPhoneNumber("+++"))
    }
}
