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
 * Sends the billing-payment-failed email (Phase 37.6, ADR-114) — the first of the
 * organization-subscription lifecycle emails; previously a payment failure only wrote an
 * audit-event row (`OrganizationSubscriptionService.handleInvoicePaymentFailed`), so an
 * owner learned of billing trouble only by noticing suspended access, not by being told.
 */
@Component
class SubscriptionPaymentFailedEmailHandler(
    private val emailProvider: EmailProvider,
    private val resendTemplateProperties: ResendTemplateProperties,
    private val frontendProperties: FrontendProperties,
    private val objectMapper: ObjectMapper,
) : OutboxEventHandler {
    override val eventType: String = "organization_subscription.payment_failed"

    override fun handle(event: OutboxEvent) {
        val payload = objectMapper.readValue(event.payload, OrganizationBillingLifecyclePayload::class.java)
        val actionUrl = event.organizationId?.let { "${frontendProperties.baseUrl}/app/organizations/$it/billing" } ?: frontendProperties.baseUrl
        payload.ownerEmails.forEach { email ->
            emailProvider.send(
                EmailMessage(
                    to = email,
                    subject = "Payment issue with ${payload.organizationName}'s Rally26 subscription",
                    body =
                        "Hi there,\n\n" +
                            "We couldn't process the latest subscription payment for ${payload.organizationName}. " +
                            "Access continues while you resolve this, but please update your payment method soon " +
                            "from Billing settings in Rally26 to avoid an interruption.\n\n— Rally26",
                    template =
                        resendTemplateProperties.notificationId.takeIf { it.isNotBlank() }?.let { templateId ->
                            EmailTemplateRef(
                                id = templateId,
                                variables =
                                    mapOf(
                                        "NOTIFICATION_TITLE" to "Payment issue with your subscription",
                                        "NOTIFICATION_DETAILS" to "We couldn't process the latest subscription payment for ${payload.organizationName}. Access continues while you resolve this — please update your payment method soon.",
                                        "ACTION_URL" to actionUrl,
                                    ),
                            )
                        },
                ),
            )
        }
    }
}
