package com.rally26.outbox.application

import com.rally26.config.OutboxWorkerProperties
import com.rally26.outbox.domain.OutboxEvent
import com.rally26.outbox.domain.OutboxEventStatus
import com.rally26.outbox.persistence.OutboxEventRepository
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertTrue

class OutboxWorkerTest {

	private val outboxEventRepository = mockk<OutboxEventRepository>()
	private val properties = OutboxWorkerProperties(maxAttempts = 3, backoffBaseSeconds = 10, backoffCapSeconds = 3600)

	private fun event(attemptCount: Int, eventType: String = "known.event") = OutboxEvent(
		id = UUID.randomUUID(), aggregateType = "thing", aggregateId = UUID.randomUUID(), organizationId = UUID.randomUUID(),
		eventType = eventType, schemaVersion = 1, payload = "{}", status = OutboxEventStatus.PROCESSING,
		attemptCount = attemptCount, availableAt = Instant.now(), processedAt = null, lastError = null, createdAt = Instant.now(),
	)

	@Test
	fun `does nothing when disabled`() {
		val worker = OutboxWorker(outboxEventRepository, emptyList(), properties.copy(enabled = false))

		worker.pollAndDispatch()

		verify(exactly = 0) { outboxEventRepository.claimBatch(any()) }
	}

	@Test
	fun `dispatches a claimed event to the matching handler and marks it processed on success`() {
		val handler = mockk<OutboxEventHandler>()
		every { handler.eventType } returns "known.event"
		every { handler.handle(any()) } just runs
		val worker = OutboxWorker(outboxEventRepository, listOf(handler), properties)
		val claimed = event(attemptCount = 1)
		every { outboxEventRepository.claimBatch(properties.batchSize) } returns listOf(claimed)
		every { outboxEventRepository.markProcessed(claimed.id) } just runs

		worker.pollAndDispatch()

		verify(exactly = 1) { handler.handle(claimed) }
		verify(exactly = 1) { outboxEventRepository.markProcessed(claimed.id) }
	}

	@Test
	fun `leaves an event untouched when no handler is registered for its event type`() {
		val worker = OutboxWorker(outboxEventRepository, emptyList(), properties)
		val claimed = event(attemptCount = 1, eventType = "unregistered.event")

		worker.dispatchOne(claimed)

		verify(exactly = 0) { outboxEventRepository.markProcessed(any()) }
		verify(exactly = 0) { outboxEventRepository.markFailed(any(), any(), any()) }
		verify(exactly = 0) { outboxEventRepository.markDeadLetter(any(), any()) }
	}

	@Test
	fun `retries with backoff when a handler fails below max attempts`() {
		val handler = mockk<OutboxEventHandler>()
		every { handler.eventType } returns "known.event"
		every { handler.handle(any()) } throws RuntimeException("transient failure")
		val worker = OutboxWorker(outboxEventRepository, listOf(handler), properties)
		val claimed = event(attemptCount = 2) // below maxAttempts = 3
		val availableAtSlot = slot<Instant>()
		every { outboxEventRepository.markFailed(claimed.id, capture(availableAtSlot), "transient failure") } just runs

		worker.dispatchOne(claimed)

		verify(exactly = 1) { outboxEventRepository.markFailed(claimed.id, any(), "transient failure") }
		verify(exactly = 0) { outboxEventRepository.markDeadLetter(any(), any()) }
		assertTrue(availableAtSlot.captured.isAfter(Instant.now()))
	}

	@Test
	fun `dead-letters when a handler fails at max attempts`() {
		val handler = mockk<OutboxEventHandler>()
		every { handler.eventType } returns "known.event"
		every { handler.handle(any()) } throws RuntimeException("permanent-ish failure")
		val worker = OutboxWorker(outboxEventRepository, listOf(handler), properties)
		val claimed = event(attemptCount = 3) // == maxAttempts
		every { outboxEventRepository.markDeadLetter(claimed.id, "permanent-ish failure") } just runs

		worker.dispatchOne(claimed)

		verify(exactly = 1) { outboxEventRepository.markDeadLetter(claimed.id, "permanent-ish failure") }
		verify(exactly = 0) { outboxEventRepository.markFailed(any(), any(), any()) }
	}
}
