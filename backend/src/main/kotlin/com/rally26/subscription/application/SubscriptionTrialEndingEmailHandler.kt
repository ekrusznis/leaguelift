package com.rally26.subscription.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.rally26.notification.EmailMessage
import com.rally26.notification.EmailProvider
import com.rally26.outbox.application.OutboxEventHandler
import com.rally26.outbox.domain.OutboxEvent
import org.springframework.stereotype.Component

/**
 * Sends the trial-ending email (Phase 37.6, ADR-114) — enqueued by
 * `OrganizationSubscriptionService.handleTrialWillEnd`, reached via Stripe's own
 * `customer.subscription.trial_will_end` webhook (fires ~3 days before trial end), the
 * one lifecycle event that's genuinely webhook-driven rather than detected from a state
 * transition, since there's no local "was this trialing before" comparison to make.
 */
@Component
class SubscriptionTrialEndingEmailHandler(
    private val emailProvider: EmailProvider,
    private val objectMapper: ObjectMapper,
) : OutboxEventHandler {
    override val eventType: String = "organization_subscription.trial_ending"

    override fun handle(event: OutboxEvent) {
        val payload = objectMapper.readValue(event.payload, OrganizationBillingTrialEndingPayload::class.java)
        payload.ownerEmails.forEach { email ->
            emailProvider.send(
                EmailMessage(
                    to = email,
                    subject = "${payload.organizationName}'s Rally26 trial ends ${payload.trialEndDate}",
                    body =
                        "Hi there,\n\n" +
                            "${payload.organizationName}'s Rally26 trial ends on ${payload.trialEndDate}. Add a payment " +
                            "method from Billing settings before then to keep uninterrupted access.\n\n— Rally26",
                ),
            )
        }
    }
}
