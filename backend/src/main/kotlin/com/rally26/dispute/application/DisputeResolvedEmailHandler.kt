package com.rally26.dispute.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.rally26.notification.EmailMessage
import com.rally26.notification.EmailProvider
import com.rally26.outbox.application.OutboxEventHandler
import com.rally26.outbox.domain.OutboxEvent
import org.springframework.stereotype.Component
import java.text.NumberFormat
import java.util.Locale

/** Sends the dispute-resolved email (DESIGN-DOC.md §14.6 item #4) — outcome is "won" (funds reinstated) or "lost" (funds stay withdrawn, already reflected in the ledger since the dispute opened). */
@Component
class DisputeResolvedEmailHandler(
    private val emailProvider: EmailProvider,
    private val objectMapper: ObjectMapper,
) : OutboxEventHandler {
    override val eventType: String = "payment_dispute.resolved"

    override fun handle(event: OutboxEvent) {
        val payload = objectMapper.readValue(event.payload, DisputeNotificationPayload::class.java)
        val amount = NumberFormat.getCurrencyInstance(Locale.US).format(payload.amountMinor / 100.0)
        val won = payload.outcome == "won"
        payload.ownerEmails.forEach { email ->
            emailProvider.send(
                EmailMessage(
                    to = email,
                    subject = "Dispute ${if (won) "won" else "resolved"} for ${payload.organizationName}",
                    body =
                        "Hi there,\n\n" +
                            if (won) {
                                "Good news — Rally26 won the $amount dispute for ${payload.organizationName}. " +
                                    "The disputed amount has been reinstated to your organization's earnings.\n\n— Rally26"
                            } else {
                                "The $amount dispute for ${payload.organizationName} was resolved in the cardholder's favor. " +
                                    "The disputed amount remains deducted from your organization's earnings, as reflected since the dispute opened.\n\n— Rally26"
                            },
                ),
            )
        }
    }
}
