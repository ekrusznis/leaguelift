package com.rally26.event.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.rally26.audit.application.AuditService
import com.rally26.authorization.application.AuthorizationService
import com.rally26.authorization.domain.Capabilities
import com.rally26.authorization.domain.GuardianRelationship
import com.rally26.authorization.domain.GuardianRelationshipStatus
import com.rally26.authorization.persistence.GuardianRelationshipRepository
import com.rally26.common.error.ForbiddenException
import com.rally26.common.error.ValidationException
import com.rally26.common.web.CurrentUser
import com.rally26.event.domain.Event
import com.rally26.event.domain.EventRsvp
import com.rally26.event.domain.EventSourceType
import com.rally26.event.domain.EventStatus
import com.rally26.event.domain.EventType
import com.rally26.event.domain.EventVisibility
import com.rally26.event.domain.RsvpResponse
import com.rally26.event.domain.RsvpSource
import com.rally26.event.persistence.EventRepository
import com.rally26.event.persistence.EventRsvpRepository
import com.rally26.identity.domain.AppUser
import com.rally26.identity.domain.AppUserStatus
import com.rally26.identity.persistence.AppUserRepository
import com.rally26.outbox.application.OutboxWriter
import com.rally26.participant.domain.Participant
import com.rally26.participant.domain.ParticipantStatus
import com.rally26.participant.domain.ParticipantTeamAssignment
import com.rally26.participant.persistence.ParticipantRepository
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class EventRsvpServiceTest {
    private val eventRsvpRepository = mockk<EventRsvpRepository>()
    private val eventRepository = mockk<EventRepository>()
    private val participantRepository = mockk<ParticipantRepository>()
    private val guardianRelationshipRepository = mockk<GuardianRelationshipRepository>()
    private val authorizationService = mockk<AuthorizationService>()
    private val auditService = mockk<AuditService>()
    private val eventService = mockk<EventService>()
    private val appUserRepository = mockk<AppUserRepository>()
    private val outboxWriter = mockk<OutboxWriter>()
    private val service =
        EventRsvpService(
            eventRsvpRepository,
            eventRepository,
            participantRepository,
            guardianRelationshipRepository,
            authorizationService,
            auditService,
            ObjectMapper(),
            eventService,
            appUserRepository,
            outboxWriter,
        )

    private val orgId = UUID.randomUUID()
    private val teamId = UUID.randomUUID()
    private val eventId = UUID.randomUUID()
    private val householdId = UUID.randomUUID()
    private val participantId = UUID.randomUUID()
    private val currentUser = CurrentUser(UUID.randomUUID(), "parent@example.com", "Parent")

    private fun event(withTeam: Boolean = true) =
        Event(
            id = eventId,
            organizationId = orgId,
            teamId = if (withTeam) teamId else null,
            tournamentId = null,
            opponentTeamId = null,
            opponentName = null,
            eventType = EventType.PRACTICE,
            title = null,
            description = null,
            status = EventStatus.SCHEDULED,
            startAt = null,
            endAt = null,
            arrivalAt = null,
            meetingAt = null,
            timezone = "America/New_York",
            venueName = null,
            address = null,
            latitude = null,
            longitude = null,
            area = null,
            meetingPoint = null,
            directionsNotes = null,
            visibility = EventVisibility.TEAM,
            sourceType = EventSourceType.MANUAL,
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

    private fun participant() =
        Participant(participantId, householdId, orgId, "Jamie", "Lee", null, null, ParticipantStatus.ACTIVE, Instant.now(), Instant.now())

    private fun onTeamAssignment() =
        ParticipantTeamAssignment(UUID.randomUUID(), participantId, teamId, orgId, "ACTIVE", null, Instant.now(), Instant.now())

    private fun rsvp(
        response: RsvpResponse = RsvpResponse.ATTENDING,
        source: RsvpSource = RsvpSource.GUARDIAN,
    ) = EventRsvp(
        UUID.randomUUID(),
        eventId,
        participantId,
        response,
        null,
        currentUser.userId,
        source,
        Instant.now(),
        Instant.now(),
    )

    @Test
    fun `submit rejects an event with no owning team`() {
        every { eventRepository.findById(eventId, orgId) } returns event(withTeam = false)
        every { participantRepository.findById(participantId, orgId) } returns participant()

        assertFailsWith<ValidationException> {
            service.submit(orgId, eventId, participantId, RsvpResponse.ATTENDING, null, currentUser)
        }
    }

    @Test
    fun `submit rejects a participant who is not on the event's team`() {
        every { eventRepository.findById(eventId, orgId) } returns event()
        every { participantRepository.findById(participantId, orgId) } returns participant()
        every { participantRepository.listTeamAssignments(participantId, orgId) } returns emptyList()

        assertFailsWith<ValidationException> {
            service.submit(orgId, eventId, participantId, RsvpResponse.ATTENDING, null, currentUser)
        }
    }

    @Test
    fun `submit rejects a caller with no self, guardian, or staff relationship to the participant`() {
        every { eventRepository.findById(eventId, orgId) } returns event()
        every { participantRepository.findById(participantId, orgId) } returns participant()
        every { participantRepository.listTeamAssignments(participantId, orgId) } returns listOf(onTeamAssignment())
        every { authorizationService.hasParticipantCapability(currentUser, participantId, Capabilities.EVENT_RSVP_SELF) } returns false
        every { guardianRelationshipRepository.findActiveForHousehold(currentUser.userId, householdId) } returns null
        every { authorizationService.hasTeamCapability(orgId, teamId, currentUser, Capabilities.EVENT_UPDATE) } returns false

        assertFailsWith<ForbiddenException> {
            service.submit(orgId, eventId, participantId, RsvpResponse.ATTENDING, null, currentUser)
        }
    }

    @Test
    fun `submit records source GUARDIAN for a real guardian relationship`() {
        every { eventRepository.findById(eventId, orgId) } returns event()
        every { participantRepository.findById(participantId, orgId) } returns participant()
        every { participantRepository.listTeamAssignments(participantId, orgId) } returns listOf(onTeamAssignment())
        every { authorizationService.hasParticipantCapability(currentUser, participantId, Capabilities.EVENT_RSVP_SELF) } returns false
        every { guardianRelationshipRepository.findActiveForHousehold(currentUser.userId, householdId) } returns
            GuardianRelationship(
                UUID.randomUUID(),
                orgId,
                householdId,
                UUID.randomUUID(),
                currentUser.userId,
                GuardianRelationshipStatus.ACTIVE,
                Instant.now(),
                Instant.now(),
            )
        every { eventRsvpRepository.findByEventAndParticipant(eventId, participantId) } returns null
        every {
            eventRsvpRepository.upsert(eventId, participantId, RsvpResponse.MAYBE, "running late", currentUser.userId, RsvpSource.GUARDIAN)
        } returns
            rsvp(RsvpResponse.MAYBE)
        every { auditService.record(any(), any(), any(), any(), any(), any()) } just runs
        every { eventService.displayTitleFor(any(), orgId) } returns "Varsity Soccer Practice"
        every { authorizationService.listTeamStaffUserIds(orgId, teamId, Capabilities.EVENT_RSVP_READ_TEAM) } returns emptySet()
        every { outboxWriter.write(any(), any(), any(), any(), any()) } just runs

        val result = service.submit(orgId, eventId, participantId, RsvpResponse.MAYBE, "running late", currentUser)

        assertEquals(RsvpSource.GUARDIAN, result.source)
        verify(exactly = 1) {
            eventRsvpRepository.upsert(eventId, participantId, RsvpResponse.MAYBE, "running late", currentUser.userId, RsvpSource.GUARDIAN)
        }
        verify(exactly = 1) { auditService.record(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `submit records source SELF for an athlete self-link`() {
        every { eventRepository.findById(eventId, orgId) } returns event()
        every { participantRepository.findById(participantId, orgId) } returns participant()
        every { participantRepository.listTeamAssignments(participantId, orgId) } returns listOf(onTeamAssignment())
        every { authorizationService.hasParticipantCapability(currentUser, participantId, Capabilities.EVENT_RSVP_SELF) } returns true
        every { eventRsvpRepository.findByEventAndParticipant(eventId, participantId) } returns null
        every {
            eventRsvpRepository.upsert(
                eventId,
                participantId,
                RsvpResponse.ATTENDING,
                null,
                currentUser.userId,
                RsvpSource.SELF,
            )
        } returns
            rsvp(RsvpResponse.ATTENDING, RsvpSource.SELF)
        every { auditService.record(any(), any(), any(), any(), any(), any()) } just runs
        every { eventService.displayTitleFor(any(), orgId) } returns "Varsity Soccer Practice"
        every { authorizationService.listTeamStaffUserIds(orgId, teamId, Capabilities.EVENT_RSVP_READ_TEAM) } returns emptySet()
        every { outboxWriter.write(any(), any(), any(), any(), any()) } just runs

        val result = service.submit(orgId, eventId, participantId, RsvpResponse.ATTENDING, null, currentUser)

        assertEquals(RsvpSource.SELF, result.source)
        verify(exactly = 1) { auditService.record(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `submit notifies team staff by email, excluding the submitting user, and never the submitting family`() {
        val coachUserId = UUID.randomUUID()
        every { eventRepository.findById(eventId, orgId) } returns event()
        every { participantRepository.findById(participantId, orgId) } returns participant()
        every { participantRepository.listTeamAssignments(participantId, orgId) } returns listOf(onTeamAssignment())
        every { authorizationService.hasParticipantCapability(currentUser, participantId, Capabilities.EVENT_RSVP_SELF) } returns true
        every { eventRsvpRepository.findByEventAndParticipant(eventId, participantId) } returns null
        every {
            eventRsvpRepository.upsert(
                eventId,
                participantId,
                RsvpResponse.ATTENDING,
                null,
                currentUser.userId,
                RsvpSource.SELF,
            )
        } returns
            rsvp(RsvpResponse.ATTENDING, RsvpSource.SELF)
        every { auditService.record(any(), any(), any(), any(), any(), any()) } just runs
        every { eventService.displayTitleFor(any(), orgId) } returns "Varsity Soccer Practice"
        every { authorizationService.listTeamStaffUserIds(orgId, teamId, Capabilities.EVENT_RSVP_READ_TEAM) } returns
            setOf(coachUserId, currentUser.userId)
        every { appUserRepository.findById(coachUserId) } returns
            AppUser(coachUserId, "coach@example.com", "Coach", AppUserStatus.ACTIVE, null, Instant.now(), Instant.now())
        every { outboxWriter.write(any(), any(), any(), any(), any()) } just runs

        service.submit(orgId, eventId, participantId, RsvpResponse.ATTENDING, null, currentUser)

        verify(exactly = 1) { outboxWriter.write(any(), any(), any(), eq("event.rsvp_changed"), any()) }
        verify(exactly = 0) { appUserRepository.findById(currentUser.userId) }
    }

    @Test
    fun `getRsvps returns the full response list for staff with event-rsvp read-team access`() {
        every { eventRepository.findById(eventId, orgId) } returns event()
        every { eventRsvpRepository.findByEvent(eventId) } returns listOf(rsvp(RsvpResponse.ATTENDING), rsvp(RsvpResponse.MAYBE))
        every { authorizationService.hasTeamCapability(orgId, teamId, currentUser, Capabilities.EVENT_RSVP_READ_TEAM) } returns true

        val result = service.getRsvps(orgId, eventId, currentUser)

        assertEquals(2, result.responses.size)
        assertEquals(1L, result.summary.attending)
        assertEquals(1L, result.summary.maybe)
    }

    @Test
    fun `getRsvps returns summary only (no individual responses) for a guardian with a participant on the team`() {
        every { eventRepository.findById(eventId, orgId) } returns event()
        every { eventRsvpRepository.findByEvent(eventId) } returns listOf(rsvp(RsvpResponse.ATTENDING), rsvp(RsvpResponse.NOT_ATTENDING))
        every { authorizationService.hasTeamCapability(orgId, teamId, currentUser, Capabilities.EVENT_RSVP_READ_TEAM) } returns false
        every { authorizationService.findAthleteSelfLink(currentUser) } returns null
        every { guardianRelationshipRepository.findActiveForUser(currentUser.userId) } returns
            listOf(
                GuardianRelationship(
                    UUID.randomUUID(),
                    orgId,
                    householdId,
                    UUID.randomUUID(),
                    currentUser.userId,
                    GuardianRelationshipStatus.ACTIVE,
                    Instant.now(),
                    Instant.now(),
                ),
            )
        every { participantRepository.findByHousehold(householdId, orgId) } returns listOf(participant())
        every { participantRepository.listTeamAssignments(participantId, orgId) } returns listOf(onTeamAssignment())

        val result = service.getRsvps(orgId, eventId, currentUser)

        assertTrue(result.responses.isEmpty())
        assertEquals(1L, result.summary.attending)
        assertEquals(1L, result.summary.notAttending)
    }

    @Test
    fun `getRsvps denies a caller with no staff access and no linked participant on the team`() {
        every { eventRepository.findById(eventId, orgId) } returns event()
        every { eventRsvpRepository.findByEvent(eventId) } returns emptyList()
        every { authorizationService.hasTeamCapability(orgId, teamId, currentUser, Capabilities.EVENT_RSVP_READ_TEAM) } returns false
        every { authorizationService.findAthleteSelfLink(currentUser) } returns null
        every { guardianRelationshipRepository.findActiveForUser(currentUser.userId) } returns emptyList()

        assertFailsWith<ForbiddenException> {
            service.getRsvps(orgId, eventId, currentUser)
        }
    }
}
