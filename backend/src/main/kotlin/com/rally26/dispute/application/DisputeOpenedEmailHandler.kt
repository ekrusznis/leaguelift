package com.rally26.dispute.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.rally26.notification.EmailMessage
import com.rally26.notification.EmailProvider
import com.rally26.outbox.application.OutboxEventHandler
import com.rally26.outbox.domain.OutboxEvent
import org.springframework.stereotype.Component
import java.text.NumberFormat
import java.util.Locale

/** Sends the dispute-opened email (DESIGN-DOC.md §14.6 item #4) to every active manager — the org needs to know a charge was disputed even though Rally26 (as merchant of record) handles the Stripe-side response. */
@Component
class DisputeOpenedEmailHandler(
    private val emailProvider: EmailProvider,
    private val objectMapper: ObjectMapper,
) : OutboxEventHandler {
    override val eventType: String = "payment_dispute.opened"

    override fun handle(event: OutboxEvent) {
        val payload = objectMapper.readValue(event.payload, DisputeNotificationPayload::class.java)
        val amount = NumberFormat.getCurrencyInstance(Locale.US).format(payload.amountMinor / 100.0)
        payload.ownerEmails.forEach { email ->
            emailProvider.send(
                EmailMessage(
                    to = email,
                    subject = "A payment to ${payload.organizationName} has been disputed",
                    body =
                        "Hi there,\n\n" +
                            "A cardholder has disputed a $amount payment to ${payload.organizationName} (reason: ${payload.reason}). " +
                            "Rally26 is the merchant of record and will respond to Stripe directly. " +
                            "The disputed amount has already been deducted from your organization's earnings, and this is reflected in your ledger.\n\n" +
                            "You don't need to take any action, but reach out to Rally26 support if you have information relevant to the dispute.\n\n— Rally26",
                ),
            )
        }
    }
}
