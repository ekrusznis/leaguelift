package com.rally26.integration.eventsource.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.rally26.audit.application.AuditService
import com.rally26.authorization.application.AuthorizationService
import com.rally26.authorization.domain.Capabilities
import com.rally26.common.error.ForbiddenException
import com.rally26.common.error.ValidationException
import com.rally26.common.web.CurrentUser
import com.rally26.event.domain.Event
import com.rally26.event.domain.EventSourceType
import com.rally26.event.domain.EventStatus
import com.rally26.event.domain.EventType
import com.rally26.event.domain.EventVisibility
import com.rally26.event.persistence.EventRepository
import com.rally26.membership.application.MembershipService
import com.rally26.membership.domain.MembershipRole
import com.rally26.membership.domain.MembershipStatus
import com.rally26.membership.domain.OrganizationMembership
import com.rally26.team.domain.Team
import com.rally26.team.domain.TeamStatus
import com.rally26.team.persistence.TeamRepository
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

class CsvEventImportServiceTest {
    private val eventRepository = mockk<EventRepository>()
    private val teamRepository = mockk<TeamRepository>()
    private val authorizationService = mockk<AuthorizationService>()
    private val membershipService = mockk<MembershipService>()
    private val auditService = mockk<AuditService>()
    private val service =
        CsvEventImportService(eventRepository, teamRepository, authorizationService, membershipService, auditService, ObjectMapper())

    private val orgId = UUID.randomUUID()
    private val teamId = UUID.randomUUID()
    private val currentUser = CurrentUser(UUID.randomUUID(), "coach@example.com", "Coach")

    private fun team() = Team(teamId, orgId, "Varsity Soccer", "Soccer", "Fall 2026", TeamStatus.ACTIVE, null, Instant.now(), Instant.now())

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
        syncHash: String,
    ) = Event(
        id = id,
        organizationId = orgId,
        teamId = teamId,
        tournamentId = null,
        opponentTeamId = null,
        opponentName = "Rivals",
        eventType = EventType.COMPETITION,
        title = null,
        description = null,
        status = EventStatus.TENTATIVE,
        startAt = Instant.parse("2026-09-05T15:30:00Z"),
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
        sourceType = EventSourceType.CSV_IMPORT,
        provider = "CSV_IMPORT",
        connectionId = teamId.toString(),
        externalEventId = "row-1",
        externalSyncHash = syncHash,
        sourceUpdatedAt = Instant.now(),
        createdByUserId = currentUser.userId,
        updatedByUserId = currentUser.userId,
        createdAt = Instant.now(),
        updatedAt = Instant.now(),
    )

    private fun stubTeamAccess() {
        every { authorizationService.requireTeamCapability(orgId, teamId, currentUser, Capabilities.EVENT_CREATE) } just runs
        every { teamRepository.findById(teamId, orgId) } returns team()
    }

    @Test
    fun `import requires event-create capability for a team-scoped import`() {
        every { authorizationService.requireTeamCapability(orgId, teamId, currentUser, Capabilities.EVENT_CREATE) } throws
            ForbiddenException("DENIED", "no")

        assertFailsWith<ForbiddenException> {
            service.import(orgId, teamId, "America/New_York", "external_id,event_type\nrow-1,PRACTICE\n", currentUser)
        }
    }

    @Test
    fun `import requires manager role for an org-wide import`() {
        every { membershipService.requireManagerRole(orgId, currentUser) } throws ForbiddenException("DENIED", "no")

        assertFailsWith<ForbiddenException> {
            service.import(orgId, null, "America/New_York", "external_id,event_type\nrow-1,PRACTICE\n", currentUser)
        }
    }

    @Test
    fun `import rejects an invalid timezone`() {
        stubTeamAccess()

        assertFailsWith<ValidationException> {
            service.import(orgId, teamId, "Not/AZone", "external_id,event_type\nrow-1,PRACTICE\n", currentUser)
        }
    }

    @Test
    fun `import rejects an empty CSV`() {
        stubTeamAccess()

        assertFailsWith<ValidationException> {
            service.import(orgId, teamId, "America/New_York", "", currentUser)
        }
    }

    @Test
    fun `import rejects a CSV missing required headers`() {
        stubTeamAccess()

        assertFailsWith<ValidationException> {
            service.import(orgId, teamId, "America/New_York", "title,venue_name\nGame 1,Home Field\n", currentUser)
        }
    }

    @Test
    fun `import creates a new TENTATIVE event for a valid row with no existing match`() {
        stubTeamAccess()
        every { eventRepository.findByExternalIdentity(orgId, "CSV_IMPORT", teamId.toString(), "row-1") } returns null
        every {
            eventRepository.insert(
                organizationId = orgId,
                teamId = teamId,
                tournamentId = null,
                opponentTeamId = null,
                opponentName = "Rivals",
                eventType = EventType.COMPETITION,
                title = null,
                description = null,
                startAt = Instant.parse("2026-09-05T15:30:00Z"),
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
                visibility = EventVisibility.TEAM,
                createdByUserId = currentUser.userId,
                sourceType = EventSourceType.CSV_IMPORT,
                provider = "CSV_IMPORT",
                connectionId = teamId.toString(),
                externalEventId = "row-1",
                externalSyncHash = any(),
                sourceUpdatedAt = any(),
                initialStatus = EventStatus.TENTATIVE,
            )
        } returns sampleEvent(syncHash = "irrelevant")
        every { auditService.record(any(), any(), any(), any(), any(), any()) } just runs

        val result =
            service.import(
                orgId,
                teamId,
                "America/New_York",
                "external_id,event_type,opponent_name,start_at,venue_name\nrow-1,COMPETITION,Rivals,2026-09-05T15:30:00Z,Home Field\n",
                currentUser,
            )

        assertEquals(1, result.createdCount)
        assertEquals(0, result.stagedCount)
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun `import skips a row whose sync hash already matches the existing event`() {
        stubTeamAccess()
        // The hash of a row with only external_id+event_type set (every other field null).
        val payload = listOf("row-1", "PRACTICE", null, null, null, null, null, null, null, null, null).joinToString("|") { it ?: "" }
        val digest =
            java.security.MessageDigest
                .getInstance("SHA-256")
                .digest(payload.toByteArray(Charsets.UTF_8))
        val expectedHash = digest.joinToString("") { "%02x".format(it) }
        every { eventRepository.findByExternalIdentity(orgId, "CSV_IMPORT", teamId.toString(), "row-1") } returns
            sampleEvent(syncHash = expectedHash)
        every { auditService.record(any(), any(), any(), any(), any(), any()) } just runs

        val result = service.import(orgId, teamId, "America/New_York", "external_id,event_type\nrow-1,PRACTICE\n", currentUser)

        assertEquals(0, result.createdCount)
        assertEquals(0, result.stagedCount)
        assertEquals(1, result.unchangedCount)
        verify(exactly = 0) { eventRepository.stagePendingSourceUpdate(any(), any(), any(), any()) }
    }

    @Test
    fun `import stages a pending update for an existing event when the sync hash differs, without writing any live field`() {
        stubTeamAccess()
        val existing = sampleEvent(syncHash = "stale-hash")
        every { eventRepository.findByExternalIdentity(orgId, "CSV_IMPORT", teamId.toString(), "row-1") } returns existing
        every { eventRepository.stagePendingSourceUpdate(existing.id, orgId, any(), any()) } returns 1
        every { auditService.record(any(), any(), any(), any(), any(), any()) } just runs

        val result =
            service.import(
                orgId,
                teamId,
                "America/New_York",
                "external_id,event_type,start_at\nrow-1,PRACTICE,2026-09-06T15:30:00Z\n",
                currentUser,
            )

        assertEquals(1, result.stagedCount)
        verify(exactly = 1) { eventRepository.stagePendingSourceUpdate(existing.id, orgId, any(), any()) }
        verify(exactly = 0) {
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
                any(),
                any(),
            )
        }
    }

    @Test
    fun `import collects a row-level error without aborting the batch`() {
        stubTeamAccess()
        every { eventRepository.findByExternalIdentity(orgId, "CSV_IMPORT", teamId.toString(), "row-2") } returns null
        every {
            eventRepository.insert(
                organizationId = orgId,
                teamId = teamId,
                tournamentId = null,
                opponentTeamId = null,
                opponentName = null,
                eventType = EventType.PRACTICE,
                title = null,
                description = null,
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
                createdByUserId = currentUser.userId,
                sourceType = EventSourceType.CSV_IMPORT,
                provider = "CSV_IMPORT",
                connectionId = teamId.toString(),
                externalEventId = "row-2",
                externalSyncHash = any(),
                sourceUpdatedAt = any(),
                initialStatus = EventStatus.TENTATIVE,
            )
        } returns sampleEvent(syncHash = "irrelevant")
        every { auditService.record(any(), any(), any(), any(), any(), any()) } just runs

        val result =
            service.import(
                orgId,
                teamId,
                "America/New_York",
                "external_id,event_type\n,PRACTICE\nrow-2,PRACTICE\n",
                currentUser,
            )

        assertEquals(1, result.createdCount)
        assertEquals(1, result.errors.size)
        assertEquals(2, result.errors.first().rowNumber)
    }
}
