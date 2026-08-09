package com.rally26.settings.application

import com.rally26.settings.domain.NotificationContactContext
import com.rally26.settings.domain.NotificationPreferenceState
import com.rally26.settings.domain.NotificationTopic
import com.rally26.settings.domain.UserNotificationPreference
import com.rally26.settings.persistence.NotificationPreferenceRepository
import io.mockk.every
import io.mockk.mockk
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NotificationDeliveryResolverTest {
    private val repository = mockk<NotificationPreferenceRepository>()
    private val resolver = NotificationDeliveryResolver(repository)

    @Test
    fun `contact-only recipient keeps the legacy-resolved destinations`() {
        val decision =
            resolver.resolve(
                userId = null,
                householdId = UUID.randomUUID(),
                topic = NotificationTopic.FEES_PAYMENTS,
                candidateEmail = "guardian@example.com",
                candidatePhone = "+15555550123",
            )

        assertFalse(decision.inApp)
        assertEquals("guardian@example.com", decision.email)
        assertEquals("+15555550123", decision.sms)
    }

    @Test
    fun `explicit account email enable overrides legacy household email opt-out`() {
        val userId = UUID.randomUUID()
        val householdId = UUID.randomUUID()
        every { repository.find(userId, NotificationTopic.ANNOUNCEMENTS) } returns
            preference(userId, NotificationTopic.ANNOUNCEMENTS, email = NotificationPreferenceState.ENABLED)
        every { repository.contactContext(userId, householdId) } returns
            NotificationContactContext("account@example.com", "+15555550123", legacyEmailAllowed = false)
        every { repository.currentSmsConsent(userId) } returns false

        val decision = resolver.resolve(userId, householdId, NotificationTopic.ANNOUNCEMENTS, null, null)

        assertTrue(decision.inApp)
        assertEquals("account@example.com", decision.email)
        assertNull(decision.sms)
    }

    @Test
    fun `already eligible staff email is not suppressed by a guardian household opt-out`() {
        val userId = UUID.randomUUID()
        val householdId = UUID.randomUUID()
        every { repository.find(userId, NotificationTopic.ANNOUNCEMENTS) } returns null
        every { repository.contactContext(userId, householdId) } returns
            NotificationContactContext("guardian-profile@example.com", null, legacyEmailAllowed = false)
        every { repository.currentSmsConsent(userId) } returns false

        val decision =
            resolver.resolve(
                userId = userId,
                householdId = householdId,
                topic = NotificationTopic.ANNOUNCEMENTS,
                candidateEmail = "staff@example.com",
                candidatePhone = null,
            )

        assertEquals("staff@example.com", decision.email)
    }

    @Test
    fun `sms requires current account consent and explicit topic enable`() {
        val userId = UUID.randomUUID()
        val householdId = UUID.randomUUID()
        every { repository.find(userId, NotificationTopic.EVENTS_SCHEDULE) } returns
            preference(userId, NotificationTopic.EVENTS_SCHEDULE, sms = NotificationPreferenceState.ENABLED)
        every { repository.contactContext(userId, householdId) } returns
            NotificationContactContext("account@example.com", "+15555550123", legacyEmailAllowed = true)
        every { repository.currentSmsConsent(userId) } returnsMany listOf(false, true)

        val withoutConsent = resolver.resolve(userId, householdId, NotificationTopic.EVENTS_SCHEDULE, null, null)
        val withConsent = resolver.resolve(userId, householdId, NotificationTopic.EVENTS_SCHEDULE, null, null)

        assertNull(withoutConsent.sms)
        assertEquals("+15555550123", withConsent.sms)
    }

    private fun preference(
        userId: UUID,
        topic: NotificationTopic,
        inApp: NotificationPreferenceState = NotificationPreferenceState.DEFAULT,
        email: NotificationPreferenceState = NotificationPreferenceState.DEFAULT,
        sms: NotificationPreferenceState = NotificationPreferenceState.DEFAULT,
    ) = UserNotificationPreference(
        userId = userId,
        topic = topic,
        inAppState = inApp,
        emailState = email,
        smsState = sms,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )
}
