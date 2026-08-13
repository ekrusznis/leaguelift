package com.rally26.fee.application

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

/** Sends the online fee-payment receipt email — `fee_assignment.payment_confirmed_online` is only written by `FeeService.confirmOnlineCheckoutFromWebhook` when the paying guardian has an email on file (always true today, since online payment requires being signed in). */
@Component
class FeePaymentReceiptEmailHandler(
    private val emailProvider: EmailProvider,
    private val resendTemplateProperties: ResendTemplateProperties,
    private val frontendProperties: FrontendProperties,
    private val objectMapper: ObjectMapper,
) : OutboxEventHandler {
    override val eventType: String = "fee_assignment.payment_confirmed_online"

    override fun handle(event: OutboxEvent) {
        val payload = objectMapper.readValue(event.payload, FeePaymentConfirmedPayload::class.java)
        val amount = NumberFormat.getCurrencyInstance(Locale.US).format(payload.amountMinor / 100.0)
        val orderUrl = "${frontendProperties.baseUrl}/app/organizations/${payload.organizationId}/households/${payload.householdId}/fees"
        emailProvider.send(
            EmailMessage(
                to = payload.payerEmail,
                subject = "Your fee payment is confirmed",
                body =
                    "Hi ${payload.payerName ?: "there"},\n\n" +
                        "This confirms your payment of $amount for \"${payload.feeDescription}\" has been received.\n\n" +
                        "View it here: $orderUrl\n\n— Rally26",
                template =
                    resendTemplateProperties.receiptId.takeIf { it.isNotBlank() }?.let { templateId ->
                        EmailTemplateRef(
                            id = templateId,
                            variables =
                                mapOf(
                                    "RECEIPT_TITLE" to "Fee payment confirmed",
                                    "RECEIPT_DATA" to
                                        "Paid to: ${payload.feeDescription}\nAmount: $amount\nDate: ${payload.paidAt}\nPayer: ${payload.payerName ?: payload.payerEmail}",
                                    "ORDER_URL" to orderUrl,
                                ),
                        )
                    },
            ),
        )
    }
}
