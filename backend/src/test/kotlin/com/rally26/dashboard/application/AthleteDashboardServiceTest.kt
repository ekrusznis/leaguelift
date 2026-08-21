package com.rally26.dashboard.application

import com.rally26.authorization.application.AuthorizationService
import com.rally26.authorization.domain.ResourceRole
import com.rally26.authorization.domain.RoleAssignment
import com.rally26.authorization.domain.RoleAssignmentContextType
import com.rally26.authorization.domain.RoleAssignmentStatus
import com.rally26.authorization.persistence.RoleAssignmentRepository
import com.rally26.common.web.CurrentUser
import com.rally26.event.application.EventService
import com.rally26.household.domain.AdultStatus
import com.rally26.household.domain.HouseholdAdult
import com.rally26.household.persistence.HouseholdRepository
import com.rally26.identity.domain.AppUser
import com.rally26.identity.domain.AppUserStatus
import com.rally26.identity.persistence.AppUserRepository
import com.rally26.participant.domain.Participant
import com.rally26.participant.domain.ParticipantStatus
import com.rally26.participant.domain.ParticipantTeamAssignment
import com.rally26.participant.persistence.ParticipantRepository
import com.rally26.team.domain.Sport
import com.rally26.team.domain.Team
import com.rally26.team.domain.TeamStatus
import com.rally26.team.persistence.TeamRepository
import io.mockk.every
import io.mockk.mockk
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * As of Phase 7/ADR-020, real wherever a `role_assignment(PARTICIPANT, ATHLETE_SELF)`
 * self-link exists — see the class doc on [AthleteDashboardService] for what stays
 * honestly empty (schedule, orders) even when linked.
 */
class AthleteDashboardServiceTest {
    private val authorizationService = mockk<AuthorizationService>()
    private val roleAssignmentRepository = mockk<RoleAssignmentRepository>()
    private val participantRepository = mockk<ParticipantRepository>()
    private val householdRepository = mockk<HouseholdRepository>()
    private val teamRepository = mockk<TeamRepository>()
    private val appUserRepository = mockk<AppUserRepository>()
    private val eventService = mockk<EventService>()

    private val service =
        AthleteDashboardService(
            authorizationService,
            roleAssignmentRepository,
            participantRepository,
            householdRepository,
            teamRepository,
            appUserRepository,
            eventService,
        )

    private val currentUser = CurrentUser(UUID.randomUUID(), "maya.johnson@example.com", "Maya Johnson")
    private val orgId = UUID.randomUUID()
    private val householdId = UUID.randomUUID()
    private val participantId = UUID.randomUUID()

    private fun selfLink() =
        RoleAssignment(
            UUID.randomUUID(),
            orgId,
            currentUser.userId,
            RoleAssignmentContextType.PARTICIPANT,
            participantId,
            ResourceRole.ATHLETE_SELF,
            RoleAssignmentStatus.ACTIVE,
            UUID.randomUUID(),
            Instant.now(),
            Instant.now(),
        )

    private fun participant() =
        Participant(
            participantId,
            householdId,
            orgId,
            "Maya",
            "Johnson",
            null,
            null,
            ParticipantStatus.ACTIVE,
            Instant.now(),
            Instant.now(),
        )

    @Test
    fun `getOverview falls back to the caller's own display name with no self-link`() {
        every { authorizationService.findAthleteSelfLink(currentUser) } returns null

        val result = service.getOverview(currentUser)

        assertEquals("Maya Johnson", result.displayName)
        assertEquals(false, result.isDemoData)
        assertEquals(null, result.nextEvent)
    }

    @Test
    fun `getOverview uses the linked participant's real name`() {
        every { authorizationService.findAthleteSelfLink(currentUser) } returns selfLink()
        every { participantRepository.findById(participantId, orgId) } returns participant()
        every { eventService.listForParticipant(orgId, participantId, currentUser, 0, 50) } returns emptyList()

        val result = service.getOverview(currentUser)

        assertEquals("Maya Johnson", result.displayName)
        assertEquals(null, result.nextEvent)
    }

    @Test
    fun `getTeams returns empty with no self-link, never fabricated content`() {
        every { authorizationService.findAthleteSelfLink(currentUser) } returns null

        assertTrue(service.getTeams(currentUser).isEmpty())
    }

    @Test
    fun `getTeams returns the linked participant's real team assignments and coach identity`() {
        val team =
            Team(UUID.randomUUID(), orgId, "Varsity Soccer", Sport.SOCCER, "2024", TeamStatus.ACTIVE, null, Instant.now(), Instant.now())
        val coach =
            AppUser(UUID.randomUUID(), "coach@example.com", "Jordan Ellis", AppUserStatus.ACTIVE, null, Instant.now(), Instant.now())
        val coachGrant =
            RoleAssignment(
                UUID.randomUUID(),
                orgId,
                coach.id,
                RoleAssignmentContextType.TEAM,
                team.id,
                ResourceRole.TEAM_MANAGER,
                RoleAssignmentStatus.ACTIVE,
                null,
                Instant.now(),
                Instant.now(),
            )
        every { authorizationService.findAthleteSelfLink(currentUser) } returns selfLink()
        every { participantRepository.findById(participantId, orgId) } returns participant()
        every { participantRepository.listTeamAssignments(participantId, orgId) } returns
            listOf(
                ParticipantTeamAssignment(UUID.randomUUID(), participantId, team.id, orgId, "ACTIVE", null, Instant.now(), Instant.now()),
            )
        every { teamRepository.findById(team.id, orgId) } returns team
        every { roleAssignmentRepository.listActiveForResource(RoleAssignmentContextType.TEAM, team.id) } returns listOf(coachGrant)
        every { appUserRepository.findById(coach.id) } returns coach

        val result = service.getTeams(currentUser)

        assertEquals(1, result.size)
        assertEquals("Varsity Soccer", result.first().name)
        assertEquals("Jordan Ellis", result.first().coachName)
    }

    @Test
    fun `getGuardians returns real household adults for the linked participant`() {
        every { authorizationService.findAthleteSelfLink(currentUser) } returns selfLink()
        every { participantRepository.findById(participantId, orgId) } returns participant()
        every { householdRepository.listAdults(householdId, orgId) } returns
            listOf(
                HouseholdAdult(
                    UUID.randomUUID(),
                    householdId,
                    orgId,
                    "Sarah",
                    "Johnson",
                    "sarah@example.com",
                    "555-0198",
                    "Parent",
                    true,
                    AdultStatus.ACTIVE,
                    Instant.now(),
                    Instant.now(),
                ),
            )

        val result = service.getGuardians(currentUser)

        assertEquals(1, result.size)
        assertEquals("Sarah Johnson", result.first().name)
    }

    @Test
    fun `getWeekEvents returns empty with no self-link, never fabricated content`() {
        every { authorizationService.findAthleteSelfLink(currentUser) } returns null

        assertTrue(service.getWeekEvents(currentUser).isEmpty())
    }

    @Test
    fun `getWeekEvents returns the linked participant's real upcoming events`() {
        val team =
            Team(UUID.randomUUID(), orgId, "Varsity Soccer", Sport.SOCCER, "2024", TeamStatus.ACTIVE, null, Instant.now(), Instant.now())
        val event =
            com.rally26.event.domain.Event(
                id = UUID.randomUUID(),
                organizationId = orgId,
                teamId = team.id,
                tournamentId = null,
                opponentTeamId = null,
                opponentName = "Northside FC",
                eventType = com.rally26.event.domain.EventType.COMPETITION,
                title = null,
                description = null,
                status = com.rally26.event.domain.EventStatus.SCHEDULED,
                startAt = Instant.now().plusSeconds(3600),
                endAt = null,
                arrivalAt = null,
                meetingAt = null,
                timezone = "America/New_York",
                venueName = "Home Field",
                address = null,
                latitude = null,
                longitude = null,
                area = null,
                meetingPoint = null,
                directionsNotes = null,
                visibility = com.rally26.event.domain.EventVisibility.TEAM,
                sourceType = com.rally26.event.domain.EventSourceType.MANUAL,
                provider = null,
                connectionId = null,
                externalEventId = null,
                externalSyncHash = null,
                sourceUpdatedAt = null,
                createdByUserId = UUID.randomUUID(),
                updatedByUserId = UUID.randomUUID(),
                createdAt = Instant.now(),
                updatedAt = Instant.now(),
            )
        every { authorizationService.findAthleteSelfLink(currentUser) } returns selfLink()
        every { participantRepository.findById(participantId, orgId) } returns participant()
        every { eventService.listForParticipant(orgId, participantId, currentUser, 0, 50) } returns listOf(event)
        every { teamRepository.findById(team.id, orgId) } returns team

        val result = service.getWeekEvents(currentUser)

        assertEquals(1, result.size)
        assertEquals("Varsity Soccer vs Northside FC", result.first().title)
        assertEquals("Home Field", result.first().subtitle)
    }

    @Test
    fun `recent history and orders are honestly empty — no backing data model exists`() {
        assertTrue(service.getRecentHistory(currentUser).isEmpty())
        assertTrue(service.getOrders(currentUser).isEmpty())
    }
}
