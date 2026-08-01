package com.leaguelift.support.application

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.leaguelift.audit.application.AuditService
import com.leaguelift.authorization.application.AuthorizationService
import com.leaguelift.common.error.ConflictException
import com.leaguelift.common.web.CurrentUser
import com.leaguelift.membership.application.MembershipService
import com.leaguelift.outbox.persistence.OutboxEventRepository
import com.leaguelift.support.domain.SupportCase
import com.leaguelift.support.domain.SupportCaseCategory
import com.leaguelift.support.domain.SupportCasePriority
import com.leaguelift.support.domain.SupportCaseStatus
import com.leaguelift.support.persistence.SupportCaseRepository
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SupportCaseServiceTest {
	private val repository = mockk<SupportCaseRepository>()
	private val membershipService = mockk<MembershipService>()
	private val authorizationService = mockk<AuthorizationService>()
	private val outboxRepository = mockk<OutboxEventRepository>()
	private val auditService = mockk<AuditService>()
	private val clock = Clock.fixed(Instant.parse("2026-08-01T13:30:00Z"), ZoneOffset.UTC)
	private val service = SupportCaseService(
		repository,
		membershipService,
		authorizationService,
		outboxRepository,
		auditService,
		jacksonObjectMapper(),
		clock,
	)
	private val caseId = UUID.randomUUID()
	private val user = CurrentUser(UUID.randomUUID(), "owner@example.com", "Owner User")

	@Test
	fun `public submission creates the durable case before one confirmation event`() {
		val created = supportCase(requesterEmail = "adult@example.com")
		every { repository.findByIdempotencyKey("case-public-001") } returns null
		every {
			repository.insert(
				"case-public-001", null, null, "Adult User", "adult@example.com",
				SupportCaseCategory.TECHNICAL_PROBLEM, "Page would not load", any(),
			)
		} returns created
		every { outboxRepository.insert(any(), any(), any(), any(), any(), any()) } just runs
		every { auditService.record(any(), any(), any(), any(), any(), any()) } just runs

		val result = service.createPublic(
			" case-public-001 ", " Adult User ", " ADULT@EXAMPLE.COM ",
			SupportCaseCategory.TECHNICAL_PROBLEM,
			" Page would not load ",
			"The organization page remained blank after I signed in.",
		)

		assertEquals(caseId, result.id)
		verify(exactly = 1) { repository.insert(any(), any(), any(), any(), any(), any(), any(), any()) }
		verify(exactly = 1) {
			outboxRepository.insert("SUPPORT_CASE", caseId, null, "support.case.created", any(), 1)
		}
		verify(exactly = 1) { auditService.record(null, null, "support_case.created", "SUPPORT_CASE", caseId, any()) }
	}

	@Test
	fun `retry with the same requester and idempotency key returns the existing case`() {
		val existing = supportCase(requesterEmail = "adult@example.com")
		every { repository.findByIdempotencyKey("case-public-001") } returns existing

		val result = service.createPublic(
			"case-public-001", "Adult User", "adult@example.com",
			SupportCaseCategory.TECHNICAL_PROBLEM,
			"Page would not load",
			"The organization page remained blank after I signed in.",
		)

		assertEquals(existing, result)
		verify(exactly = 0) { repository.insert(any(), any(), any(), any(), any(), any(), any(), any()) }
		verify(exactly = 0) { outboxRepository.insert(any(), any(), any(), any(), any(), any()) }
	}

	@Test
	fun `idempotency key cannot be reused by another requester`() {
		every { repository.findByIdempotencyKey("case-public-001") } returns supportCase(requesterEmail = "first@example.com")

		assertFailsWith<ConflictException> {
			service.createPublic(
				"case-public-001", "Second Adult", "second@example.com",
				SupportCaseCategory.OTHER,
				"A different request",
				"This is a different request from another adult user.",
			)
		}

		verify(exactly = 0) { repository.insert(any(), any(), any(), any(), any(), any(), any(), any()) }
	}

	@Test
	fun `authenticated organization case verifies active membership`() {
		val organizationId = UUID.randomUUID()
		val created = supportCase(requesterUserId = user.userId, requesterEmail = user.email, organizationId = organizationId)
		every { membershipService.requireActiveMembership(organizationId, user) } returns mockk()
		every { repository.findByIdempotencyKey("case-auth-001") } returns null
		every { repository.insert(any(), organizationId, user.userId, any(), any(), any(), any(), any()) } returns created
		every { outboxRepository.insert(any(), any(), any(), any(), any(), any()) } just runs
		every { auditService.record(any(), any(), any(), any(), any(), any()) } just runs

		service.createAuthenticated(
			user, "case-auth-001", organizationId, SupportCaseCategory.ORGANIZATION_SETUP,
			"Need help with setup", "The onboarding import preview needs clarification before execution.",
		)

		verify(exactly = 1) { membershipService.requireActiveMembership(organizationId, user) }
	}

	private fun supportCase(
		requesterUserId: UUID? = null,
		requesterEmail: String,
		organizationId: UUID? = null,
	) = SupportCase(
		id = caseId,
		idempotencyKey = "case-public-001",
		organizationId = organizationId,
		organizationName = if (organizationId == null) null else "North Jersey Volleyball",
		requesterUserId = requesterUserId,
		requesterName = if (requesterUserId == null) "Adult User" else user.displayName,
		requesterEmail = requesterEmail,
		category = SupportCaseCategory.TECHNICAL_PROBLEM,
		priority = SupportCasePriority.NORMAL,
		subject = "Page would not load",
		description = "The organization page remained blank after I signed in.",
		status = SupportCaseStatus.OPEN,
		assignedPlatformUserId = null,
		assignedPlatformUserName = null,
		resolution = null,
		closedAt = null,
		createdAt = Instant.parse("2026-08-01T13:30:00Z"),
		updatedAt = Instant.parse("2026-08-01T13:30:00Z"),
	)
}
