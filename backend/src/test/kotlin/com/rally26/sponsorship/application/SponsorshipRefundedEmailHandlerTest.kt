package com.rally26.sponsorship.application

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.rally26.notification.EmailMessage
import com.rally26.notification.EmailProvider
import com.rally26.outbox.domain.OutboxEvent
import com.rally26.outbox.domain.OutboxEventStatus
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

class SponsorshipRefundedEmailHandlerTest {

	private val emailProvider = mockk<EmailProvider>()
	private val objectMapper = jacksonObjectMapper()
	private val handler = SponsorshipRefundedEmailHandler(emailProvider, objectMapper)

	private fun eventWithPayload(payloadJson: String): OutboxEvent {
		val now = Instant.now()
		return OutboxEvent(
			id = UUID.randomUUID(), aggregateType = "sponsorship", aggregateId = UUID.randomUUID(), organizationId = UUID.randomUUID(),
			eventType = "sponsorship.refunded", schemaVersion = 1, payload = payloadJson, status = OutboxEventStatus.PROCESSING,
			attemptCount = 1, availableAt = now, processedAt = null, lastError = null, createdAt = now,
		)
	}

	@Test
	fun `sends an email when the sponsor has a contact email`() {
		val payload = objectMapper.writeValueAsString(SponsorshipRefundedPayload("sponsor@acme.test", "Acme Co", "Gold Sponsor", 50_000L, "USD"))
		val messageSlot = slot<EmailMessage>()
		every { emailProvider.send(capture(messageSlot)) } just runs

		handler.handle(eventWithPayload(payload))

		verify(exactly = 1) { emailProvider.send(any()) }
		assertEquals("sponsor@acme.test", messageSlot.captured.to)
	}

	@Test
	fun `skips sending when the sponsor has no contact email`() {
		val payload = objectMapper.writeValueAsString(SponsorshipRefundedPayload(null, "Acme Co", "Gold Sponsor", 50_000L, "USD"))

		handler.handle(eventWithPayload(payload))

		verify(exactly = 0) { emailProvider.send(any()) }
	}
}
