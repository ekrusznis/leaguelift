package com.rally26.foundingorg.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.rally26.config.FrontendProperties
import com.rally26.config.ResendTemplateProperties
import com.rally26.notification.EmailMessage
import com.rally26.notification.EmailProvider
import com.rally26.notification.EmailTemplateRef
import com.rally26.outbox.application.OutboxEventHandler
import com.rally26.outbox.domain.OutboxEvent
import org.springframework.stereotype.Component

/** Day 30/60 — a light "how's it going" check-in, not a warning. */
@Component
class FoundingPilotCheckinEmailHandler(
    private val emailProvider: EmailProvider,
    private val resendTemplateProperties: ResendTemplateProperties,
    private val frontendProperties: FrontendProperties,
    private val objectMapper: ObjectMapper,
) : OutboxEventHandler {
    override val eventType: String = "founding_org.checkin_due"

    override fun handle(event: OutboxEvent) {
        val payload = objectMapper.readValue(event.payload, FoundingPilotEmailPayload::class.java)
        val actionUrl = "${frontendProperties.baseUrl}/app/organizations/${event.organizationId}/billing"
        val details =
            "How's the Founding Organization pilot going for ${payload.organizationName}? " +
                "We'd love to hear what's working and what isn't."
        payload.ownerEmails.forEach { email ->
            emailProvider.send(
                EmailMessage(
                    to = email,
                    subject = "How's your Rally26 Founding Organization pilot going?",
                    body = "Hi there,\n\n$details\n\nReply any time — we read every response.\n\n— Rally26",
                    template =
                        resendTemplateProperties.notificationId.takeIf { it.isNotBlank() }?.let { templateId ->
                            EmailTemplateRef(
                                id = templateId,
                                variables =
                                    mapOf(
                                        "NOTIFICATION_TITLE" to "How's your pilot going?",
                                        "NOTIFICATION_DETAILS" to details,
                                        "ACTION_URL" to actionUrl,
                                    ),
                            )
                        },
                ),
            )
        }
    }
}

/** Day 63/70/77/84/89 — a weekly countdown as the 90-day pilot approaches its end. */
@Component
class FoundingPilotExpirationWarningEmailHandler(
    private val emailProvider: EmailProvider,
    private val resendTemplateProperties: ResendTemplateProperties,
    private val frontendProperties: FrontendProperties,
    private val objectMapper: ObjectMapper,
) : OutboxEventHandler {
    override val eventType: String = "founding_org.expiration_warning"

    override fun handle(event: OutboxEvent) {
        val payload = objectMapper.readValue(event.payload, FoundingPilotEmailPayload::class.java)
        val actionUrl = "${frontendProperties.baseUrl}/app/organizations/${event.organizationId}/billing"
        val dayWord = if (payload.daysRemaining == 1L) "day" else "days"
        val details =
            "${payload.organizationName}'s Founding Organization pilot ends in ${payload.daysRemaining} $dayWord " +
                "(${payload.pilotEndDate}). Add billing before then to keep uninterrupted access."
        payload.ownerEmails.forEach { email ->
            emailProvider.send(
                EmailMessage(
                    to = email,
                    subject = "Your Rally26 pilot ends in ${payload.daysRemaining} $dayWord",
                    body = "Hi there,\n\n$details\n\n— Rally26",
                    template =
                        resendTemplateProperties.notificationId.takeIf { it.isNotBlank() }?.let { templateId ->
                            EmailTemplateRef(
                                id = templateId,
                                variables =
                                    mapOf(
                                        "NOTIFICATION_TITLE" to "Your pilot ends in ${payload.daysRemaining} $dayWord",
                                        "NOTIFICATION_DETAILS" to details,
                                        "ACTION_URL" to actionUrl,
                                    ),
                            )
                        },
                ),
            )
        }
    }
}

/** Day 90 — the pilot has actually ended and the organization is now suspended. */
@Component
class FoundingPilotExpiredEmailHandler(
    private val emailProvider: EmailProvider,
    private val resendTemplateProperties: ResendTemplateProperties,
    private val frontendProperties: FrontendProperties,
    private val objectMapper: ObjectMapper,
) : OutboxEventHandler {
    override val eventType: String = "founding_org.pilot_expired"

    override fun handle(event: OutboxEvent) {
        val payload = objectMapper.readValue(event.payload, FoundingPilotEmailPayload::class.java)
        val actionUrl = "${frontendProperties.baseUrl}/app/organizations/${event.organizationId}/billing"
        val details =
            "${payload.organizationName}'s Founding Organization pilot has ended and access is paused. Add billing to restore " +
                "access immediately — nothing has been deleted."
        payload.ownerEmails.forEach { email ->
            emailProvider.send(
                EmailMessage(
                    to = email,
                    subject = "Your Rally26 pilot has ended",
                    body = "Hi there,\n\n$details\n\n— Rally26",
                    template =
                        resendTemplateProperties.notificationId.takeIf { it.isNotBlank() }?.let { templateId ->
                            EmailTemplateRef(
                                id = templateId,
                                variables =
                                    mapOf(
                                        "NOTIFICATION_TITLE" to "Your pilot has ended",
                                        "NOTIFICATION_DETAILS" to details,
                                        "ACTION_URL" to actionUrl,
                                    ),
                            )
                        },
                ),
            )
        }
    }
}
