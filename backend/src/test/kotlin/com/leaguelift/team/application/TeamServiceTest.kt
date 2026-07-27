package com.leaguelift.team.application

import com.leaguelift.audit.application.AuditService
import com.leaguelift.common.error.ConflictException
import com.leaguelift.common.error.NotFoundException
import com.leaguelift.common.error.ValidationException
import com.leaguelift.common.web.CurrentUser
import com.leaguelift.membership.application.MembershipService
import com.leaguelift.membership.domain.MembershipRole
import com.leaguelift.membership.domain.MembershipStatus
import com.leaguelift.membership.domain.OrganizationMembership
import com.leaguelift.team.domain.Team
import com.leaguelift.team.domain.TeamStatus
import com.leaguelift.team.persistence.TeamRepository
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.springframework.dao.DuplicateKeyException
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TeamServiceTest {

    private val teamRepository = mockk<TeamRepository>()
    private val membershipService = mockk<MembershipService>()
    private val auditService = mockk<AuditService>()
    private val service = TeamService(teamRepository, membershipService, auditService)

    private val orgId = UUID.randomUUID()
    private val currentUser = CurrentUser(UUID.randomUUID(), "sub-manager", "manager@example.com", "Manager")

    @Test
    fun `list requires active membership`() {
        every { membershipService.requireActiveMembership(orgId, currentUser) } returns managerMembership()
        every { teamRepository.findAll(orgId, 0, 20) } returns emptyList()

        service.list(orgId, currentUser, 0, 20)

        verify(exactly = 1) { membershipService.requireActiveMembership(orgId, currentUser) }
    }

    @Test
    fun `create requires manager role`() {
        every { membershipService.requireManagerRole(orgId, currentUser) } returns managerMembership()
        val team = sampleTeam()
        every { teamRepository.insert(orgId, team.name, team.sport, team.season, team.contactEmail) } returns team
        every { auditService.record(any(), any(), any(), any(), any(), any()) } just runs

        val result = service.create(orgId, team.name, team.sport, team.season, team.contactEmail, currentUser)

        assertEquals(team.id, result.id)
        verify(exactly = 1) { membershipService.requireManagerRole(orgId, currentUser) }
        verify(exactly = 1) { auditService.record(currentUser.userId, orgId, "team.created", "team", team.id, any()) }
    }

    @Test
    fun `create with duplicate name throws ConflictException`() {
        every { membershipService.requireManagerRole(orgId, currentUser) } returns managerMembership()
        every { teamRepository.insert(any(), any(), any(), any(), any()) } throws DuplicateKeyException("unique violation")

        assertFailsWith<ConflictException> {
            service.create(orgId, "Duplicate", "Soccer", null, null, currentUser)
        }
    }

    @Test
    fun `create with invalid contact email throws ValidationException`() {
        every { membershipService.requireManagerRole(orgId, currentUser) } returns managerMembership()

        assertFailsWith<ValidationException> {
            service.create(orgId, "Team A", "Soccer", null, "not-an-email", currentUser)
        }
    }

    @Test
    fun `get returns team for active member`() {
        val team = sampleTeam()
        every { membershipService.requireActiveMembership(orgId, currentUser) } returns managerMembership()
        every { teamRepository.findById(team.id, orgId) } returns team

        val result = service.get(orgId, team.id, currentUser)

        assertEquals(team.id, result.id)
    }

    @Test
    fun `get throws NotFoundException when team does not exist`() {
        every { membershipService.requireActiveMembership(orgId, currentUser) } returns managerMembership()
        every { teamRepository.findById(any(), orgId) } returns null

        assertFailsWith<NotFoundException> {
            service.get(orgId, UUID.randomUUID(), currentUser)
        }
    }

    @Test
    fun `archive sets team status and records audit`() {
        every { membershipService.requireManagerRole(orgId, currentUser) } returns managerMembership()
        val teamId = UUID.randomUUID()
        every { teamRepository.archive(teamId, orgId) } returns 1
        every { auditService.record(any(), any(), any(), any(), any(), any()) } just runs

        service.archive(orgId, teamId, currentUser)

        verify(exactly = 1) { auditService.record(currentUser.userId, orgId, "team.archived", "team", teamId, any()) }
    }

    @Test
    fun `archive throws NotFoundException when team does not exist`() {
        every { membershipService.requireManagerRole(orgId, currentUser) } returns managerMembership()
        every { teamRepository.archive(any(), orgId) } returns 0

        assertFailsWith<NotFoundException> {
            service.archive(orgId, UUID.randomUUID(), currentUser)
        }
    }

    private fun sampleTeam() = Team(
        id = UUID.randomUUID(),
        organizationId = orgId,
        name = "Riverside U12 Blue",
        sport = "Soccer",
        season = "Fall 2026",
        status = TeamStatus.ACTIVE,
        contactEmail = "coach@riverside.org",
        createdAt = Instant.now(),
        updatedAt = Instant.now(),
    )

    private fun managerMembership() = OrganizationMembership(
        id = UUID.randomUUID(),
        organizationId = orgId,
        userId = currentUser.userId,
        role = MembershipRole.ADMINISTRATOR,
        status = MembershipStatus.ACTIVE,
        createdAt = Instant.now(),
        updatedAt = Instant.now(),
    )
}
