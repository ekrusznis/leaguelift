package com.rally26.sponsorship.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.rally26.notification.EmailMessage
import com.rally26.notification.EmailProvider
import com.rally26.outbox.application.OutboxEventHandler
import com.rally26.outbox.domain.OutboxEvent
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

private val log = LoggerFactory.getLogger(SponsorshipRenewalReminderHandler::class.java)

/**
 * Sends the actual renewal-reminder email (Phase 8 slice 1, ADR-022) — the
 * `EmailProvider`-calling half of what used to be one direct-scheduled method
 * (`SponsorshipRenewalReminderService`, ADR-019); [SponsorshipRenewalScanner] does the
 * finding/enqueueing/marking-reminded half. A sponsor with no contact email on file is
 * logged and treated as a no-op, not a failure — there's nothing to send, and the
 * scanner already marked this sponsorship reminded, so retrying would never help.
 */
@Component
class SponsorshipRenewalReminderHandler(
	private val emailProvider: EmailProvider,
	private val objectMapper: ObjectMapper,
) : OutboxEventHandler {

	override val eventType: String = "sponsorship.renewal_reminder_due"

	override fun handle(event: OutboxEvent) {
		val payload = objectMapper.readTree(event.payload)
		val sponsorContactEmail = payload.get("sponsorContactEmail")?.takeIf { !it.isNull }?.asText()
		val sponsorName = payload.get("sponsorName").asText()
		val packageName = payload.get("packageName").asText()
		val placementEndDate = payload.get("placementEndDate").asText()

		if (sponsorContactEmail == null) {
			log.info(
				"Sponsorship {} for package \"{}\" expiring {} has no sponsor contact email on file — nothing to send.",
				event.aggregateId, packageName, placementEndDate,
			)
			return
		}
		emailProvider.send(
			EmailMessage(
				to = sponsorContactEmail,
				subject = "Your sponsorship of $packageName is ending soon",
				body = "Hi $sponsorName,\n\n" +
					"Your sponsorship placement for \"$packageName\" is scheduled to end on $placementEndDate. " +
					"Reach out to the organization if you'd like to renew.\n\n" +
					"— Rally26",
			),
		)
	}
}
