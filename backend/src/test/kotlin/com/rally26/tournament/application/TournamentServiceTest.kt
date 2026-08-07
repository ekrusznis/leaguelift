package com.rally26.tournament.application

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
import com.rally26.timezone.application.TimeZoneService
import com.rally26.tournament.domain.Tournament
import com.rally26.tournament.domain.TournamentStatus
import com.rally26.tournament.persistence.TournamentRepository
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.springframework.dao.DuplicateKeyException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TournamentServiceTest {
    private val tournamentRepository = mockk<TournamentRepository>()
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
    private val service = TournamentService(tournamentRepository, membershipService, auditService, timeZoneService)

    private val orgId = UUID.randomUUID()
    private val currentUser = CurrentUser(UUID.randomUUID(), "manager@example.com", "Manager")

    @Test
    fun `list requires active membership`() {
        every { membershipService.requireActiveMembership(orgId, currentUser) } returns managerMembership()
        every { tournamentRepository.findAll(orgId, 0, 20) } returns emptyList()

        service.list(orgId, currentUser, 0, 20)

        verify(exactly = 1) { membershipService.requireActiveMembership(orgId, currentUser) }
    }

    @Test
    fun `create requires manager role and records audit`() {
        every { membershipService.requireManagerRole(orgId, currentUser) } returns managerMembership()
        val tournament = sampleTournament()
        every {
            tournamentRepository.insert(
                orgId,
                tournament.name,
                tournament.sport,
                tournament.startDate,
                tournament.endDate,
                tournament.location,
                tournament.contactEmail,
            )
        } returns tournament
        every { auditService.record(any(), any(), any(), any(), any(), any()) } just runs

        val result =
            service.create(
                orgId,
                tournament.name,
                tournament.sport,
                tournament.startDate,
                tournament.endDate,
                tournament.location,
                tournament.contactEmail,
                currentUser,
            )

        assertEquals(tournament.id, result.id)
        verify(exactly = 1) {
            auditService.record(currentUser.userId, orgId, "tournament.created", "tournament", tournament.id, any())
        }
    }

    @Test
    fun `create with end date before start date throws ValidationException`() {
        every { membershipService.requireManagerRole(orgId, currentUser) } returns managerMembership()

        assertFailsWith<ValidationException> {
            service.create(
                orgId,
                "Spring Cup",
                "Soccer",
                startDate = LocalDate.of(2026, 6, 10),
                endDate = LocalDate.of(2026, 6, 5),
                location = null,
                contactEmail = null,
                currentUser = currentUser,
            )
        }
    }

    @Test
    fun `create with invalid contact email throws ValidationException`() {
        every { membershipService.requireManagerRole(orgId, currentUser) } returns managerMembership()

        assertFailsWith<ValidationException> {
            service.create(orgId, "Spring Cup", null, null, null, null, "bad-email", currentUser)
        }
    }

    @Test
    fun `create with duplicate name throws ConflictException`() {
        every { membershipService.requireManagerRole(orgId, currentUser) } returns managerMembership()
        every { tournamentRepository.insert(any(), any(), any(), any(), any(), any(), any()) } throws DuplicateKeyException("unique")

        assertFailsWith<ConflictException> {
            service.create(orgId, "Duplicate Cup", null, null, null, null, null, currentUser)
        }
    }

    @Test
    fun `get throws NotFoundException when tournament does not exist`() {
        every { membershipService.requireActiveMembership(orgId, currentUser) } returns managerMembership()
        every { tournamentRepository.findById(any(), orgId) } returns null

        assertFailsWith<NotFoundException> {
            service.get(orgId, UUID.randomUUID(), currentUser)
        }
    }

    @Test
    fun `archive sets tournament status and records audit`() {
        every { membershipService.requireManagerRole(orgId, currentUser) } returns managerMembership()
        val tournamentId = UUID.randomUUID()
        every { tournamentRepository.archive(tournamentId, orgId) } returns 1
        every { auditService.record(any(), any(), any(), any(), any(), any()) } just runs

        service.archive(orgId, tournamentId, currentUser)

        verify(exactly = 1) {
            auditService.record(currentUser.userId, orgId, "tournament.archived", "tournament", tournamentId, any())
        }
    }

    @Test
    fun `archive throws NotFoundException when tournament does not exist`() {
        every { membershipService.requireManagerRole(orgId, currentUser) } returns managerMembership()
        every { tournamentRepository.archive(any(), orgId) } returns 0

        assertFailsWith<NotFoundException> {
            service.archive(orgId, UUID.randomUUID(), currentUser)
        }
    }

    @Test
    fun `updateTimezoneOverride sets an override and records an audit event`() {
        val tournament = sampleTournament()
        every { membershipService.requireManagerRole(orgId, currentUser) } returns managerMembership()
        every { tournamentRepository.findById(tournament.id, orgId) } returns
            tournament andThen tournament.copy(timezoneOverride = "America/Chicago")
        every { tournamentRepository.updateTimezoneOverride(tournament.id, orgId, "America/Chicago") } returns 1
        every { auditService.record(any(), any(), any(), any(), any(), any()) } just runs

        val result = service.updateTimezoneOverride(orgId, tournament.id, "America/Chicago", currentUser)

        assertEquals("America/Chicago", result.timezoneOverride)
        verify(exactly = 1) {
            auditService.record(currentUser.userId, orgId, "tournament.timezone_override_updated", "tournament", tournament.id, any())
        }
    }

    @Test
    fun `updateTimezoneOverride with null explicitly clears back to inherit organization default`() {
        val tournament = sampleTournament().copy(timezoneOverride = "America/Chicago")
        every { membershipService.requireManagerRole(orgId, currentUser) } returns managerMembership()
        every { tournamentRepository.findById(tournament.id, orgId) } returns tournament andThen tournament.copy(timezoneOverride = null)
        every { tournamentRepository.updateTimezoneOverride(tournament.id, orgId, null) } returns 1
        every { auditService.record(any(), any(), any(), any(), any(), any()) } just runs

        val result = service.updateTimezoneOverride(orgId, tournament.id, null, currentUser)

        assertEquals(null, result.timezoneOverride)
    }

    @Test
    fun `updateTimezoneOverride rejects an invalid timezone`() {
        val tournament = sampleTournament()
        every { membershipService.requireManagerRole(orgId, currentUser) } returns managerMembership()
        every { tournamentRepository.findById(tournament.id, orgId) } returns tournament

        assertFailsWith<ValidationException> {
            service.updateTimezoneOverride(orgId, tournament.id, "Not/AZone", currentUser)
        }
    }

    @Test
    fun `updateTimezoneOverride throws NotFoundException when tournament does not exist`() {
        every { membershipService.requireManagerRole(orgId, currentUser) } returns managerMembership()
        every { tournamentRepository.findById(any(), orgId) } returns null

        assertFailsWith<NotFoundException> {
            service.updateTimezoneOverride(orgId, UUID.randomUUID(), "America/New_York", currentUser)
        }
    }

    private fun sampleTournament() =
        Tournament(
            id = UUID.randomUUID(),
            organizationId = orgId,
            name = "Spring Invitational 2026",
            sport = "Soccer",
            status = TournamentStatus.ACTIVE,
            startDate = LocalDate.of(2026, 5, 1),
            endDate = LocalDate.of(2026, 5, 3),
            location = "Riverside Park",
            contactEmail = "td@riverside.org",
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
