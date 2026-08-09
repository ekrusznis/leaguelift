package com.rally26.settings.web

import com.rally26.settings.application.NotificationPreferences
import com.rally26.settings.domain.NotificationPreferenceState
import com.rally26.settings.domain.NotificationTopic

data class NotificationPreferencesResponse(
    val smsConsent: Boolean,
    val topics: List<NotificationTopicPreferenceResponse>,
    val requiredCommunications: List<RequiredCommunicationResponse>,
)

data class NotificationTopicPreferenceResponse(
    val topic: NotificationTopic,
    val inApp: NotificationPreferenceState,
    val email: NotificationPreferenceState,
    val sms: NotificationPreferenceState,
)

data class UpdateNotificationTopicRequest(
    val inApp: NotificationPreferenceState,
    val email: NotificationPreferenceState,
    val sms: NotificationPreferenceState,
)

data class UpdateSmsConsentRequest(
    val consented: Boolean,
)

data class RequiredCommunicationResponse(
    val key: String,
    val label: String,
)

private val REQUIRED_COMMUNICATIONS =
    listOf(
        RequiredCommunicationResponse("ACCOUNT_SECURITY", "Account verification, password reset, and security changes"),
        RequiredCommunicationResponse("INVITATIONS", "Membership and account invitations"),
        RequiredCommunicationResponse("FINANCIAL_RECEIPTS", "Payment, order, refund, and financial receipts"),
        RequiredCommunicationResponse(
            "LEGAL_ELIGIBILITY",
            "Legally required eligibility, waiver, and e-sign receipts when those workflows are active",
        ),
    )

fun NotificationPreferences.toResponse() =
    NotificationPreferencesResponse(
        smsConsent = smsConsent,
        topics = topics.map { NotificationTopicPreferenceResponse(it.topic, it.inAppState, it.emailState, it.smsState) },
        requiredCommunications = REQUIRED_COMMUNICATIONS,
    )
