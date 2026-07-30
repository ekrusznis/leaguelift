package com.leaguelift.sponsorship.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.leaguelift.notification.EmailMessage
import com.leaguelift.notification.EmailProvider
import com.leaguelift.outbox.domain.OutboxEvent
import com.leaguelift.outbox.domain.OutboxEventStatus
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class SponsorshipRenewalReminderHandlerTest {

	private val emailProvider = mockk<EmailProvider>()
	private val objectMapper = ObjectMapper()
	private val handler = SponsorshipRenewalReminderHandler(emailProvider, objectMapper)

	private fun eventWithPayload(payloadJson: String): OutboxEvent {
		val now = Instant.now()
		return OutboxEvent(
			id = UUID.randomUUID(), aggregateType = "sponsorship", aggregateId = UUID.randomUUID(), organizationId = UUID.randomUUID(),
			eventType = "sponsorship.renewal_reminder_due", schemaVersion = 1, payload = payloadJson, status = OutboxEventStatus.PROCESSING,
			attemptCount = 1, availableAt = now, processedAt = null, lastError = null, createdAt = now,
		)
	}

	@Test
	fun `sends an email when the candidate has a contact email`() {
		val payload = """{"sponsorshipId":"${UUID.randomUUID()}","sponsorName":"Acme Co","sponsorContactEmail":"sponsor@acme.test","packageName":"Gold Sponsor","placementEndDate":"2026-08-10"}"""
		val messageSlot = slot<EmailMessage>()
		every { emailProvider.send(capture(messageSlot)) } just runs

		handler.handle(eventWithPayload(payload))

		verify(exactly = 1) { emailProvider.send(any()) }
		assertEquals("sponsor@acme.test", messageSlot.captured.to)
		kotlin.test.assertTrue(messageSlot.captured.subject.contains("Gold Sponsor"))
	}

	@Test
	fun `skips sending when the candidate has no contact email`() {
		val payload = """{"sponsorshipId":"${UUID.randomUUID()}","sponsorName":"No Email Co","sponsorContactEmail":null,"packageName":"Silver Sponsor","placementEndDate":"2026-08-01"}"""

		handler.handle(eventWithPayload(payload))

		verify(exactly = 0) { emailProvider.send(any()) }
	}
}
