package com.rally26.event.application

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.rally26.audit.application.AuditService
import com.rally26.authorization.application.AuthorizationService
import com.rally26.authorization.domain.Capabilities
import com.rally26.common.error.FieldError
import com.rally26.common.error.ForbiddenException
import com.rally26.common.error.NotFoundException
import com.rally26.common.error.ValidationException
import com.rally26.common.web.CurrentUser
import com.rally26.event.domain.Event
import com.rally26.event.domain.EventSourceType
import com.rally26.event.domain.EventStatus
import com.rally26.event.domain.EventType
import com.rally26.event.domain.EventVisibility
import com.rally26.event.domain.PendingSourceEventSnapshot
import com.rally26.event.persistence.EventRepository
import com.rally26.household.persistence.HouseholdRepository
import com.rally26.membership.application.MembershipService
import com.rally26.membership.domain.MembershipRole
import com.rally26.membership.domain.MembershipStatus
import com.rally26.membership.domain.OrganizationMembership
import com.rally26.outbox.application.OutboxWriter
import com.rally26.participant.persistence.ParticipantRepository
import com.rally26.team.domain.Team
import com.rally26.team.domain.TeamStatus
import com.rally26.team.persistence.TeamRepository
import com.rally26.timezone.application.TimeZoneService
import com.rally26.tournament.persistence.TournamentRepository
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class EventServiceTest {
    private val eventRepository = mockk<EventRepository>()
    private val teamRepository = mockk<TeamRepository>()
    private val tournamentRepository = mockk<TournamentRepository>()
    private val householdRepository = mockk<HouseholdRepository>()
    private val participantRepository = mockk<ParticipantRepository>()
    private val membershipService = mockk<MembershipService>()
    private val authorizationService = mockk<AuthorizationService>()
    private val auditService = mockk<AuditService>()
    private val calendarProvider = mockk<CalendarProvider>()
    private val mapsProvider = mockk<MapsProvider>()
    private val outboxWriter = mockk<OutboxWriter>()
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
            every { resolveEffectiveZone(any(), any(), any()) } returns "America/New_York"
        }
    private val service =
        EventService(
            eventRepository,
            teamRepository,
            tournamentRepository,
            householdRepository,
            participantRepository,
            membershipService,
            authorizationService,
            auditService,
            calendarProvider,
            mapsProvider,
            outboxWriter,
            jacksonObjectMapper(),
            timeZoneService,
        )

    private val orgId = UUID.randomUUID()
    private val teamId = UUID.randomUUID()
    private val participantId2 = UUID.randomUUID()
    private val currentUser = CurrentUser(UUID.randomUUID(), "coach@example.com", "Coach")

    private fun team(id: UUID = teamId) =
        Team(id, orgId, "Varsity Soccer", "Soccer", "Fall 2026", TeamStatus.ACTIVE, null, Instant.now(), Instant.now())

    private fun managerMembership() =
        OrganizationMembership(
            UUID.randomUUID(),
            orgId,
            currentUser.userId,
            MembershipRole.ADMINISTRATOR,
            MembershipStatus.ACTIVE,
            Instant.now(),
            Instant.now(),
        )

    private fun sampleEvent(
        id: UUID = UUID.randomUUID(),
        teamId: UUID? = this.teamId,
        tournamentId: UUID? = null,
        status: EventStatus = EventStatus.DRAFT,
    ) = Event(
        id = id,
        organizationId = orgId,
        teamId = teamId,
        tournamentId = tournamentId,
        opponentTeamId = null,
        opponentName = null,
        eventType = EventType.PRACTICE,
        title = null,
        description = null,
        status = status,
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
        createdByUserId = currentUser.userId,
        updatedByUserId = currentUser.userId,
        createdAt = Instant.now(),
        updatedAt = Instant.now(),
    )

    @Test
    fun `create rejects an invalid timezone`() {
        assertFailsWith<ValidationException> {
            service.create(
                orgId,
                teamId,
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
                "Not/AZone",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                EventVisibility.TEAM,
                currentUser,
            )
        }
    }

    @Test
    fun `create rejects an opponent team equal to the owning team`() {
        assertFailsWith<ValidationException> {
            service.create(
                orgId,
                teamId,
                null,
                teamId,
                null,
                EventType.COMPETITION,
                null,
                null,
                null,
                null,
                null,
                null,
                "America/New_York",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                EventVisibility.TEAM,
                currentUser,
            )
        }
    }

    @Test
    fun `create requires team event-create capability when a team is given`() {
        every { authorizationService.requireTeamCapability(orgId, teamId, currentUser, Capabilities.EVENT_CREATE) } throws
            ForbiddenException("DENIED", "no")

        assertFailsWith<ForbiddenException> {
            service.create(
                orgId,
                teamId,
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
                "America/New_York",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                EventVisibility.TEAM,
                currentUser,
            )
        }
    }

    @Test
    fun `create requires manager role for an org-wide event with no team or tournament`() {
        every { membershipService.requireManagerRole(orgId, currentUser) } throws ForbiddenException("DENIED", "no")

        assertFailsWith<ForbiddenException> {
            service.create(
                orgId,
                null,
                null,
                null,
                null,
                EventType.MEETING,
                "Annual Meeting",
                null,
                null,
                null,
                null,
                null,
                "America/New_York",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                EventVisibility.ORGANIZATION,
                currentUser,
            )
        }
    }

    @Test
    fun `create succeeds for a team-scoped event and records an audit event`() {
        every { authorizationService.requireTeamCapability(orgId, teamId, currentUser, Capabilities.EVENT_CREATE) } just runs
        every { teamRepository.findById(teamId, orgId) } returns team()
        val created = sampleEvent()
        every {
            eventRepository.insert(
                orgId,
                teamId,
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
                "America/New_York",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                EventVisibility.TEAM,
                currentUser.userId,
            )
        } returns created
        every { auditService.record(currentUser.userId, orgId, "event.created", "event", created.id) } just runs

        val result =
            service.create(
                orgId,
                teamId,
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
                "America/New_York",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                EventVisibility.TEAM,
                currentUser,
            )

        assertEquals(created.id, result.id)
        verify(exactly = 1) { auditService.record(currentUser.userId, orgId, "event.created", "event", created.id) }
    }

    @Test
    fun `create succeeds for an all-day event with every instant field null`() {
        every { authorizationService.requireTeamCapability(orgId, teamId, currentUser, Capabilities.EVENT_CREATE) } just runs
        every { teamRepository.findById(teamId, orgId) } returns team()
        val allDayDate = java.time.LocalDate.of(2026, 7, 4)
        val created = sampleEvent().copy(allDayDate = allDayDate, startAt = null, endAt = null)
        every {
            eventRepository.insert(
                orgId,
                teamId,
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
                "America/New_York",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                EventVisibility.TEAM,
                currentUser.userId,
                allDayDate = allDayDate,
            )
        } returns created
        every { auditService.record(currentUser.userId, orgId, "event.created", "event", created.id) } just runs

        val result =
            service.create(
                orgId,
                teamId,
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
                "America/New_York",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                EventVisibility.TEAM,
                currentUser,
                allDayDate = allDayDate,
            )

        assertEquals(allDayDate, result.allDayDate)
    }

    @Test
    fun `create rejects an all-day date combined with a start time`() {
        assertFailsWith<ValidationException> {
            service.create(
                orgId,
                teamId,
                null,
                null,
                null,
                EventType.PRACTICE,
                null,
                null,
                Instant.now(),
                null,
                null,
                null,
                "America/New_York",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                EventVisibility.TEAM,
                currentUser,
                allDayDate = java.time.LocalDate.of(2026, 7, 4),
            )
        }
    }

    @Test
    fun `a later organization timezone change does not rewrite an already-created event's snapshot`() {
        every { authorizationService.requireTeamCapability(orgId, teamId, currentUser, Capabilities.EVENT_CREATE) } just runs
        every { teamRepository.findById(teamId, orgId) } returns team()
        val firstEvent = sampleEvent()
        every {
            eventRepository.insert(
                orgId,
                teamId,
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
                "America/New_York",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                EventVisibility.TEAM,
                currentUser.userId,
            )
        } returns firstEvent
        every { auditService.record(currentUser.userId, orgId, "event.created", "event", firstEvent.id) } just runs

        val result =
            service.create(
                orgId,
                teamId,
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
                "America/New_York",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                EventVisibility.TEAM,
                currentUser,
            )

        // The organization's default timezone changing afterward (simulated here by simply
        // never re-reading it) must never retroactively alter this already-persisted snapshot.
        assertEquals("America/New_York", result.timezone)
    }

    @Test
    fun `create throws NotFoundException when the team does not belong to the organization`() {
        every { authorizationService.requireTeamCapability(orgId, teamId, currentUser, Capabilities.EVENT_CREATE) } just runs
        every { teamRepository.findById(teamId, orgId) } returns null

        assertFailsWith<NotFoundException> {
            service.create(
                orgId,
                teamId,
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
                "America/New_York",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                EventVisibility.TEAM,
                currentUser,
            )
        }
    }

    @Test
    fun `publish rejects an event that is not DRAFT`() {
        val event = sampleEvent(status = EventStatus.SCHEDULED)
        every { eventRepository.findById(event.id, orgId) } returns event
        every { authorizationService.requireTeamCapability(orgId, teamId, currentUser, Capabilities.EVENT_PUBLISH) } just runs

        assertFailsWith<ValidationException> {
            service.publish(orgId, event.id, currentUser)
        }
    }

    @Test
    fun `publish moves a DRAFT event to TENTATIVE`() {
        val event = sampleEvent(status = EventStatus.DRAFT)
        every { eventRepository.findById(event.id, orgId) } returns event andThen event.copy(status = EventStatus.TENTATIVE)
        every { authorizationService.requireTeamCapability(orgId, teamId, currentUser, Capabilities.EVENT_PUBLISH) } just runs
        every { eventRepository.updateStatus(event.id, orgId, EventStatus.TENTATIVE, currentUser.userId) } returns 1
        every { auditService.record(currentUser.userId, orgId, "event.published", "event", event.id) } just runs
        every { teamRepository.findById(teamId, orgId) } returns team()
        every { householdRepository.findActiveForTeam(teamId, orgId) } returns emptyList()
        every { outboxWriter.write(any(), any(), any(), any(), any()) } just runs

        val result = service.publish(orgId, event.id, currentUser)

        assertEquals(EventStatus.TENTATIVE, result.status)
        verify(exactly = 1) { outboxWriter.write(any(), any(), any(), eq("event.created"), any()) }
    }

    @Test
    fun `cancel rejects an already-completed event`() {
        val event = sampleEvent(status = EventStatus.COMPLETED)
        every { eventRepository.findById(event.id, orgId) } returns event
        every { authorizationService.requireTeamCapability(orgId, teamId, currentUser, Capabilities.EVENT_CANCEL) } just runs

        assertFailsWith<ValidationException> {
            service.cancel(orgId, event.id, currentUser)
        }
    }

    @Test
    fun `cancel succeeds for a scheduled event`() {
        val event = sampleEvent(status = EventStatus.SCHEDULED)
        every { eventRepository.findById(event.id, orgId) } returns event andThen event.copy(status = EventStatus.CANCELLED)
        every { authorizationService.requireTeamCapability(orgId, teamId, currentUser, Capabilities.EVENT_CANCEL) } just runs
        every { eventRepository.updateStatus(event.id, orgId, EventStatus.CANCELLED, currentUser.userId) } returns 1
        every { auditService.record(currentUser.userId, orgId, "event.cancelled", "event", event.id) } just runs
        every { teamRepository.findById(teamId, orgId) } returns team()
        every { householdRepository.findActiveForTeam(teamId, orgId) } returns emptyList()
        every { outboxWriter.write(any(), any(), any(), any(), any()) } just runs

        val result = service.cancel(orgId, event.id, currentUser)

        assertEquals(EventStatus.CANCELLED, result.status)
        verify(exactly = 1) { outboxWriter.write(any(), any(), any(), eq("event.cancelled"), any()) }
        verify(exactly = 1) { auditService.record(currentUser.userId, orgId, "event.cancelled", "event", event.id) }
    }

    @Test
    fun `an org-wide event (no team, no tournament) falls back to organization manager-role checks for manage actions`() {
        val event = sampleEvent(teamId = null, status = EventStatus.SCHEDULED)
        every { eventRepository.findById(event.id, orgId) } returns event andThen event.copy(status = EventStatus.CANCELLED)
        every { membershipService.requireManagerRole(orgId, currentUser) } returns managerMembership()
        every { eventRepository.updateStatus(event.id, orgId, EventStatus.CANCELLED, currentUser.userId) } returns 1
        every { auditService.record(currentUser.userId, orgId, "event.cancelled", "event", event.id) } just runs
        every { outboxWriter.write(any(), any(), any(), any(), any()) } just runs

        service.cancel(orgId, event.id, currentUser)

        verify(exactly = 1) { membershipService.requireManagerRole(orgId, currentUser) }
        verify(exactly = 0) { authorizationService.requireTeamCapability(any(), any(), any(), any()) }
    }

    @Test
    fun `get throws NotFoundException for an event in another organization`() {
        every { eventRepository.findById(any(), orgId) } returns null

        assertFailsWith<NotFoundException> {
            service.get(orgId, UUID.randomUUID(), currentUser)
        }
    }

    @Test
    fun `listForTeam requires the event-read capability at team scope`() {
        every { authorizationService.requireTeamCapability(orgId, teamId, currentUser, Capabilities.EVENT_READ) } throws
            ForbiddenException("DENIED", "no")

        assertFailsWith<ForbiddenException> {
            service.listForTeam(orgId, teamId, currentUser, 0, 20)
        }
    }

    @Test
    fun `listForHousehold denies a caller with neither org membership nor a guardian relationship`() {
        val householdId = UUID.randomUUID()
        every { householdRepository.findById(householdId, orgId) } returns mockk()
        every { authorizationService.hasHouseholdCapability(orgId, householdId, currentUser, Capabilities.EVENT_READ) } returns false

        assertFailsWith<ForbiddenException> {
            service.listForHousehold(orgId, householdId, currentUser, 0, 20)
        }
    }

    @Test
    fun `listForHousehold resolves events across every linked participant's teams`() {
        val householdId = UUID.randomUUID()
        val participantA =
            com.rally26.participant.domain.Participant(
                UUID.randomUUID(),
                householdId,
                orgId,
                "Jamie",
                "Lee",
                null,
                null,
                com.rally26.participant.domain.ParticipantStatus.ACTIVE,
                Instant.now(),
                Instant.now(),
            )
        val teamAssignment =
            com.rally26.participant.domain.ParticipantTeamAssignment(
                UUID.randomUUID(),
                participantA.id,
                teamId,
                orgId,
                "ACTIVE",
                null,
                Instant.now(),
                Instant.now(),
            )
        every { householdRepository.findById(householdId, orgId) } returns mockk()
        every { authorizationService.hasHouseholdCapability(orgId, householdId, currentUser, Capabilities.EVENT_READ) } returns true
        every { participantRepository.findByHousehold(householdId, orgId) } returns listOf(participantA)
        every { participantRepository.listTeamAssignments(participantA.id, orgId) } returns listOf(teamAssignment)
        every { eventRepository.findByTeams(setOf(teamId), orgId, 0, 20) } returns listOf(sampleEvent(status = EventStatus.SCHEDULED))

        val result = service.listForHousehold(orgId, householdId, currentUser, 0, 20)

        assertEquals(1, result.size)
    }

    @Test
    fun `listForParticipant allows the linked athlete via ATHLETE_SCHEDULE_VIEW`() {
        val participant =
            com.rally26.participant.domain.Participant(
                participantId2,
                UUID.randomUUID(),
                orgId,
                "Jamie",
                "Lee",
                null,
                null,
                com.rally26.participant.domain.ParticipantStatus.ACTIVE,
                Instant.now(),
                Instant.now(),
            )
        every { participantRepository.findById(participantId2, orgId) } returns participant
        every { authorizationService.hasParticipantCapability(currentUser, participantId2, Capabilities.ATHLETE_SCHEDULE_VIEW) } returns
            true
        every { participantRepository.listTeamAssignments(participantId2, orgId) } returns emptyList()
        every { eventRepository.findByTeams(emptySet(), orgId, 0, 20) } returns emptyList()

        val result = service.listForParticipant(orgId, participantId2, currentUser, 0, 20)

        assertEquals(0, result.size)
        verify(exactly = 0) { authorizationService.hasHouseholdCapability(any(), any(), any(), any()) }
    }

    @Test
    fun `listForParticipant denies an athlete's self-link from viewing a sibling's schedule`() {
        val siblingHouseholdId = UUID.randomUUID()
        val sibling =
            com.rally26.participant.domain.Participant(
                participantId2,
                siblingHouseholdId,
                orgId,
                "Sam",
                "Lee",
                null,
                null,
                com.rally26.participant.domain.ParticipantStatus.ACTIVE,
                Instant.now(),
                Instant.now(),
            )
        every { participantRepository.findById(participantId2, orgId) } returns sibling
        // currentUser's own self-link is tied to a different participant, so a check scoped to
        // participantId2 (the sibling) correctly returns false — same mechanism ATHLETE_SCHEDULE_VIEW
        // itself uses to key off the exact participant id, not just "is this user an athlete."
        every { authorizationService.hasParticipantCapability(currentUser, participantId2, Capabilities.ATHLETE_SCHEDULE_VIEW) } returns
            false
        every { authorizationService.hasHouseholdCapability(orgId, siblingHouseholdId, currentUser, Capabilities.EVENT_READ) } returns false

        assertFailsWith<ForbiddenException> {
            service.listForParticipant(orgId, participantId2, currentUser, 0, 20)
        }
    }

    @Test
    fun `get allows a guardian to open a published event for a linked participant team`() {
        val householdId = UUID.randomUUID()
        val participant =
            com.rally26.participant.domain.Participant(
                UUID.randomUUID(),
                householdId,
                orgId,
                "Jamie",
                "Lee",
                null,
                null,
                com.rally26.participant.domain.ParticipantStatus.ACTIVE,
                Instant.now(),
                Instant.now(),
            )
        val event = sampleEvent(status = EventStatus.SCHEDULED)
        every { eventRepository.findById(event.id, orgId) } returns event
        every { authorizationService.hasTeamCapability(orgId, teamId, currentUser, Capabilities.EVENT_READ) } returns false
        every { participantRepository.findActiveByTeam(teamId, orgId) } returns listOf(participant)
        every { authorizationService.hasParticipantCapability(currentUser, participant.id, Capabilities.ATHLETE_SCHEDULE_VIEW) } returns
            false
        every { authorizationService.hasHouseholdCapability(orgId, householdId, currentUser, Capabilities.EVENT_READ) } returns true

        val result = service.get(orgId, event.id, currentUser)

        assertEquals(event.id, result.id)
    }

    @Test
    fun `get never exposes a draft team event through guardian schedule access`() {
        val event = sampleEvent(status = EventStatus.DRAFT)
        every { eventRepository.findById(event.id, orgId) } returns event
        every { authorizationService.hasTeamCapability(orgId, teamId, currentUser, Capabilities.EVENT_READ) } returns false

        assertFailsWith<ForbiddenException> {
            service.get(orgId, event.id, currentUser)
        }
        verify(exactly = 0) { participantRepository.findActiveByTeam(any(), any()) }
    }

    @Test
    fun `getIcsForEvent reuses get's read authorization and builds ics from the resolved display title`() {
        val event = sampleEvent()
        every { eventRepository.findById(event.id, orgId) } returns event
        every { authorizationService.hasTeamCapability(orgId, teamId, currentUser, Capabilities.EVENT_READ) } returns true
        every { teamRepository.findById(teamId, orgId) } returns team()
        every { calendarProvider.buildIcs(event, "Varsity Soccer") } returns "BEGIN:VCALENDAR..."

        val ics = service.getIcsForEvent(orgId, event.id, currentUser)

        assertEquals("BEGIN:VCALENDAR...", ics)
    }

    @Test
    fun `getDirections delegates to MapsProvider using only address and coordinates`() {
        val event = sampleEvent().copy(address = "123 Main St", latitude = 1.0, longitude = 2.0)
        every { eventRepository.findById(event.id, orgId) } returns event
        every { authorizationService.hasTeamCapability(orgId, teamId, currentUser, Capabilities.EVENT_READ) } returns true
        every { mapsProvider.buildDirectionsUrl("123 Main St", 1.0, 2.0) } returns "https://maps.example/dir"

        val result = service.getDirections(orgId, event.id, currentUser)

        assertEquals("https://maps.example/dir", result)
    }

    private fun stubEventRepositoryUpdate() {
        every {
            eventRepository.update(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
            )
        } returns 1
    }

    @Test
    fun `update does not notify when the event is still DRAFT`() {
        val before = sampleEvent(status = EventStatus.DRAFT)
        val after = before.copy(startAt = Instant.now())
        every { eventRepository.findById(before.id, orgId) } returns before andThen after
        every { authorizationService.requireTeamCapability(orgId, teamId, currentUser, Capabilities.EVENT_UPDATE) } just runs
        stubEventRepositoryUpdate()
        every { auditService.record(currentUser.userId, orgId, "event.updated", "event", before.id) } just runs

        service.update(
            orgId,
            before.id,
            null,
            null,
            null,
            after.startAt,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            currentUser,
        )

        verify(exactly = 0) { outboxWriter.write(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `update fires event_time_changed when startAt changes on an already-published event`() {
        val before = sampleEvent(status = EventStatus.TENTATIVE).copy(startAt = Instant.parse("2026-08-01T10:00:00Z"))
        val after = before.copy(startAt = Instant.parse("2026-08-01T12:00:00Z"))
        every { eventRepository.findById(before.id, orgId) } returns before andThen after
        every { authorizationService.requireTeamCapability(orgId, teamId, currentUser, Capabilities.EVENT_UPDATE) } just runs
        stubEventRepositoryUpdate()
        every { auditService.record(currentUser.userId, orgId, "event.updated", "event", before.id) } just runs
        every { teamRepository.findById(teamId, orgId) } returns team()
        every { householdRepository.findActiveForTeam(teamId, orgId) } returns emptyList()
        every { outboxWriter.write(any(), any(), any(), any(), any()) } just runs

        service.update(
            orgId,
            before.id,
            null,
            null,
            null,
            after.startAt,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            currentUser,
        )

        verify(exactly = 1) { outboxWriter.write(any(), any(), any(), eq("event.time_changed"), any()) }
        verify(exactly = 1) { auditService.record(currentUser.userId, orgId, "event.updated", "event", before.id) }
    }

    @Test
    fun `update on a tournament child event with no team collapses every changed field into one tournament_event_updated notification`() {
        val tournamentId = UUID.randomUUID()
        val before =
            sampleEvent(teamId = null, tournamentId = tournamentId, status = EventStatus.TENTATIVE)
                .copy(startAt = Instant.parse("2026-08-01T10:00:00Z"), area = "Field 1")
        val after = before.copy(startAt = Instant.parse("2026-08-01T12:00:00Z"), area = "Field 2")
        every { eventRepository.findById(before.id, orgId) } returns before andThen after
        every { authorizationService.requireTournamentCapability(orgId, tournamentId, currentUser, Capabilities.EVENT_UPDATE) } just runs
        stubEventRepositoryUpdate()
        every { auditService.record(currentUser.userId, orgId, "event.updated", "event", before.id) } just runs
        every { outboxWriter.write(any(), any(), any(), any(), any()) } just runs

        service.update(
            orgId,
            before.id,
            null,
            null,
            null,
            after.startAt,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            after.area,
            null,
            null,
            null,
            null,
            currentUser,
        )

        verify(exactly = 1) { outboxWriter.write(any(), any(), any(), eq("tournament.event_updated"), any()) }
        verify(exactly = 1) { auditService.record(currentUser.userId, orgId, "event.updated", "event", before.id) }
    }

    @Test
    fun `update fills in TBD opponent, time, and area on a tournament child event without changing its identity`() {
        val tournamentId = UUID.randomUUID()
        val opponentTeamId = UUID.randomUUID()
        val before = sampleEvent(teamId = null, tournamentId = tournamentId, status = EventStatus.DRAFT)
        val filledIn = before.copy(startAt = Instant.parse("2026-08-01T10:00:00Z"), area = "Field 1", opponentTeamId = opponentTeamId)
        every { eventRepository.findById(before.id, orgId) } returns before andThen filledIn
        every { authorizationService.requireTournamentCapability(orgId, tournamentId, currentUser, Capabilities.EVENT_UPDATE) } just runs
        every { teamRepository.findById(opponentTeamId, orgId) } returns team(opponentTeamId)
        stubEventRepositoryUpdate()
        every { auditService.record(currentUser.userId, orgId, "event.updated", "event", before.id) } just runs

        val result =
            service.update(
                orgId,
                before.id,
                null,
                null,
                null,
                filledIn.startAt,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                filledIn.area,
                null,
                null,
                opponentTeamId,
                null,
                currentUser,
            )

        assertEquals(before.id, result.id)
        assertEquals(filledIn.startAt, result.startAt)
        assertEquals(filledIn.area, result.area)
        assertEquals(opponentTeamId, result.opponentTeamId)
        // Same row updated in place (same id), never a new insert — a duplicate event/tournament
        // child would be a much worse bug than a missed field, since it would silently orphan the
        // original identity every RSVP/notification/audit row already references.
        verify(exactly = 1) {
            eventRepository.update(
                before.id,
                orgId,
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                currentUser.userId,
                any(),
                any(),
            )
        }
        verify(exactly = 0) {
            eventRepository.insert(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
            )
        }
    }

    @Test
    fun `postpone notifies with event_postponed for an already-published event`() {
        val event = sampleEvent(status = EventStatus.SCHEDULED)
        every { eventRepository.findById(event.id, orgId) } returns event andThen event.copy(status = EventStatus.POSTPONED)
        every { authorizationService.requireTeamCapability(orgId, teamId, currentUser, Capabilities.EVENT_CANCEL) } just runs
        every { eventRepository.updateStatus(event.id, orgId, EventStatus.POSTPONED, currentUser.userId) } returns 1
        every { auditService.record(currentUser.userId, orgId, "event.postponed", "event", event.id) } just runs
        every { teamRepository.findById(teamId, orgId) } returns team()
        every { householdRepository.findActiveForTeam(teamId, orgId) } returns emptyList()
        every { outboxWriter.write(any(), any(), any(), any(), any()) } just runs

        val result = service.postpone(orgId, event.id, currentUser)

        assertEquals(EventStatus.POSTPONED, result.status)
        verify(exactly = 1) { outboxWriter.write(any(), any(), any(), eq("event.postponed"), any()) }
        verify(exactly = 1) { auditService.record(currentUser.userId, orgId, "event.postponed", "event", event.id) }
    }

    @Test
    fun `update now allows a source-owned field change directly on an imported event (2026-08-13 redesign — see applySourceUpdate)`() {
        val imported = sampleEvent(status = EventStatus.TENTATIVE).copy(provider = "CSV_IMPORT", sourceType = EventSourceType.CSV_IMPORT)
        val newStart = Instant.parse("2026-09-10T18:00:00Z")
        every { eventRepository.findById(imported.id, orgId) } returns imported andThen imported.copy(startAt = newStart)
        every { authorizationService.requireTeamCapability(orgId, teamId, currentUser, Capabilities.EVENT_UPDATE) } just runs
        stubEventRepositoryUpdate()
        every { auditService.record(currentUser.userId, orgId, "event.updated", "event", imported.id) } just runs
        every { teamRepository.findById(teamId, orgId) } returns team()
        every { householdRepository.findActiveForTeam(teamId, orgId) } returns emptyList()
        every { outboxWriter.write(any(), any(), any(), any(), any()) } just runs

        val result =
            service.update(
                orgId,
                imported.id,
                null,
                null,
                null,
                newStart,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                currentUser,
            )

        assertEquals(newStart, result.startAt)
    }

    @Test
    fun `update still allows an overlay-only field change on an imported event`() {
        val imported = sampleEvent(status = EventStatus.TENTATIVE).copy(provider = "CSV_IMPORT", sourceType = EventSourceType.CSV_IMPORT)
        every { eventRepository.findById(imported.id, orgId) } returns imported andThen imported.copy(meetingPoint = "By the main gate")
        every { authorizationService.requireTeamCapability(orgId, teamId, currentUser, Capabilities.EVENT_UPDATE) } just runs
        stubEventRepositoryUpdate()
        every { auditService.record(currentUser.userId, orgId, "event.updated", "event", imported.id) } just runs
        every { teamRepository.findById(teamId, orgId) } returns team()
        every { householdRepository.findActiveForTeam(teamId, orgId) } returns emptyList()
        every { outboxWriter.write(any(), any(), any(), any(), any()) } just runs

        val result =
            service.update(
                orgId,
                imported.id,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "By the main gate",
                null,
                null,
                null,
                currentUser,
            )

        assertEquals("By the main gate", result.meetingPoint)
        verify(exactly = 1) { auditService.record(currentUser.userId, orgId, "event.updated", "event", imported.id) }
    }

    @Test
    fun `detachFromSource rejects an event that was never imported`() {
        val manual = sampleEvent()
        every { eventRepository.findById(manual.id, orgId) } returns manual
        every { authorizationService.requireTeamCapability(orgId, teamId, currentUser, Capabilities.EVENT_UPDATE) } just runs

        assertFailsWith<ValidationException> {
            service.detachFromSource(orgId, manual.id, currentUser)
        }
    }

    @Test
    fun `detachFromSource clears import identity and records an audit event`() {
        val imported = sampleEvent().copy(provider = "CSV_IMPORT", sourceType = EventSourceType.CSV_IMPORT, externalEventId = "row-1")
        every { eventRepository.findById(imported.id, orgId) } returns imported andThen
            imported.copy(
                provider = null,
                sourceType = EventSourceType.MANUAL,
                externalEventId = null,
            )
        every { authorizationService.requireTeamCapability(orgId, teamId, currentUser, Capabilities.EVENT_UPDATE) } just runs
        every { eventRepository.detachFromSource(imported.id, orgId, currentUser.userId) } returns 1
        every { auditService.record(currentUser.userId, orgId, "event.detached_from_source", "event", imported.id) } just runs

        val result = service.detachFromSource(orgId, imported.id, currentUser)

        assertEquals(EventSourceType.MANUAL, result.sourceType)
        verify(exactly = 1) { eventRepository.detachFromSource(imported.id, orgId, currentUser.userId) }
        verify(exactly = 1) { auditService.record(currentUser.userId, orgId, "event.detached_from_source", "event", imported.id) }
    }

    @Test
    fun `applySourceUpdate rejects an event with nothing staged`() {
        val event = sampleEvent().copy(provider = "ICS_FEED", sourceType = EventSourceType.ICS_FEED)
        every { eventRepository.findById(event.id, orgId) } returns event
        every { authorizationService.requireTeamCapability(orgId, teamId, currentUser, Capabilities.EVENT_UPDATE) } just runs

        assertFailsWith<ValidationException> {
            service.applySourceUpdate(orgId, event.id, currentUser)
        }
    }

    @Test
    fun `applySourceUpdate writes the staged field values, clears the pending snapshot, and records a full before-after audit diff`() {
        val objectMapper = jacksonObjectMapper()
        val snapshot =
            PendingSourceEventSnapshot(
                title = "Updated title",
                description = null,
                status = null,
                startAt = "2026-09-10T18:00:00Z",
                endAt = null,
                arrivalAt = null,
                venueName = "New venue",
                address = null,
                area = null,
                opponentName = null,
            )
        val event =
            sampleEvent(status = EventStatus.TENTATIVE).copy(
                provider = "ICS_FEED",
                sourceType = EventSourceType.ICS_FEED,
                title = "Old title",
                venueName = "Old venue",
                pendingSourceSnapshotJson = objectMapper.writeValueAsString(snapshot),
                pendingSourceHash = "new-hash-123",
            )
        every { eventRepository.findById(event.id, orgId) } returns event andThen
            event.copy(
                title = "Updated title",
                venueName = "New venue",
                startAt = Instant.parse("2026-09-10T18:00:00Z"),
                externalSyncHash = "new-hash-123",
                pendingSourceSnapshotJson = null,
                pendingSourceHash = null,
            )
        every { authorizationService.requireTeamCapability(orgId, teamId, currentUser, Capabilities.EVENT_UPDATE) } just runs
        // Not the shared stubEventRepositoryUpdate() helper — that one's `any()` matcher list
        // implicitly requires externalSyncHash/sourceUpdatedAt/allDayDate to be null (their
        // defaults), but applySourceUpdate explicitly passes real values for the first two.
        every {
            eventRepository.update(
                id = event.id,
                organizationId = orgId,
                title = "Updated title",
                description = null,
                status = null,
                startAt = Instant.parse("2026-09-10T18:00:00Z"),
                endAt = null,
                arrivalAt = null,
                meetingAt = null,
                venueName = "New venue",
                address = null,
                latitude = null,
                longitude = null,
                area = null,
                meetingPoint = null,
                directionsNotes = null,
                opponentTeamId = null,
                opponentName = null,
                updatedByUserId = currentUser.userId,
                externalSyncHash = "new-hash-123",
                sourceUpdatedAt = any(),
            )
        } returns 1
        every { eventRepository.clearPendingSourceUpdate(event.id, orgId) } returns 1
        every { auditService.record(currentUser.userId, orgId, "event.updated_from_source", "event", event.id, metadataJson = any()) } just
            runs
        every { teamRepository.findById(teamId, orgId) } returns team()
        every { householdRepository.findActiveForTeam(teamId, orgId) } returns emptyList()
        every { outboxWriter.write(any(), any(), any(), any(), any()) } just runs

        val result = service.applySourceUpdate(orgId, event.id, currentUser)

        assertEquals("Updated title", result.title)
        assertEquals("New venue", result.venueName)
        assertEquals(null, result.pendingSourceSnapshotJson)
        verify(exactly = 1) { eventRepository.clearPendingSourceUpdate(event.id, orgId) }
        verify(exactly = 1) {
            auditService.record(
                currentUser.userId,
                orgId,
                "event.updated_from_source",
                "event",
                event.id,
                metadataJson =
                    match {
                        it.contains("\"field\":\"title\"") &&
                            it.contains("\"oldValue\":\"Old title\"") &&
                            it.contains("\"newValue\":\"Updated title\"") &&
                            it.contains("\"field\":\"venueName\"")
                    },
            )
        }
    }

    @Test
    fun `describePendingSourceUpdate returns null when nothing is staged`() {
        val event = sampleEvent()

        assertEquals(null, service.describePendingSourceUpdate(event))
    }

    @Test
    fun `describePendingSourceUpdate only includes fields the source actually sets and that actually differ`() {
        val objectMapper = jacksonObjectMapper()
        val snapshot =
            PendingSourceEventSnapshot(
                title = "Same title",
                description = null,
                status = null,
                startAt = null,
                endAt = null,
                arrivalAt = null,
                venueName = "New venue",
                address = null,
                area = null,
                opponentName = null,
            )
        val event =
            sampleEvent().copy(
                title = "Same title",
                venueName = "Old venue",
                pendingSourceSnapshotJson = objectMapper.writeValueAsString(snapshot),
                pendingSourceHash = "hash",
            )

        val changes = service.describePendingSourceUpdate(event)

        assertEquals(1, changes?.size)
        assertEquals("venueName", changes?.single()?.field)
        assertEquals("Old venue", changes?.single()?.oldValue)
        assertEquals("New venue", changes?.single()?.newValue)
    }

    @Test
    fun `update rejects an all-day date combined with a start time in the same call`() {
        val before = sampleEvent(status = EventStatus.DRAFT)
        every { eventRepository.findById(before.id, orgId) } returns before
        every { authorizationService.requireTeamCapability(orgId, teamId, currentUser, Capabilities.EVENT_UPDATE) } just runs

        assertFailsWith<ValidationException> {
            service.update(
                orgId,
                before.id,
                null,
                null,
                null,
                Instant.now(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                currentUser,
                allDayDate = java.time.LocalDate.of(2026, 7, 4),
            )
        }
    }

    @Test
    fun `update rejects converting an already-timed event to all-day`() {
        val before = sampleEvent(status = EventStatus.DRAFT).copy(startAt = Instant.now())
        every { eventRepository.findById(before.id, orgId) } returns before
        every { authorizationService.requireTeamCapability(orgId, teamId, currentUser, Capabilities.EVENT_UPDATE) } just runs

        assertFailsWith<ValidationException> {
            service.update(
                orgId,
                before.id,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                currentUser,
                allDayDate = java.time.LocalDate.of(2026, 7, 4),
            )
        }
    }

    @Test
    fun `resolveDefaultTimezone requires active membership and delegates to the effective-zone resolution`() {
        every { membershipService.requireActiveMembership(orgId, currentUser) } returns managerMembership()
        every { timeZoneService.resolveEffectiveZone(orgId, teamId, null) } returns "America/Chicago"

        val result = service.resolveDefaultTimezone(orgId, teamId, null, currentUser)

        assertEquals("America/Chicago", result)
        verify(exactly = 1) { timeZoneService.resolveEffectiveZone(orgId, teamId, null) }
    }
}
