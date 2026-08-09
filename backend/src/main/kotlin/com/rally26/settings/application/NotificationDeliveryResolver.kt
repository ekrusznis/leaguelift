package com.rally26.settings.application

import com.rally26.settings.domain.NotificationChannel
import com.rally26.settings.domain.NotificationDeliveryDecision
import com.rally26.settings.domain.NotificationPreferencePolicy
import com.rally26.settings.domain.NotificationPreferenceState
import com.rally26.settings.domain.NotificationTopic
import com.rally26.settings.persistence.NotificationPreferenceRepository
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class NotificationDeliveryResolver(
    private val repository: NotificationPreferenceRepository,
) {
    fun resolve(
        userId: UUID?,
        householdId: UUID?,
        topic: NotificationTopic,
        candidateEmail: String?,
        candidatePhone: String?,
    ): NotificationDeliveryDecision {
        if (userId == null) {
            // Legacy contact-only recipients were already filtered by the household flags
            // in the existing recipient resolver. Keep that behavior until the contact activates an account.
            return NotificationDeliveryDecision(false, candidateEmail, candidatePhone)
        }
        val preference = repository.find(userId, topic)
        val contact = repository.contactContext(userId, householdId)
        val smsConsented = repository.currentSmsConsent(userId)
        val inAppState = preference?.inAppState ?: NotificationPreferenceState.DEFAULT
        val emailState = preference?.emailState ?: NotificationPreferenceState.DEFAULT
        val smsState = preference?.smsState ?: NotificationPreferenceState.DEFAULT
        val email = candidateEmail?.takeIf { it.isNotBlank() } ?: contact?.email
        val phone = candidatePhone?.takeIf { it.isNotBlank() } ?: contact?.phone
        val legacyEmailAllowed = if (!candidateEmail.isNullOrBlank()) true else contact?.legacyEmailAllowed ?: true

        return NotificationDeliveryDecision(
            inApp = NotificationPreferencePolicy.enabled(inAppState, NotificationChannel.IN_APP),
            email = if (NotificationPreferencePolicy.enabled(emailState, NotificationChannel.EMAIL, legacyEmailAllowed)) email else null,
            sms = if (NotificationPreferencePolicy.enabled(smsState, NotificationChannel.SMS, smsConsented = smsConsented)) phone else null,
        )
    }
}
