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
 * Sends the downgrade-scheduled confirmation email (Phase 46, DESIGN-DOC.md §14.1U) —
 * enqueued by `OrganizationPlanChangeService.applyPlanChange` the moment an owner
 * schedules a downgrade to Free; the actual switch happens later, at which point
 * [SubscriptionDowngradeCompletedEmailHandler] sends a second, separate email.
 */
@Component
class SubscriptionDowngradeScheduledEmailHandler(
    private val emailProvider: EmailProvider,
    private val resendTemplateProperties: ResendTemplateProperties,
    private val frontendProperties: FrontendProperties,
    private val objectMapper: ObjectMapper,
) : OutboxEventHandler {
    override val eventType: String = "organization_subscription.downgrade_scheduled"

    override fun handle(event: OutboxEvent) {
        val payload = objectMapper.readValue(event.payload, OrganizationBillingLifecyclePayload::class.java)
        val actionUrl =
            event.organizationId?.let { "${frontendProperties.baseUrl}/app/organizations/$it/billing" } ?: frontendProperties.baseUrl
        payload.ownerEmails.forEach { email ->
            emailProvider.send(
                EmailMessage(
                    to = email,
                    subject = "${payload.organizationName}'s Rally26 plan is switching to Free",
                    body =
                        "Hi there,\n\n" +
                            "${payload.organizationName}'s Rally26 subscription is scheduled to switch to the Free plan at " +
                            "the end of the current billing period. You'll keep your current plan's access until then, and " +
                            "you can change your mind any time before it takes effect from Billing.\n\n— Rally26",
                    template =
                        resendTemplateProperties.notificationId.takeIf { it.isNotBlank() }?.let { templateId ->
                            EmailTemplateRef(
                                id = templateId,
                                variables =
                                    mapOf(
                                        "NOTIFICATION_TITLE" to "Switching to Free",
                                        "NOTIFICATION_DETAILS" to
                                            "${payload.organizationName}'s Rally26 subscription is scheduled to switch to the Free plan at the end of the current billing period.",
                                        "ACTION_URL" to actionUrl,
                                    ),
                            )
                        },
                ),
            )
        }
    }
}
