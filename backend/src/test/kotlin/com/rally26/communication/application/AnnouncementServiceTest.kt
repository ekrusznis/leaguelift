package com.rally26.communication.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.rally26.audit.application.AuditService
import com.rally26.authorization.application.AuthorizationService
import com.rally26.common.error.ValidationException
import com.rally26.common.web.CurrentUser
import com.rally26.communication.domain.Announcement
import com.rally26.communication.domain.AnnouncementAudience
import com.rally26.communication.domain.AnnouncementKind
import com.rally26.communication.domain.AnnouncementRecipientCandidate
import com.rally26.communication.domain.AnnouncementRecipientType
import com.rally26.communication.domain.AnnouncementScopeType
import com.rally26.communication.domain.AnnouncementStatus
import com.rally26.communication.domain.DeliveryStatus
import com.rally26.communication.persistence.AnnouncementRepository
import com.rally26.membership.application.MembershipService
import com.rally26.membership.domain.OrganizationMembership
import com.rally26.outbox.application.OutboxWriter
import com.rally26.team.persistence.TeamRepository
import com.rally26.tournament.persistence.TournamentRepository
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

class AnnouncementServiceTest {
    private val repository = mockk<AnnouncementRepository>()
    private val membershipService = mockk<MembershipService>()
    private val authorizationService = mockk<AuthorizationService>()
    private val teamRepository = mockk<TeamRepository>()
    private val tournamentRepository = mockk<TournamentRepository>()
    private val outboxWriter = mockk<OutboxWriter>()
    private val auditService = mockk<AuditService>()
    private val clock = Clock.fixed(Instant.parse("2026-08-01T16:00:00Z"), ZoneOffset.UTC)
    private val service = AnnouncementService(
        repository, membershipService, authorizationService, teamRepository, tournamentRepository,
        outboxWriter, auditService, ObjectMapper(), clock,
    )
    private val organizationId = UUID.randomUUID()
    private val user = CurrentUser(UUID.randomUUID(), "owner@example.com", "Owner")
    private val membership = mockk<OrganizationMembership>()

    private fun announcement(
        id: UUID = UUID.randomUUID(),
        status: AnnouncementStatus = AnnouncementStatus.DRAFT,
        recipientCount: Long = 0,
    ) = Announcement(
        id = id,
        organizationId = organizationId,
        scopeType = AnnouncementScopeType.ORGANIZATION,
        scopeId = organizationId,
        scopeName = "North Jersey Volleyball",
        kind = AnnouncementKind.GENERAL,
        relatedEntityType = null,
        relatedEntityId = null,
        targetHouseholdId = null,
        sourceKey = "GENERAL:test-key-123",
        title = "Practice location update",
        body = "Tonight's practice will use Court 2 instead of Court 1.",
        audience = AnnouncementAudience.ALL,
        status = status,
        emailEnabled = true,
        smsEnabled = false,
        createdByUserId = user.userId,
        publishedByUserId = if (status == AnnouncementStatus.PUBLISHED) user.userId else null,
        publishedAt = if (status == AnnouncementStatus.PUBLISHED) Instant.now(clock) else null,
        archivedAt = null,
        recipientCount = recipientCount,
        emailSentCount = 0,
        emailFailedCount = 0,
        smsSentCount = 0,
        smsFailedCount = 0,
        createdAt = Instant.now(clock),
        updatedAt = Instant.now(clock),
    )

    @Test
    fun `publishing snapshots recipients and writes one outbox event`() {
        val draft = announcement()
        val published = draft.copy(status = AnnouncementStatus.PUBLISHED, publishedByUserId = user.userId, publishedAt = Instant.now(clock), recipientCount = 1)
        val candidate = AnnouncementRecipientCandidate(
            recipientType = AnnouncementRecipientType.STAFF,
            userId = UUID.randomUUID(),
            householdId = null,
            displayName = "Coach Lee",
            email = "coach@example.com",
            phone = null,
        )
        every { repository.findById(draft.id, organizationId) } returnsMany listOf(draft, published)
        every { membershipService.requireManagerRole(organizationId, user) } returns membership
        every { repository.listOrganizationStaff(organizationId) } returns listOf(candidate)
        every { repository.listOrganizationGuardians(organizationId) } returns emptyList()
        every { repository.listOrganizationAthletes(organizationId) } returns emptyList()
        every { repository.insertRecipient(draft.id, organizationId, any(), candidate, true, DeliveryStatus.PENDING, DeliveryStatus.NONE) } just runs
        every { repository.publish(draft.id, organizationId, user.userId, Instant.now(clock)) } returns 1
        every { outboxWriter.write("announcement", draft.id, organizationId, "announcement.published", any()) } just runs
        every { auditService.record(user.userId, organizationId, "announcement.published", "ANNOUNCEMENT", draft.id, any()) } just runs

        val result = service.publish(organizationId, draft.id, user)

        assertEquals(AnnouncementStatus.PUBLISHED, result.status)
        verify(exactly = 1) { repository.insertRecipient(draft.id, organizationId, any(), candidate, true, DeliveryStatus.PENDING, DeliveryStatus.NONE) }
        verify(exactly = 1) { outboxWriter.write("announcement", draft.id, organizationId, "announcement.published", any()) }
    }

    @Test
    fun `publishing refuses an audience with no eligible destination`() {
        val draft = announcement()
        every { repository.findById(draft.id, organizationId) } returns draft
        every { membershipService.requireManagerRole(organizationId, user) } returns membership
        every { repository.listOrganizationStaff(organizationId) } returns emptyList()
        every { repository.listOrganizationGuardians(organizationId) } returns emptyList()
        every { repository.listOrganizationAthletes(organizationId) } returns emptyList()

        assertFailsWith<ValidationException> { service.publish(organizationId, draft.id, user) }

        verify(exactly = 0) { repository.publish(any(), any(), any(), any()) }
        verify(exactly = 0) { outboxWriter.write(any(), any(), any(), any(), any()) }
    }
}
