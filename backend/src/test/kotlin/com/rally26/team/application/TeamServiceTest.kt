package com.rally26.team.application

import com.rally26.audit.application.AuditService
import com.rally26.common.error.ConflictException
import com.rally26.common.error.FieldError
import com.rally26.common.error.NotFoundException
import com.rally26.common.error.ValidationException
import com.rally26.common.web.CurrentUser
import com.rally26.membership.application.MembershipService
import com.rally26.membership.domain.MembershipRole
import com.rally26.membership.domain.MembershipStatus
import com.rally26.membership.domain.OrganizationMembership
import com.rally26.team.domain.Team
import com.rally26.team.domain.TeamStatus
import com.rally26.team.persistence.TeamRepository
import com.rally26.timezone.application.TimeZoneService
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.springframework.dao.DuplicateKeyException
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TeamServiceTest {
    private val teamRepository = mockk<TeamRepository>()
    private val membershipService = mockk<MembershipService>()
    private val auditService = mockk<AuditService>()
    private val timeZoneService =
        mockk<TimeZoneService> {
            every { requireValid(any()) } answers {
                val tz = firstArg<String>()
                try {
                    ZoneId.of(tz)
                } catch (e: Exception) {
                    throw ValidationException(
                        "Timezone must be a valid IANA time zone id (e.g. America/New_York).",
                        listOf(FieldError("timezone", "Invalid time zone.")),
                    )
                }
            }
        }
    private val service = TeamService(teamRepository, membershipService, auditService, timeZoneService)

    private val orgId = UUID.randomUUID()
    private val currentUser = CurrentUser(UUID.randomUUID(), "manager@example.com", "Manager")

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

    @Test
    fun `updateTimezoneOverride sets an override and records an audit event`() {
        val team = sampleTeam()
        every { membershipService.requireManagerRole(orgId, currentUser) } returns managerMembership()
        every { teamRepository.findById(team.id, orgId) } returns team andThen team.copy(timezoneOverride = "America/Los_Angeles")
        every { teamRepository.updateTimezoneOverride(team.id, orgId, "America/Los_Angeles") } returns 1
        every { auditService.record(any(), any(), any(), any(), any(), any()) } just runs

        val result = service.updateTimezoneOverride(orgId, team.id, "America/Los_Angeles", currentUser)

        assertEquals("America/Los_Angeles", result.timezoneOverride)
        verify(exactly = 1) {
            auditService.record(currentUser.userId, orgId, "team.timezone_override_updated", "team", team.id, any())
        }
    }

    @Test
    fun `updateTimezoneOverride with null explicitly clears back to inherit organization default`() {
        val team = sampleTeam().copy(timezoneOverride = "America/Los_Angeles")
        every { membershipService.requireManagerRole(orgId, currentUser) } returns managerMembership()
        every { teamRepository.findById(team.id, orgId) } returns team andThen team.copy(timezoneOverride = null)
        every { teamRepository.updateTimezoneOverride(team.id, orgId, null) } returns 1
        every { auditService.record(any(), any(), any(), any(), any(), any()) } just runs

        val result = service.updateTimezoneOverride(orgId, team.id, null, currentUser)

        assertEquals(null, result.timezoneOverride)
    }

    @Test
    fun `updateTimezoneOverride rejects an invalid timezone`() {
        val team = sampleTeam()
        every { membershipService.requireManagerRole(orgId, currentUser) } returns managerMembership()
        every { teamRepository.findById(team.id, orgId) } returns team

        assertFailsWith<ValidationException> {
            service.updateTimezoneOverride(orgId, team.id, "Not/AZone", currentUser)
        }
    }

    @Test
    fun `updateTimezoneOverride throws NotFoundException when team does not exist`() {
        every { membershipService.requireManagerRole(orgId, currentUser) } returns managerMembership()
        every { teamRepository.findById(any(), orgId) } returns null

        assertFailsWith<NotFoundException> {
            service.updateTimezoneOverride(orgId, UUID.randomUUID(), "America/New_York", currentUser)
        }
    }

    private fun sampleTeam() =
        Team(
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

    private fun managerMembership() =
        OrganizationMembership(
            id = UUID.randomUUID(),
            organizationId = orgId,
            userId = currentUser.userId,
            role = MembershipRole.ADMINISTRATOR,
            status = MembershipStatus.ACTIVE,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
        )
}
