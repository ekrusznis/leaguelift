package com.rally26.subscription.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.rally26.notification.EmailMessage
import com.rally26.notification.EmailProvider
import com.rally26.outbox.application.OutboxEventHandler
import com.rally26.outbox.domain.OutboxEvent
import org.springframework.stereotype.Component

/**
 * Sends the subscription-canceled email (Phase 37.6, ADR-114) — enqueued by
 * `OrganizationSubscriptionService.handleSubscriptionChanged` only on the transition
 * into CANCELED, not on every webhook redelivery of an already-canceled subscription.
 */
@Component
class SubscriptionCanceledEmailHandler(
    private val emailProvider: EmailProvider,
    private val objectMapper: ObjectMapper,
) : OutboxEventHandler {
    override val eventType: String = "organization_subscription.canceled"

    override fun handle(event: OutboxEvent) {
        val payload = objectMapper.readValue(event.payload, OrganizationBillingLifecyclePayload::class.java)
        payload.ownerEmails.forEach { email ->
            emailProvider.send(
                EmailMessage(
                    to = email,
                    subject = "${payload.organizationName}'s Rally26 subscription has ended",
                    body =
                        "Hi there,\n\n" +
                            "${payload.organizationName}'s Rally26 subscription has been canceled and organization access " +
                            "is now suspended. If this wasn't intentional, or you'd like to resubscribe, sign in to Rally26 " +
                            "to start a new subscription.\n\n— Rally26",
                ),
            )
        }
    }
}
