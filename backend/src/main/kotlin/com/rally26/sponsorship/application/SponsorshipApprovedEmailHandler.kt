package com.rally26.sponsorship.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.rally26.config.FrontendProperties
import com.rally26.config.ResendTemplateProperties
import com.rally26.notification.EmailMessage
import com.rally26.notification.EmailProvider
import com.rally26.notification.EmailTemplateRef
import com.rally26.outbox.application.OutboxEventHandler
import com.rally26.outbox.domain.OutboxEvent
import org.springframework.stereotype.Component

/** Sends the sponsorship-approved email (Phase 8 slice 2) — a sponsor with no contact email on file is a silent no-op, same posture as the renewal reminder. */
@Component
class SponsorshipApprovedEmailHandler(
    private val emailProvider: EmailProvider,
    private val resendTemplateProperties: ResendTemplateProperties,
    private val frontendProperties: FrontendProperties,
    private val objectMapper: ObjectMapper,
) : OutboxEventHandler {
    override val eventType: String = "sponsorship.approved"

    override fun handle(event: OutboxEvent) {
        val payload = objectMapper.readValue(event.payload, SponsorshipApprovedPayload::class.java)
        val contactEmail = payload.sponsorContactEmail ?: return
        val actionUrl = payload.organizationSlug?.let { "${frontendProperties.baseUrl}/sponsors/$it" } ?: frontendProperties.baseUrl
        emailProvider.send(
            EmailMessage(
                to = contactEmail,
                subject = "Your sponsorship is now live",
                body =
                    "Hi ${payload.sponsorName},\n\n" +
                        "Your sponsorship of \"${payload.packageName}\" has been approved and is now visible on the " +
                        "organization's public sponsor directory. " +
                        "Thank you for your support.\n\n— Rally26",
                template =
                    resendTemplateProperties.notificationId.takeIf { it.isNotBlank() }?.let { templateId ->
                        EmailTemplateRef(
                            id = templateId,
                            variables =
                                mapOf(
                                    "NOTIFICATION_TITLE" to "Your sponsorship is now live",
                                    "NOTIFICATION_DETAILS" to
                                        "Your sponsorship of \"${payload.packageName}\" has been approved and is now visible on the organization's public sponsor directory. Thank you for your support.",
                                    "ACTION_URL" to actionUrl,
                                ),
                        )
                    },
            ),
        )
    }
}
