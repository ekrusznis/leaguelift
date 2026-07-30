package com.leaguelift.fundraising.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.leaguelift.notification.EmailMessage
import com.leaguelift.notification.EmailProvider
import com.leaguelift.outbox.application.OutboxEventHandler
import com.leaguelift.outbox.domain.OutboxEvent
import org.springframework.stereotype.Component
import java.text.NumberFormat
import java.util.Locale

/** Sends the contribution thank-you email (Phase 8 slice 2) — `contribution.confirmed` is only written by `ContributionService.confirmFromWebhook` when the contribution has a supporter email on file. */
@Component
class ContributionThankYouEmailHandler(
	private val emailProvider: EmailProvider,
	private val objectMapper: ObjectMapper,
) : OutboxEventHandler {

	override val eventType: String = "contribution.confirmed"

	override fun handle(event: OutboxEvent) {
		val payload = objectMapper.readValue(event.payload, ContributionConfirmedPayload::class.java)
		val amount = NumberFormat.getCurrencyInstance(Locale.US).format(payload.amountMinor / 100.0)
		emailProvider.send(
			EmailMessage(
				to = payload.supporterEmail,
				subject = "Thank you for your contribution",
				body = "Hi ${payload.supporterName ?: "there"},\n\n" +
					"Thank you for your contribution of $amount to ${payload.campaignName}. " +
					"Your support makes a real difference.\n\n— LeagueLift",
			),
		)
	}
}
