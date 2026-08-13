package com.rally26.offlinefinance.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.rally26.config.FrontendProperties
import com.rally26.config.ResendTemplateProperties
import com.rally26.notification.EmailMessage
import com.rally26.notification.EmailProvider
import com.rally26.notification.EmailTemplateRef
import com.rally26.outbox.application.OutboxEventHandler
import com.rally26.outbox.domain.OutboxEvent
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Currency
import java.util.Locale

@Component
class OfflineFinancialAcknowledgementEmailHandler(
    private val emailProvider: EmailProvider,
    private val resendTemplateProperties: ResendTemplateProperties,
    private val frontendProperties: FrontendProperties,
    private val objectMapper: ObjectMapper,
) : OutboxEventHandler {
    override val eventType: String = "offline.financial.verified"

    override fun handle(event: OutboxEvent) {
        val payload = objectMapper.readValue(event.payload, OfflineFinancialAcknowledgementPayload::class.java)
        val formatter =
            NumberFormat.getCurrencyInstance(Locale.US).apply {
                currency = Currency.getInstance(payload.currency)
            }
        val amount = formatter.format(BigDecimal.valueOf(payload.amountMinor, 2))
        val received =
            DateTimeFormatter
                .ofPattern("MMM d, uuuu", Locale.US)
                .withZone(ZoneOffset.UTC)
                .format(Instant.parse(payload.receivedAt))
        val referenceLine = payload.paymentReference?.let { "\nReference: $it" } ?: ""
        val orderUrl = frontendProperties.baseUrl
        emailProvider.send(
            EmailMessage(
                to = payload.recipientEmail,
                subject = "Offline payment recorded by Rally26",
                body =
                    "Hi ${payload.payerName ?: "there"},\n\n" +
                        "An organization recorded your $amount ${payload.recordType.lowercase().replace('_', ' ')} " +
                        "for ${payload.displayLabel} as received on $received via " +
                        payload.paymentMethod.lowercase().replace('_', ' ') + "." + referenceLine + "\n\n" +
                        "This message confirms the record in Rally26. The payment was received outside Rally26 and was not processed by Rally26.\n\n— Rally26",
                template =
                    resendTemplateProperties.receiptId.takeIf { it.isNotBlank() }?.let { templateId ->
                        EmailTemplateRef(
                            id = templateId,
                            variables =
                                mapOf(
                                    "RECEIPT_TITLE" to "Offline payment recorded",
                                    "RECEIPT_DATA" to
                                        "For: ${payload.displayLabel}\nAmount: $amount\nReceived: $received via ${payload.paymentMethod.lowercase().replace('_', ' ')}$referenceLine\nRecorded: ${payload.verifiedAt}",
                                    "ORDER_URL" to orderUrl,
                                ),
                        )
                    },
            ),
        )
    }
}
