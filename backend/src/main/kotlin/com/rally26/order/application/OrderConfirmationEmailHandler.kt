package com.rally26.order.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.rally26.notification.EmailMessage
import com.rally26.notification.EmailProvider
import com.rally26.outbox.application.OutboxEventHandler
import com.rally26.outbox.domain.OutboxEvent
import org.springframework.stereotype.Component
import java.text.NumberFormat
import java.util.Locale

/** Sends the order-confirmation email (Phase 8 slice 2) — `order.confirmed` is only written by `OrderService.confirmFromWebhook` when the order has a supporter email on file, so there's nothing to skip here. */
@Component
class OrderConfirmationEmailHandler(
	private val emailProvider: EmailProvider,
	private val objectMapper: ObjectMapper,
) : OutboxEventHandler {

	override val eventType: String = "order.confirmed"

	override fun handle(event: OutboxEvent) {
		val payload = objectMapper.readValue(event.payload, OrderConfirmedPayload::class.java)
		val amount = NumberFormat.getCurrencyInstance(Locale.US).format(payload.totalMinor / 100.0)
		emailProvider.send(
			EmailMessage(
				to = payload.supporterEmail,
				subject = "Your order is confirmed",
				body = "Hi ${payload.supporterName ?: "there"},\n\n" +
					"Thanks for your order — your payment of $amount has been confirmed. " +
					"We'll let you know once it ships.\n\n— Rally26",
			),
		)
	}
}
