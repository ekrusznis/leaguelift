package com.rally26.settings.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.rally26.audit.application.AuditService
import com.rally26.settings.domain.NotificationPreferenceState
import com.rally26.settings.domain.NotificationTopic
import com.rally26.settings.domain.SmsConsentState
import com.rally26.settings.persistence.NotificationPreferenceRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

data class NotificationTopicPreferences(
    val topic: NotificationTopic,
    val inAppState: NotificationPreferenceState,
    val emailState: NotificationPreferenceState,
    val smsState: NotificationPreferenceState,
)

data class NotificationPreferences(
    val smsConsent: Boolean,
    val topics: List<NotificationTopicPreferences>,
)

@Service
class NotificationPreferenceService(
    private val repository: NotificationPreferenceRepository,
    private val auditService: AuditService,
    private val objectMapper: ObjectMapper,
) {
    fun get(userId: UUID): NotificationPreferences {
        val stored = repository.listForUser(userId).associateBy { it.topic }
        return NotificationPreferences(
            smsConsent = repository.currentSmsConsent(userId),
            topics =
                NotificationTopic.entries.map { topic ->
                    stored[topic]?.let {
                        NotificationTopicPreferences(topic, it.inAppState, it.emailState, it.smsState)
                    } ?: NotificationTopicPreferences(
                        topic,
                        NotificationPreferenceState.DEFAULT,
                        NotificationPreferenceState.DEFAULT,
                        NotificationPreferenceState.DEFAULT,
                    )
                },
        )
    }

    @Transactional
    fun updateTopic(
        userId: UUID,
        topic: NotificationTopic,
        inAppState: NotificationPreferenceState,
        emailState: NotificationPreferenceState,
        smsState: NotificationPreferenceState,
    ): NotificationPreferences {
        repository.upsert(userId, topic, inAppState, emailState, smsState)
        auditService.record(
            userId,
            null,
            "settings.notification.updated",
            "USER_NOTIFICATION_PREFERENCE",
            userId,
            objectMapper.writeValueAsString(
                mapOf(
                    "topic" to topic.name,
                    "inApp" to inAppState.name,
                    "email" to emailState.name,
                    "sms" to smsState.name,
                ),
            ),
        )
        return get(userId)
    }

    @Transactional
    fun setSmsConsent(
        userId: UUID,
        consented: Boolean,
    ): NotificationPreferences {
        val current = repository.currentSmsConsent(userId)
        if (current != consented) {
            repository.insertSmsConsentEvent(userId, if (consented) SmsConsentState.GRANTED else SmsConsentState.REVOKED)
            auditService.record(
                userId,
                null,
                if (consented) "settings.sms_consent.granted" else "settings.sms_consent.revoked",
                "USER_SMS_CONSENT",
                userId,
            )
        }
        return get(userId)
    }
}
