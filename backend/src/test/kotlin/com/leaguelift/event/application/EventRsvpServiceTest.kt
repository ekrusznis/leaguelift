package com.leaguelift.event.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.leaguelift.audit.application.AuditService
import com.leaguelift.authorization.application.AuthorizationService
import com.leaguelift.authorization.domain.Capabilities
import com.leaguelift.authorization.domain.GuardianRelationship
import com.leaguelift.authorization.domain.GuardianRelationshipStatus
import com.leaguelift.authorization.domain.ResourceRole
import com.leaguelift.authorization.domain.RoleAssignment
import com.leaguelift.authorization.domain.RoleAssignmentContextType
import com.leaguelift.authorization.domain.RoleAssignmentStatus
import com.leaguelift.authorization.persistence.GuardianRelationshipRepository
import com.leaguelift.common.error.ForbiddenException
import com.leaguelift.common.error.ValidationException
import com.leaguelift.common.web.CurrentUser
import com.leaguelift.event.domain.Event
import com.leaguelift.event.domain.EventRsvp
import com.leaguelift.event.domain.EventSourceType
import com.leaguelift.event.domain.EventStatus
import com.leaguelift.event.domain.EventType
import com.leaguelift.event.domain.EventVisibility
import com.leaguelift.event.domain.RsvpResponse
import com.leaguelift.event.domain.RsvpSource
import com.leaguelift.event.persistence.EventRepository
import com.leaguelift.event.persistence.EventRsvpRepository
import com.leaguelift.identity.domain.AppUser
import com.leaguelift.identity.domain.AppUserStatus
import com.leaguelift.identity.persistence.AppUserRepository
import com.leaguelift.outbox.application.OutboxWriter
import com.leaguelift.participant.domain.Participant
import com.leaguelift.participant.domain.ParticipantStatus
import com.leaguelift.participant.domain.ParticipantTeamAssignment
import com.leaguelift.participant.persistence.ParticipantRepository
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
	private val service = EventRsvpService(
		eventRsvpRepository, eventRepository, participantRepository, guardianRelationshipRepository, authorizationService, auditService, ObjectMapper(),
		eventService, appUserRepository, outboxWriter,
	)

	private val orgId = UUID.randomUUID()
	private val teamId = UUID.randomUUID()
	private val eventId = UUID.randomUUID()
	private val householdId = UUID.randomUUID()
	private val participantId = UUID.randomUUID()
	private val currentUser = CurrentUser(UUID.randomUUID(), "parent@example.com", "Parent")

	private fun event(withTeam: Boolean = true) = Event(
		id = eventId, organizationId = orgId, teamId = if (withTeam) teamId else null, tournamentId = null, opponentTeamId = null,
		opponentName = null, eventType = EventType.PRACTICE, title = null, description = null, status = EventStatus.SCHEDULED,
		startAt = null, endAt = null, arrivalAt = null, meetingAt = null, timezone = "America/New_York", venueName = null, address = null,
		latitude = null, longitude = null, area = null, meetingPoint = null, directionsNotes = null, visibility = EventVisibility.TEAM,
		sourceType = EventSourceType.MANUAL, provider = null, connectionId = null, externalEventId = null, externalSyncHash = null,
		sourceUpdatedAt = null, createdByUserId = UUID.randomUUID(), updatedByUserId = UUID.randomUUID(), createdAt = Instant.now(), updatedAt = Instant.now(),
	)

	private fun participant() = Participant(participantId, householdId, orgId, "Jamie", "Lee", null, null, ParticipantStatus.ACTIVE, Instant.now(), Instant.now())

	private fun onTeamAssignment() = ParticipantTeamAssignment(UUID.randomUUID(), participantId, teamId, orgId, "ACTIVE", null, Instant.now(), Instant.now())

	private fun rsvp(response: RsvpResponse = RsvpResponse.ATTENDING, source: RsvpSource = RsvpSource.GUARDIAN) = EventRsvp(
		UUID.randomUUID(), eventId, participantId, response, null, currentUser.userId, source, Instant.now(), Instant.now(),
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
			GuardianRelationship(UUID.randomUUID(), orgId, householdId, UUID.randomUUID(), currentUser.userId, GuardianRelationshipStatus.ACTIVE, Instant.now(), Instant.now())
		every { eventRsvpRepository.findByEventAndParticipant(eventId, participantId) } returns null
		every { eventRsvpRepository.upsert(eventId, participantId, RsvpResponse.MAYBE, "running late", currentUser.userId, RsvpSource.GUARDIAN) } returns rsvp(RsvpResponse.MAYBE)
		every { auditService.record(any(), any(), any(), any(), any(), any()) } just runs
		every { eventService.displayTitleFor(any(), orgId) } returns "Varsity Soccer Practice"
		every { authorizationService.listTeamStaffUserIds(orgId, teamId, Capabilities.EVENT_RSVP_READ_TEAM) } returns emptySet()
		every { outboxWriter.write(any(), any(), any(), any(), any()) } just runs

		val result = service.submit(orgId, eventId, participantId, RsvpResponse.MAYBE, "running late", currentUser)

		assertEquals(RsvpSource.GUARDIAN, result.source)
		verify(exactly = 1) { eventRsvpRepository.upsert(eventId, participantId, RsvpResponse.MAYBE, "running late", currentUser.userId, RsvpSource.GUARDIAN) }
		verify(exactly = 1) { auditService.record(any(), any(), any(), any(), any(), any()) }
	}

	@Test
	fun `submit records source SELF for an athlete self-link`() {
		every { eventRepository.findById(eventId, orgId) } returns event()
		every { participantRepository.findById(participantId, orgId) } returns participant()
		every { participantRepository.listTeamAssignments(participantId, orgId) } returns listOf(onTeamAssignment())
		every { authorizationService.hasParticipantCapability(currentUser, participantId, Capabilities.EVENT_RSVP_SELF) } returns true
		every { eventRsvpRepository.findByEventAndParticipant(eventId, participantId) } returns null
		every { eventRsvpRepository.upsert(eventId, participantId, RsvpResponse.ATTENDING, null, currentUser.userId, RsvpSource.SELF) } returns rsvp(RsvpResponse.ATTENDING, RsvpSource.SELF)
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
		every { eventRsvpRepository.upsert(eventId, participantId, RsvpResponse.ATTENDING, null, currentUser.userId, RsvpSource.SELF) } returns rsvp(RsvpResponse.ATTENDING, RsvpSource.SELF)
		every { auditService.record(any(), any(), any(), any(), any(), any()) } just runs
		every { eventService.displayTitleFor(any(), orgId) } returns "Varsity Soccer Practice"
		every { authorizationService.listTeamStaffUserIds(orgId, teamId, Capabilities.EVENT_RSVP_READ_TEAM) } returns setOf(coachUserId, currentUser.userId)
		every { appUserRepository.findById(coachUserId) } returns AppUser(coachUserId, "coach@example.com", "Coach", AppUserStatus.ACTIVE, null, Instant.now(), Instant.now())
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
		every { guardianRelationshipRepository.findActiveForUser(currentUser.userId) } returns listOf(
			GuardianRelationship(UUID.randomUUID(), orgId, householdId, UUID.randomUUID(), currentUser.userId, GuardianRelationshipStatus.ACTIVE, Instant.now(), Instant.now()),
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
