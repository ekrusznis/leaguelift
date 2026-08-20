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
 * Sends the downgrade-to-Free-completed email (Phase 46, DESIGN-DOC.md §14.1U) —
 * enqueued by `OrganizationSubscriptionService.handleSubscriptionChanged` only when a
 * scheduled downgrade (see `OrganizationPlanChangeService`) actually takes effect at
 * the end of the paid billing period the owner already paid for. Mirrors
 * `SubscriptionCanceledEmailHandler`'s shape exactly, but the organization stays fully
 * accessible on Free — this is not a suspension.
 */
@Component
class SubscriptionDowngradeCompletedEmailHandler(
    private val emailProvider: EmailProvider,
    private val resendTemplateProperties: ResendTemplateProperties,
    private val frontendProperties: FrontendProperties,
    private val objectMapper: ObjectMapper,
) : OutboxEventHandler {
    override val eventType: String = "organization_subscription.downgrade_completed"

    override fun handle(event: OutboxEvent) {
        val payload = objectMapper.readValue(event.payload, OrganizationBillingLifecyclePayload::class.java)
        val actionUrl =
            event.organizationId?.let { "${frontendProperties.baseUrl}/app/organizations/$it/billing" } ?: frontendProperties.baseUrl
        payload.ownerEmails.forEach { email ->
            emailProvider.send(
                EmailMessage(
                    to = email,
                    subject = "${payload.organizationName} is now on the Rally26 Free plan",
                    body =
                        "Hi there,\n\n" +
                            "${payload.organizationName}'s Rally26 subscription has switched to the Free plan, as scheduled. " +
                            "Organization access continues without interruption under Free's limits. You can upgrade again " +
                            "at any time from Billing.\n\n— Rally26",
                    template =
                        resendTemplateProperties.notificationId.takeIf { it.isNotBlank() }?.let { templateId ->
                            EmailTemplateRef(
                                id = templateId,
                                variables =
                                    mapOf(
                                        "NOTIFICATION_TITLE" to "Now on the Free plan",
                                        "NOTIFICATION_DETAILS" to
                                            "${payload.organizationName}'s Rally26 subscription has switched to the Free plan, as scheduled. Access continues under Free's limits.",
                                        "ACTION_URL" to actionUrl,
                                    ),
                            )
                        },
                ),
            )
        }
    }
}
