package com.rally26.timezone.application

import com.rally26.common.error.ValidationException
import com.rally26.organization.domain.Organization
import com.rally26.organization.domain.OrganizationStatus
import com.rally26.organization.domain.OrganizationType
import com.rally26.organization.persistence.OrganizationRepository
import com.rally26.team.domain.Sport
import com.rally26.team.domain.Team
import com.rally26.team.domain.TeamStatus
import com.rally26.team.persistence.TeamRepository
import com.rally26.tournament.domain.Tournament
import com.rally26.tournament.domain.TournamentStatus
import com.rally26.tournament.persistence.TournamentRepository
import io.mockk.every
import io.mockk.mockk
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TimeZoneServiceTest {
    private val organizationRepository = mockk<OrganizationRepository>()
    private val teamRepository = mockk<TeamRepository>()
    private val tournamentRepository = mockk<TournamentRepository>()
    private val service = TimeZoneService(organizationRepository, teamRepository, tournamentRepository)

    private val orgId = UUID.randomUUID()
    private val teamId = UUID.randomUUID()
    private val tournamentId = UUID.randomUUID()

    private fun organization(timezone: String?) =
        Organization(
            orgId,
            "Riverside Youth Sports",
            "riverside",
            OrganizationType.RECREATIONAL_LEAGUE,
            OrganizationStatus.ACTIVE,
            emptyList(),
            null,
            null,
            Instant.now(),
            Instant.now(),
            timezone = timezone,
        )

    private fun team(timezoneOverride: String?) =
        Team(teamId, orgId, "Varsity Soccer", Sport.SOCCER, null, TeamStatus.ACTIVE, null, Instant.now(), Instant.now(), timezoneOverride)

    private fun tournament(timezoneOverride: String?) =
        Tournament(
            tournamentId,
            orgId,
            "State Cup",
            "Soccer",
            TournamentStatus.ACTIVE,
            null,
            null,
            null,
            null,
            Instant.now(),
            Instant.now(),
            timezoneOverride,
        )

    @Test
    fun `requireValid accepts a real IANA zone`() {
        assertEquals(ZoneId.of("America/Denver"), service.requireValid("America/Denver"))
    }

    @Test
    fun `requireValid rejects garbage`() {
        assertFailsWith<ValidationException> { service.requireValid("Not/AZone") }
    }

    @Test
    fun `team override wins over tournament, organization, and fallback`() {
        every { teamRepository.findById(teamId, orgId) } returns team("America/Los_Angeles")
        val resolved = service.resolveEffectiveZone(orgId, teamId, null)
        assertEquals("America/Los_Angeles", resolved)
    }

    @Test
    fun `tournament override wins over organization and fallback when there is no team`() {
        every { tournamentRepository.findById(tournamentId, orgId) } returns tournament("America/Chicago")
        val resolved = service.resolveEffectiveZone(orgId, null, tournamentId)
        assertEquals("America/Chicago", resolved)
    }

    @Test
    fun `falls back to organization default when neither team nor tournament override is set`() {
        every { teamRepository.findById(teamId, orgId) } returns team(null)
        every { organizationRepository.findById(orgId) } returns organization("America/New_York")
        val resolved = service.resolveEffectiveZone(orgId, teamId, null)
        assertEquals("America/New_York", resolved)
    }

    @Test
    fun `falls back to the hard-coded fallback zone when nothing is configured`() {
        every { organizationRepository.findById(orgId) } returns organization(null)
        val resolved = service.resolveEffectiveZone(orgId, null, null)
        assertEquals(TimeZoneService.FALLBACK_ZONE, resolved)
    }

    @Test
    fun `resolveInstant rejects a local time that falls in a DST gap`() {
        // 2026 US spring-forward: clocks jump from 2:00 AM to 3:00 AM on March 8.
        val zone = ZoneId.of("America/New_York")
        val gapLocalTime = LocalDateTime.of(2026, 3, 8, 2, 30)
        assertFailsWith<ValidationException> { service.resolveInstant(gapLocalTime, zone) }
    }

    @Test
    fun `resolveInstant resolves a DST overlap to the pre-transition offset`() {
        // 2026 US fall-back: clocks fall from 2:00 AM to 1:00 AM on November 1, so 1:30 AM occurs twice.
        val zone = ZoneId.of("America/New_York")
        val overlapLocalTime = LocalDateTime.of(2026, 11, 1, 1, 30)
        val transition = zone.rules.getTransition(overlapLocalTime)!!
        check(transition.isOverlap)
        val expected = overlapLocalTime.atOffset(transition.offsetBefore).toInstant()

        assertEquals(expected, service.resolveInstant(overlapLocalTime, zone))
    }

    @Test
    fun `resolveInstant converts an unambiguous local time normally`() {
        val zone = ZoneId.of("America/New_York")
        val localTime = LocalDateTime.of(2026, 6, 15, 10, 0)
        assertEquals(localTime.atZone(zone).toInstant(), service.resolveInstant(localTime, zone))
    }
}
