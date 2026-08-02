package com.leaguelift.offlinefinance.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.leaguelift.notification.EmailMessage
import com.leaguelift.notification.EmailProvider
import com.leaguelift.outbox.application.OutboxEventHandler
import com.leaguelift.outbox.domain.OutboxEvent
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
	private val objectMapper: ObjectMapper,
) : OutboxEventHandler {
	override val eventType: String = "offline.financial.verified"

	override fun handle(event: OutboxEvent) {
		val payload = objectMapper.readValue(event.payload, OfflineFinancialAcknowledgementPayload::class.java)
		val formatter = NumberFormat.getCurrencyInstance(Locale.US).apply {
			currency = Currency.getInstance(payload.currency)
		}
		val amount = formatter.format(BigDecimal.valueOf(payload.amountMinor, 2))
		val received = DateTimeFormatter.ofPattern("MMM d, uuuu", Locale.US)
			.withZone(ZoneOffset.UTC)
			.format(Instant.parse(payload.receivedAt))
		val referenceLine = payload.paymentReference?.let { "\nReference: $it" } ?: ""
		emailProvider.send(
			EmailMessage(
				to = payload.recipientEmail,
				subject = "Offline payment recorded by LeagueLift",
				body = "Hi ${payload.payerName ?: "there"},\n\n" +
					"An organization recorded your $amount ${payload.recordType.lowercase().replace('_', ' ')} " +
					"for ${payload.displayLabel} as received on $received via " +
					payload.paymentMethod.lowercase().replace('_', ' ') + "." + referenceLine + "\n\n" +
					"This message confirms the record in LeagueLift. The payment was received outside LeagueLift and was not processed by LeagueLift.\n\n— LeagueLift",
			),
		)
	}
}
