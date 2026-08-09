package com.rally26.settings.domain

import java.time.Instant
import java.util.UUID

enum class NotificationTopic {
    EVENTS_SCHEDULE,
    RSVP,
    FEES_PAYMENTS,
    DOCUMENTS_ELIGIBILITY,
    ANNOUNCEMENTS,
    FUNDRAISING,
    SWAG_SHOP_ORDERS,
    MESSAGES,
    SUPPORT,
}

enum class NotificationChannel {
    IN_APP,
    EMAIL,
    SMS,
}

enum class NotificationPreferenceState {
    DEFAULT,
    ENABLED,
    DISABLED,
}

enum class SmsConsentState {
    GRANTED,
    REVOKED,
}

data class UserNotificationPreference(
    val userId: UUID,
    val topic: NotificationTopic,
    val inAppState: NotificationPreferenceState,
    val emailState: NotificationPreferenceState,
    val smsState: NotificationPreferenceState,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class NotificationContactContext(
    val email: String?,
    val phone: String?,
    val legacyEmailAllowed: Boolean,
)

data class NotificationDeliveryDecision(
    val inApp: Boolean,
    val email: String?,
    val sms: String?,
)

object NotificationPreferencePolicy {
    fun enabled(
        state: NotificationPreferenceState,
        channel: NotificationChannel,
        legacyEmailAllowed: Boolean = true,
        smsConsented: Boolean = false,
    ): Boolean =
        when (channel) {
            NotificationChannel.IN_APP -> state != NotificationPreferenceState.DISABLED
            NotificationChannel.EMAIL ->
                when (state) {
                    NotificationPreferenceState.DEFAULT -> legacyEmailAllowed
                    NotificationPreferenceState.ENABLED -> true
                    NotificationPreferenceState.DISABLED -> false
                }
            NotificationChannel.SMS ->
                smsConsented && state == NotificationPreferenceState.ENABLED
        }
}
