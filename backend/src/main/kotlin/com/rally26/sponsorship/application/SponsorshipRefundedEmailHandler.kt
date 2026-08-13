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
import java.text.NumberFormat
import java.util.Locale

/** Sends the sponsorship-refunded email (Phase 8 slice 2) — covers both a reject-triggered refund and a general org-admin refund; a sponsor with no contact email on file is a silent no-op. */
@Component
class SponsorshipRefundedEmailHandler(
    private val emailProvider: EmailProvider,
    private val resendTemplateProperties: ResendTemplateProperties,
    private val frontendProperties: FrontendProperties,
    private val objectMapper: ObjectMapper,
) : OutboxEventHandler {
    override val eventType: String = "sponsorship.refunded"

    override fun handle(event: OutboxEvent) {
        val payload = objectMapper.readValue(event.payload, SponsorshipRefundedPayload::class.java)
        val contactEmail = payload.sponsorContactEmail ?: return
        val amount = NumberFormat.getCurrencyInstance(Locale.US).format(payload.amountMinor / 100.0)
        val orderUrl = payload.organizationSlug?.let { "${frontendProperties.baseUrl}/sponsors/$it" } ?: frontendProperties.baseUrl
        emailProvider.send(
            EmailMessage(
                to = contactEmail,
                subject = "Your sponsorship has been refunded",
                body =
                    "Hi ${payload.sponsorName},\n\n" +
                        "Your sponsorship of \"${payload.packageName}\" ($amount) has been refunded. " +
                        "Reach out to the organization if you have any questions.\n\n— Rally26",
                template =
                    resendTemplateProperties.receiptId.takeIf { it.isNotBlank() }?.let { templateId ->
                        EmailTemplateRef(
                            id = templateId,
                            variables =
                                mapOf(
                                    "RECEIPT_TITLE" to "Sponsorship refunded",
                                    "RECEIPT_DATA" to "Package: ${payload.packageName}\nAmount: $amount\nDate: ${payload.refundedAt}\nSponsor: ${payload.sponsorName}",
                                    "ORDER_URL" to orderUrl,
                                ),
                        )
                    },
            ),
        )
    }
}
