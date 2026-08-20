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
 * Sends the plan-changed confirmation email (Phase 46, DESIGN-DOC.md §14.1U) — enqueued
 * by `OrganizationPlanChangeService.applyPlanChange` for an immediate, prorated
 * paid&lt;-&gt;paid plan change (e.g. Starter -&gt; Club). Not used for FREE-tier transitions,
 * which have their own dedicated handlers.
 */
@Component
class SubscriptionPlanChangeAppliedEmailHandler(
    private val emailProvider: EmailProvider,
    private val resendTemplateProperties: ResendTemplateProperties,
    private val frontendProperties: FrontendProperties,
    private val objectMapper: ObjectMapper,
) : OutboxEventHandler {
    override val eventType: String = "organization_subscription.plan_change_applied"

    override fun handle(event: OutboxEvent) {
        val payload = objectMapper.readValue(event.payload, OrganizationBillingLifecyclePayload::class.java)
        val actionUrl =
            event.organizationId?.let { "${frontendProperties.baseUrl}/app/organizations/$it/billing" } ?: frontendProperties.baseUrl
        payload.ownerEmails.forEach { email ->
            emailProvider.send(
                EmailMessage(
                    to = email,
                    subject = "${payload.organizationName}'s Rally26 plan has changed",
                    body =
                        "Hi there,\n\n" +
                            "${payload.organizationName}'s Rally26 subscription plan has changed. Any prorated charge or " +
                            "credit has been applied automatically. Review the new plan and billing details any time from " +
                            "Billing.\n\n— Rally26",
                    template =
                        resendTemplateProperties.notificationId.takeIf { it.isNotBlank() }?.let { templateId ->
                            EmailTemplateRef(
                                id = templateId,
                                variables =
                                    mapOf(
                                        "NOTIFICATION_TITLE" to "Plan changed",
                                        "NOTIFICATION_DETAILS" to
                                            "${payload.organizationName}'s Rally26 subscription plan has changed. Any prorated charge or credit has been applied automatically.",
                                        "ACTION_URL" to actionUrl,
                                    ),
                            )
                        },
                ),
            )
        }
    }
}
