package com.rally26.subscription.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.rally26.config.FrontendProperties
import com.rally26.config.ResendTemplateProperties
import com.rally26.notification.EmailMessage
import com.rally26.notification.EmailProvider
import com.rally26.notification.EmailTemplateRef
import com.rally26.outbox.application.OutboxEventHandler
import com.rally26.outbox.domain.OutboxEvent
import org.springframework.stereotype.Component

/**
 * Sends the billing-recovered email (Phase 37.6, ADR-114) — only enqueued by
 * `OrganizationSubscriptionService.handleInvoicePaid` when the subscription was actually
 * PAST_DUE beforehand, so a routine monthly renewal charge never triggers this; it fires
 * once, the moment a real payment problem is actually resolved.
 */
@Component
class SubscriptionPaymentRecoveredEmailHandler(
    private val emailProvider: EmailProvider,
    private val resendTemplateProperties: ResendTemplateProperties,
    private val frontendProperties: FrontendProperties,
    private val objectMapper: ObjectMapper,
) : OutboxEventHandler {
    override val eventType: String = "organization_subscription.payment_recovered"

    override fun handle(event: OutboxEvent) {
        val payload = objectMapper.readValue(event.payload, OrganizationBillingLifecyclePayload::class.java)
        val actionUrl = event.organizationId?.let { "${frontendProperties.baseUrl}/app/organizations/$it/billing" } ?: frontendProperties.baseUrl
        payload.ownerEmails.forEach { email ->
            emailProvider.send(
                EmailMessage(
                    to = email,
                    subject = "Payment received — ${payload.organizationName}'s subscription is current",
                    body =
                        "Hi there,\n\n" +
                            "Good news — we successfully processed a payment for ${payload.organizationName}'s Rally26 " +
                            "subscription and your account is now current. Thanks for taking care of it.\n\n— Rally26",
                    template =
                        resendTemplateProperties.notificationId.takeIf { it.isNotBlank() }?.let { templateId ->
                            EmailTemplateRef(
                                id = templateId,
                                variables =
                                    mapOf(
                                        "NOTIFICATION_TITLE" to "Payment received",
                                        "NOTIFICATION_DETAILS" to "${payload.organizationName}'s Rally26 subscription is now current. Thanks for taking care of it.",
                                        "ACTION_URL" to actionUrl,
                                    ),
                            )
                        },
                ),
            )
        }
    }
}
