package com.example.whatsappfocusblocker.accessibility

import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
import org.junit.Test

class AccessibilityServiceHelperTest {

    @Test
    fun whatsappAccessibilityService_constantsAreCorrect() {
        assertEquals("com.whatsapp", WhatsAppAccessibilityService.WHATSAPP_PKG)
        assertEquals("com.whatsapp.w4b", WhatsAppAccessibilityService.WHATSAPP_BUSINESS_PKG)
        assertNotNull(WhatsAppAccessibilityService.isServiceActive)
    }
}
