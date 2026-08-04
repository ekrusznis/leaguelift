package com.rally26.order.application

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

class OrderConfirmationEmailHandlerTest {

	private val emailProvider = mockk<EmailProvider>()
	private val objectMapper = jacksonObjectMapper()
	private val handler = OrderConfirmationEmailHandler(emailProvider, objectMapper)

	@Test
	fun `sends a confirmation email built from the payload`() {
		val now = Instant.now()
		val payload = objectMapper.writeValueAsString(OrderConfirmedPayload("supporter@example.test", "Jane Doe", 5_000L, "USD"))
		val event = OutboxEvent(
			id = UUID.randomUUID(), aggregateType = "order", aggregateId = UUID.randomUUID(), organizationId = UUID.randomUUID(),
			eventType = "order.confirmed", schemaVersion = 1, payload = payload, status = OutboxEventStatus.PROCESSING,
			attemptCount = 1, availableAt = now, processedAt = null, lastError = null, createdAt = now,
		)
		val messageSlot = slot<EmailMessage>()
		every { emailProvider.send(capture(messageSlot)) } just runs

		handler.handle(event)

		verify(exactly = 1) { emailProvider.send(any()) }
		assertEquals("supporter@example.test", messageSlot.captured.to)
	}
}
