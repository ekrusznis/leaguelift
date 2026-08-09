package com.rally26.settings.domain

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NotificationPreferencePolicyTest {
    @Test
    fun `optional in-app defaults on and explicit disable wins`() {
        assertTrue(NotificationPreferencePolicy.enabled(NotificationPreferenceState.DEFAULT, NotificationChannel.IN_APP))
        assertFalse(NotificationPreferencePolicy.enabled(NotificationPreferenceState.DISABLED, NotificationChannel.IN_APP))
    }

    @Test
    fun `email default honors the legacy household compatibility guard`() {
        assertTrue(
            NotificationPreferencePolicy.enabled(NotificationPreferenceState.DEFAULT, NotificationChannel.EMAIL, legacyEmailAllowed = true),
        )
        assertFalse(
            NotificationPreferencePolicy.enabled(
                NotificationPreferenceState.DEFAULT,
                NotificationChannel.EMAIL,
                legacyEmailAllowed = false,
            ),
        )
        assertTrue(
            NotificationPreferencePolicy.enabled(
                NotificationPreferenceState.ENABLED,
                NotificationChannel.EMAIL,
                legacyEmailAllowed = false,
            ),
        )
    }

    @Test
    fun `sms requires both individual consent and an explicit topic enable`() {
        assertFalse(
            NotificationPreferencePolicy.enabled(NotificationPreferenceState.DEFAULT, NotificationChannel.SMS, smsConsented = true),
        )
        assertFalse(
            NotificationPreferencePolicy.enabled(NotificationPreferenceState.ENABLED, NotificationChannel.SMS, smsConsented = false),
        )
        assertTrue(
            NotificationPreferencePolicy.enabled(NotificationPreferenceState.ENABLED, NotificationChannel.SMS, smsConsented = true),
        )
        assertFalse(
            NotificationPreferencePolicy.enabled(NotificationPreferenceState.DISABLED, NotificationChannel.SMS, smsConsented = true),
        )
    }
}
