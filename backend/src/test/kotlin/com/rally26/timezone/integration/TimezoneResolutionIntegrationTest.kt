package com.rally26.timezone.integration

import com.rally26.event.application.EventService
import com.rally26.event.domain.EventType
import com.rally26.event.domain.EventVisibility
import com.rally26.identity.application.PasswordAuthenticationService
import com.rally26.organization.application.OrganizationService
import com.rally26.organization.domain.OrganizationType
import com.rally26.team.application.TeamService
import com.rally26.testsupport.AbstractIntegrationTest
import com.rally26.tournament.application.TournamentService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals

/**
 * Phase 24 slice 24.5 (ADR-071): proves the real, end-to-end timezone resolution order
 * against a real Postgres instance — team override -> tournament override ->
 * organization default -> hard fallback — the way a real create-event form would use
 * it: resolve the default via [EventService.resolveDefaultTimezone], then snapshot
 * whatever it returns onto the created event.
 *
 * Requires Docker (Testcontainers). Not executed in the sandbox this scaffold was
 * generated in — run `./gradlew test` locally with Docker running.
 */
class TimezoneResolutionIntegrationTest : AbstractIntegrationTest() {
    @Autowired
    lateinit var organizationService: OrganizationService

    @Autowired
    lateinit var teamService: TeamService

    @Autowired
    lateinit var tournamentService: TournamentService

    @Autowired
    lateinit var eventService: EventService

    @Autowired
    lateinit var passwordAuthenticationService: PasswordAuthenticationService

    @Test
    fun `resolves team override, tournament override, and organization default in the documented priority order`() {
        val ownerAppUser = passwordAuthenticationService.register("owner-${System.nanoTime()}@example.com", "password1234", "Owner")
        val owner = passwordAuthenticationService.toCurrentUser(ownerAppUser)

        val organization =
            organizationService.create(
                "Riverside Youth Sports",
                "riverside-${System.nanoTime()}",
                OrganizationType.RECREATIONAL_LEAGUE,
                owner,
            )

        // Confirm the organization's address and timezone through the real update flow —
        // a US/TX address, whose static heuristic suggestion is America/Chicago.
        organizationService.update(
            organization.id,
            null,
            null,
            null,
            null,
            null,
            owner,
            addressCountry = "US",
            addressState = "TX",
        )
        val suggested = organizationService.suggestTimezone(organization.id, owner)
        assertEquals("America/Chicago", suggested)
        organizationService.update(organization.id, null, null, null, null, null, owner, timezone = suggested)

        // A team with no override: falls all the way through to the organization default.
        val teamWithoutOverride =
            teamService.create(organization.id, "Team Without Override", "Soccer", null, null, null, null, null, owner)

        // A team with an explicit override: wins over the organization default.
        val teamWithOverride =
            teamService.create(organization.id, "Team With Override", "Soccer", null, null, null, null, null, owner)
        teamService.updateTimezoneOverride(organization.id, teamWithOverride.id, "America/Los_Angeles", owner)

        // A tournament with an explicit override: wins over the organization default when no team is set.
        val tournament = tournamentService.create(organization.id, "State Cup", "Soccer", null, null, null, null, owner)
        tournamentService.updateTimezoneOverride(organization.id, tournament.id, "America/Denver", owner)

        val teamWithoutOverrideDefault = eventService.resolveDefaultTimezone(organization.id, teamWithoutOverride.id, null, owner)
        val teamWithOverrideDefault = eventService.resolveDefaultTimezone(organization.id, teamWithOverride.id, null, owner)
        val tournamentDefault = eventService.resolveDefaultTimezone(organization.id, null, tournament.id, owner)
        val orgWideDefault = eventService.resolveDefaultTimezone(organization.id, null, null, owner)

        assertEquals("America/Chicago", teamWithoutOverrideDefault)
        assertEquals("America/Los_Angeles", teamWithOverrideDefault)
        assertEquals("America/Denver", tournamentDefault)
        assertEquals("America/Chicago", orgWideDefault)

        // Each resolved default is then snapshotted onto a real created event, proving the
        // full round trip — not just the resolution service in isolation.
        val eventUnderTeamWithoutOverride =
            eventService.create(
                organization.id,
                teamWithoutOverride.id,
                null,
                null,
                null,
                EventType.PRACTICE,
                null,
                null,
                null,
                null,
                null,
                null,
                teamWithoutOverrideDefault,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                EventVisibility.TEAM,
                owner,
            )
        val eventUnderTeamWithOverride =
            eventService.create(
                organization.id,
                teamWithOverride.id,
                null,
                null,
                null,
                EventType.PRACTICE,
                null,
                null,
                null,
                null,
                null,
                null,
                teamWithOverrideDefault,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                EventVisibility.TEAM,
                owner,
            )
        val eventUnderTournament =
            eventService.create(
                organization.id,
                null,
                tournament.id,
                null,
                null,
                EventType.TOURNAMENT,
                null,
                null,
                null,
                null,
                null,
                null,
                tournamentDefault,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                EventVisibility.ORGANIZATION,
                owner,
            )

        assertEquals("America/Chicago", eventUnderTeamWithoutOverride.timezone)
        assertEquals("America/Los_Angeles", eventUnderTeamWithOverride.timezone)
        assertEquals("America/Denver", eventUnderTournament.timezone)
    }
}
