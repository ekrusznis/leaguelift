package com.leaguelift.support.application

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.leaguelift.config.SupportProperties
import com.leaguelift.notification.EmailMessage
import com.leaguelift.notification.EmailProvider
import com.leaguelift.outbox.domain.OutboxEvent
import com.leaguelift.outbox.domain.OutboxEventStatus
import com.leaguelift.support.domain.SupportCase
import com.leaguelift.support.domain.SupportCaseCategory
import com.leaguelift.support.domain.SupportCasePriority
import com.leaguelift.support.domain.SupportCaseStatus
import com.leaguelift.support.persistence.SupportCaseRepository
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

class SupportCaseCreatedEmailHandlerTest {
	private val repository = mockk<SupportCaseRepository>()
	private val emailProvider = mockk<EmailProvider>()
	private val objectMapper = jacksonObjectMapper()
	private val handler = SupportCaseCreatedEmailHandler(
		repository,
		emailProvider,
		SupportProperties("support@leaguelift.io"),
		objectMapper,
	)

	@Test
	fun `confirmation routes a copy to support with reply-to and provider idempotency`() {
		val supportCase = supportCase()
		val message = slot<EmailMessage>()
		every { repository.findById(supportCase.id) } returns supportCase
		every { emailProvider.send(capture(message)) } just runs

		handler.handle(event(supportCase.id))

		verify(exactly = 1) { emailProvider.send(any()) }
		assertEquals("adult@example.com", message.captured.to)
		assertEquals(listOf("support@leaguelift.io"), message.captured.cc)
		assertEquals("support@leaguelift.io", message.captured.replyTo)
		assertEquals("support-case-${supportCase.id}", message.captured.idempotencyKey)
	}

	@Test
	fun `missing case does not send email`() {
		val caseId = UUID.randomUUID()
		every { repository.findById(caseId) } returns null

		handler.handle(event(caseId))

		verify(exactly = 0) { emailProvider.send(any()) }
	}

	private fun event(caseId: UUID): OutboxEvent {
		val now = Instant.parse("2026-08-01T13:30:00Z")
		return OutboxEvent(
			id = UUID.randomUUID(),
			aggregateType = "SUPPORT_CASE",
			aggregateId = caseId,
			organizationId = null,
			eventType = "support.case.created",
			schemaVersion = 1,
			payload = objectMapper.writeValueAsString(mapOf("caseId" to caseId.toString())),
			status = OutboxEventStatus.PROCESSING,
			attemptCount = 1,
			availableAt = now,
			processedAt = null,
			lastError = null,
			createdAt = now,
		)
	}

	private fun supportCase(): SupportCase {
		val now = Instant.parse("2026-08-01T13:30:00Z")
		return SupportCase(
			id = UUID.randomUUID(),
			idempotencyKey = "case-public-001",
			organizationId = null,
			organizationName = null,
			requesterUserId = null,
			requesterName = "Adult User",
			requesterEmail = "adult@example.com",
			category = SupportCaseCategory.TECHNICAL_PROBLEM,
			priority = SupportCasePriority.NORMAL,
			subject = "Page would not load",
			description = "The organization page remained blank after I signed in.",
			status = SupportCaseStatus.OPEN,
			assignedPlatformUserId = null,
			assignedPlatformUserName = null,
			resolution = null,
			closedAt = null,
			createdAt = now,
			updatedAt = now,
		)
	}
}
