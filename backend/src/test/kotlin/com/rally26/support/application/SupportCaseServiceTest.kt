package com.rally26.support.application

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.rally26.audit.application.AuditService
import com.rally26.authorization.application.AuthorizationService
import com.rally26.common.error.ConflictException
import com.rally26.common.web.CurrentUser
import com.rally26.membership.application.MembershipService
import com.rally26.outbox.persistence.OutboxEventRepository
import com.rally26.support.domain.SupportCase
import com.rally26.support.domain.SupportCaseCategory
import com.rally26.support.domain.SupportCasePriority
import com.rally26.support.domain.SupportCaseStatus
import com.rally26.support.persistence.SupportCaseRepository
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
    private val service =
        SupportCaseService(
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
                "case-public-001",
                null,
                null,
                "Adult User",
                "adult@example.com",
                SupportCaseCategory.TECHNICAL_PROBLEM,
                "Page would not load",
                any(),
            )
        } returns created
        every { outboxRepository.insert(any(), any(), any(), any(), any(), any()) } just runs
        every { auditService.record(any(), any(), any(), any(), any(), any()) } just runs

        val result =
            service.createPublic(
                " case-public-001 ",
                " Adult User ",
                " ADULT@EXAMPLE.COM ",
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

        val result =
            service.createPublic(
                "case-public-001",
                "Adult User",
                "adult@example.com",
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
                "case-public-001",
                "Second Adult",
                "second@example.com",
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
            user,
            "case-auth-001",
            organizationId,
            SupportCaseCategory.ORGANIZATION_SETUP,
            "Need help with setup",
            "The onboarding import preview needs clarification before execution.",
        )

        verify(exactly = 1) { membershipService.requireActiveMembership(organizationId, user) }
    }

    @Test
    fun `updatePlatform enqueues a status-changed notification only on a real status transition`() {
        val existing = supportCase(requesterEmail = "adult@example.com").copy(status = SupportCaseStatus.OPEN)
        val updated = existing.copy(status = SupportCaseStatus.IN_PROGRESS)
        every { authorizationService.requirePlatformCapability(user, any()) } just runs
        every { repository.findById(caseId) } returnsMany listOf(existing, updated)
        every { repository.updatePlatform(caseId, SupportCaseStatus.IN_PROGRESS, SupportCasePriority.NORMAL, null, null, null) } returns 1
        every { outboxRepository.insert(any(), any(), any(), any(), any(), any()) } just runs
        every { auditService.record(any(), any(), any(), any(), any(), any()) } just runs

        val result = service.updatePlatform(user, caseId, SupportCaseStatus.IN_PROGRESS, SupportCasePriority.NORMAL, null, null)

        assertEquals(SupportCaseStatus.IN_PROGRESS, result.status)
        verify(exactly = 1) {
            outboxRepository.insert("SUPPORT_CASE", caseId, existing.organizationId, "support.case.status_changed", any(), 1)
        }
    }

    @Test
    fun `updatePlatform does not enqueue a notification when status is unchanged`() {
        val existing =
            supportCase(
                requesterEmail = "adult@example.com",
            ).copy(status = SupportCaseStatus.OPEN, priority = SupportCasePriority.NORMAL)
        val updated = existing.copy(priority = SupportCasePriority.HIGH)
        every { authorizationService.requirePlatformCapability(user, any()) } just runs
        every { repository.findById(caseId) } returnsMany listOf(existing, updated)
        every { repository.updatePlatform(caseId, SupportCaseStatus.OPEN, SupportCasePriority.HIGH, null, null, null) } returns 1
        every { auditService.record(any(), any(), any(), any(), any(), any()) } just runs

        service.updatePlatform(user, caseId, SupportCaseStatus.OPEN, SupportCasePriority.HIGH, null, null)

        verify(exactly = 0) { outboxRepository.insert(any(), any(), any(), any(), any(), any()) }
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
