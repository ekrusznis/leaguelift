package com.rally26.seasonrollover.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.rally26.audit.application.AuditService
import com.rally26.authorization.domain.ResourceRole
import com.rally26.common.error.ConflictException
import com.rally26.common.web.CurrentUser
import com.rally26.media.domain.PublicationStatus
import com.rally26.media.domain.Visibility
import com.rally26.membership.application.MembershipService
import com.rally26.membership.domain.OrganizationMembership
import com.rally26.seasonrollover.domain.SeasonRolloverBrandingItem
import com.rally26.seasonrollover.domain.SeasonRolloverCommand
import com.rally26.seasonrollover.domain.SeasonRolloverRosterItem
import com.rally26.seasonrollover.domain.SeasonRolloverRun
import com.rally26.seasonrollover.domain.SeasonRolloverStaffItem
import com.rally26.seasonrollover.persistence.SeasonRolloverRepository
import com.rally26.team.application.TeamService
import com.rally26.team.domain.Team
import com.rally26.team.domain.TeamStatus
import com.rally26.team.persistence.TeamRepository
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SeasonRolloverServiceTest {
    private val repository = mockk<SeasonRolloverRepository>()
    private val teamRepository = mockk<TeamRepository>()
    private val teamService = mockk<TeamService>()
    private val membershipService = mockk<MembershipService>()
    private val auditService = mockk<AuditService>()
    private val service =
        SeasonRolloverService(
            repository,
            teamRepository,
            teamService,
            membershipService,
            auditService,
            ObjectMapper(),
        )
    private val organizationId = UUID.randomUUID()
    private val user = CurrentUser(UUID.randomUUID(), "owner@example.com", "Owner")
    private val membership = mockk<OrganizationMembership>()
    private val source =
        Team(
            UUID.randomUUID(),
            organizationId,
            "U14 Blue",
            "Volleyball",
            "2026-2027",
            TeamStatus.ACTIVE,
            "coach@example.com",
            Instant.parse("2026-07-01T12:00:00Z"),
            Instant.parse("2026-07-31T12:00:00Z"),
        )
    private val destination =
        Team(
            UUID.randomUUID(),
            organizationId,
            "U15 Blue",
            "Volleyball",
            "2027-2028",
            TeamStatus.ACTIVE,
            "coach@example.com",
            Instant.parse("2026-08-01T12:00:00Z"),
            Instant.parse("2026-08-01T12:00:00Z"),
        )
    private val command = SeasonRolloverCommand(source.id, " U15 Blue ", " 2027-2028 ", true, true, true, true)

    @Test
    fun `preview lists only the selected safe setup data and returns a confirmation hash`() {
        allowManager()
        stubSnapshot()

        val preview = service.preview(organizationId, command, user)

        assertEquals("U15 Blue", preview.destinationTeam.name)
        assertEquals(1, preview.roster.size)
        assertEquals(1, preview.staff.size)
        assertEquals(1, preview.branding.size)
        assertEquals(64, preview.confirmationHash.length)
        assertTrue(preview.excludedData.any { it.contains("Financial history") })
        assertTrue(preview.excludedData.any { it.contains("RSVP") })
    }

    @Test
    fun `execute rejects a stale confirmation before creating a destination team`() {
        allowManager()
        stubSnapshot()
        every { repository.findRunByHash(organizationId, "0".repeat(64)) } returns null

        assertFailsWith<ConflictException> {
            service.execute(organizationId, command, "0".repeat(64), user)
        }

        verify(exactly = 0) { teamService.create(any(), any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `execute creates the team copies selected setup archives source and records the run`() {
        allowManager()
        stubSnapshot()
        val preview = service.preview(organizationId, command, user)
        every { repository.findRunByHash(organizationId, preview.confirmationHash) } returns null
        every {
            teamService.create(organizationId, "U15 Blue", "Volleyball", "2027-2028", "coach@example.com", null, null, null, user)
        } returns destination
        every { repository.copyRoster(organizationId, source.id, destination.id) } returns 1
        every { repository.copyStaff(organizationId, source.id, destination.id, user.userId) } returns 1
        every { repository.copyBranding(organizationId, source.id, destination.id) } returns 1
        every { teamService.archive(organizationId, source.id, user) } just runs
        val run =
            SeasonRolloverRun(
                UUID.randomUUID(),
                organizationId,
                source.id,
                destination.id,
                preview.confirmationHash,
                true,
                true,
                true,
                true,
                1,
                1,
                1,
                user.userId,
                Instant.parse("2026-08-01T12:30:00Z"),
            )
        every {
            repository.insertRun(
                organizationId,
                source.id,
                destination.id,
                preview.confirmationHash,
                true,
                true,
                true,
                true,
                1,
                1,
                1,
                user.userId,
            )
        } returns run
        every { auditService.record(any(), any(), any(), any(), any(), any()) } just runs

        val result = service.execute(organizationId, command, preview.confirmationHash, user)

        assertEquals(destination.id, result.destinationTeam.id)
        assertEquals(1, result.rosterCopiedCount)
        assertTrue(result.sourceArchived)
        verify(exactly = 1) { teamService.archive(organizationId, source.id, user) }
        verify(
            exactly = 1,
        ) { auditService.record(user.userId, organizationId, "season_rollover.executed", "season_rollover_run", run.id, any()) }
    }

    @Test
    fun `completed confirmation is idempotent and does not copy again`() {
        allowManager()
        val hash = "a".repeat(64)
        val run =
            SeasonRolloverRun(
                UUID.randomUUID(),
                organizationId,
                source.id,
                destination.id,
                hash,
                true,
                true,
                true,
                true,
                12,
                2,
                2,
                user.userId,
                Instant.parse("2026-08-01T12:30:00Z"),
            )
        every { repository.findRunByHash(organizationId, hash) } returns run
        every { teamRepository.findById(destination.id, organizationId) } returns destination

        val result = service.execute(organizationId, command, hash, user)

        assertEquals(run.id, result.runId)
        assertEquals(12, result.rosterCopiedCount)
        verify(exactly = 0) { repository.copyRoster(any(), any(), any()) }
        verify(exactly = 0) { teamService.create(any(), any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    private fun allowManager() {
        every { membershipService.requireManagerRole(organizationId, user) } returns membership
    }

    private fun stubSnapshot() {
        every { teamRepository.findById(source.id, organizationId) } returns source
        every { repository.teamNameExists(organizationId, "U15 Blue") } returns false
        every { repository.listRoster(organizationId, source.id) } returns
            listOf(
                SeasonRolloverRosterItem(
                    UUID.randomUUID(),
                    "Avery Morgan",
                    LocalDate.of(2026, 8, 1),
                    Instant.parse("2026-07-30T12:00:00Z"),
                    Instant.parse("2026-07-31T12:00:00Z"),
                ),
            )
        every { repository.listStaff(organizationId, source.id) } returns
            listOf(
                SeasonRolloverStaffItem(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    "Taylor Coach",
                    "coach@example.com",
                    ResourceRole.TEAM_MANAGER,
                    Instant.parse("2026-07-31T12:00:00Z"),
                    Instant.parse("2026-07-30T12:00:00Z"),
                ),
            )
        every { repository.listBranding(organizationId, source.id) } returns
            listOf(
                SeasonRolloverBrandingItem(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    "LOGO",
                    "u14-blue-logo.png",
                    PublicationStatus.PUBLISHED,
                    Visibility.PUBLIC,
                    "U14 Blue logo",
                    Instant.parse("2026-07-31T12:00:00Z"),
                    Instant.parse("2026-07-30T12:00:00Z"),
                ),
            )
    }
}
