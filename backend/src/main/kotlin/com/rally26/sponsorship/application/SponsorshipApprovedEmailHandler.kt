package com.rally26.sponsorship.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.rally26.notification.EmailMessage
import com.rally26.notification.EmailProvider
import com.rally26.outbox.application.OutboxEventHandler
import com.rally26.outbox.domain.OutboxEvent
import org.springframework.stereotype.Component

/** Sends the sponsorship-approved email (Phase 8 slice 2) — a sponsor with no contact email on file is a silent no-op, same posture as the renewal reminder. */
@Component
class SponsorshipApprovedEmailHandler(
	private val emailProvider: EmailProvider,
	private val objectMapper: ObjectMapper,
) : OutboxEventHandler {

	override val eventType: String = "sponsorship.approved"

	override fun handle(event: OutboxEvent) {
		val payload = objectMapper.readValue(event.payload, SponsorshipApprovedPayload::class.java)
		val contactEmail = payload.sponsorContactEmail ?: return
		emailProvider.send(
			EmailMessage(
				to = contactEmail,
				subject = "Your sponsorship is now live",
				body = "Hi ${payload.sponsorName},\n\n" +
					"Your sponsorship of \"${payload.packageName}\" has been approved and is now visible on the organization's public sponsor directory. " +
					"Thank you for your support.\n\n— Rally26",
			),
		)
	}
}
