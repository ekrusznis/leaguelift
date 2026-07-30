package com.leaguelift.integration.eventsource.application

import com.leaguelift.audit.application.AuditService
import com.leaguelift.authorization.application.AuthorizationService
import com.leaguelift.authorization.domain.Capabilities
import com.leaguelift.common.error.ForbiddenException
import com.leaguelift.common.error.ValidationException
import com.leaguelift.common.web.CurrentUser
import com.leaguelift.event.domain.Event
import com.leaguelift.event.domain.EventSourceType
import com.leaguelift.event.domain.EventStatus
import com.leaguelift.event.domain.EventType
import com.leaguelift.event.domain.EventVisibility
import com.leaguelift.event.persistence.EventRepository
import com.leaguelift.membership.application.MembershipService
import com.leaguelift.membership.domain.MembershipRole
import com.leaguelift.membership.domain.MembershipStatus
import com.leaguelift.membership.domain.OrganizationMembership
import com.leaguelift.team.domain.Team
import com.leaguelift.team.domain.TeamStatus
import com.leaguelift.team.persistence.TeamRepository
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
	private val service = CsvEventImportService(eventRepository, teamRepository, authorizationService, membershipService, auditService)

	private val orgId = UUID.randomUUID()
	private val teamId = UUID.randomUUID()
	private val currentUser = CurrentUser(UUID.randomUUID(), "coach@example.com", "Coach")

	private fun team() = Team(teamId, orgId, "Varsity Soccer", "Soccer", "Fall 2026", TeamStatus.ACTIVE, null, Instant.now(), Instant.now())

	private fun managerMembership() = OrganizationMembership(UUID.randomUUID(), orgId, currentUser.userId, MembershipRole.ADMINISTRATOR, MembershipStatus.ACTIVE, Instant.now(), Instant.now())

	private fun sampleEvent(id: UUID = UUID.randomUUID(), syncHash: String) = Event(
		id = id, organizationId = orgId, teamId = teamId, tournamentId = null, opponentTeamId = null, opponentName = "Rivals",
		eventType = EventType.COMPETITION, title = null, description = null, status = EventStatus.TENTATIVE, startAt = Instant.parse("2026-09-05T15:30:00Z"),
		endAt = null, arrivalAt = null, meetingAt = null, timezone = "America/New_York", venueName = null, address = null,
		latitude = null, longitude = null, area = null, meetingPoint = null, directionsNotes = null, visibility = EventVisibility.TEAM,
		sourceType = EventSourceType.CSV_IMPORT, provider = "CSV_IMPORT", connectionId = teamId.toString(), externalEventId = "row-1",
		externalSyncHash = syncHash, sourceUpdatedAt = Instant.now(), createdByUserId = currentUser.userId, updatedByUserId = currentUser.userId,
		createdAt = Instant.now(), updatedAt = Instant.now(),
	)

	private fun stubTeamAccess() {
		every { authorizationService.requireTeamCapability(orgId, teamId, currentUser, Capabilities.EVENT_CREATE) } just runs
		every { teamRepository.findById(teamId, orgId) } returns team()
	}

	@Test
	fun `import requires event-create capability for a team-scoped import`() {
		every { authorizationService.requireTeamCapability(orgId, teamId, currentUser, Capabilities.EVENT_CREATE) } throws ForbiddenException("DENIED", "no")

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
				organizationId = orgId, teamId = teamId, tournamentId = null, opponentTeamId = null, opponentName = "Rivals",
				eventType = EventType.COMPETITION, title = null, description = null, startAt = Instant.parse("2026-09-05T15:30:00Z"),
				endAt = null, arrivalAt = null, meetingAt = null, timezone = "America/New_York", venueName = "Home Field", address = null,
				latitude = null, longitude = null, area = null, meetingPoint = null, directionsNotes = null, visibility = EventVisibility.TEAM,
				createdByUserId = currentUser.userId, sourceType = EventSourceType.CSV_IMPORT, provider = "CSV_IMPORT",
				connectionId = teamId.toString(), externalEventId = "row-1", externalSyncHash = any(), sourceUpdatedAt = any(),
				initialStatus = EventStatus.TENTATIVE,
			)
		} returns sampleEvent(syncHash = "irrelevant")
		every { auditService.record(any(), any(), any(), any(), any(), any()) } just runs

		val result = service.import(
			orgId, teamId, "America/New_York",
			"external_id,event_type,opponent_name,start_at,venue_name\nrow-1,COMPETITION,Rivals,2026-09-05T15:30:00Z,Home Field\n",
			currentUser,
		)

		assertEquals(1, result.createdCount)
		assertEquals(0, result.updatedCount)
		assertTrue(result.errors.isEmpty())
	}

	@Test
	fun `import skips a row whose sync hash already matches the existing event`() {
		stubTeamAccess()
		// The hash of a row with only external_id+event_type set (every other field null).
		val payload = listOf("row-1", "PRACTICE", null, null, null, null, null, null, null, null, null).joinToString("|") { it ?: "" }
		val digest = java.security.MessageDigest.getInstance("SHA-256").digest(payload.toByteArray(Charsets.UTF_8))
		val expectedHash = digest.joinToString("") { "%02x".format(it) }
		every { eventRepository.findByExternalIdentity(orgId, "CSV_IMPORT", teamId.toString(), "row-1") } returns sampleEvent(syncHash = expectedHash)
		every { auditService.record(any(), any(), any(), any(), any(), any()) } just runs

		val result = service.import(orgId, teamId, "America/New_York", "external_id,event_type\nrow-1,PRACTICE\n", currentUser)

		assertEquals(0, result.createdCount)
		assertEquals(0, result.updatedCount)
		assertEquals(1, result.unchangedCount)
		verify(exactly = 0) { eventRepository.update(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
	}

	@Test
	fun `import updates an existing event when the sync hash differs`() {
		stubTeamAccess()
		val existing = sampleEvent(syncHash = "stale-hash")
		every { eventRepository.findByExternalIdentity(orgId, "CSV_IMPORT", teamId.toString(), "row-1") } returns existing
		every {
			eventRepository.update(
				id = existing.id, organizationId = orgId, title = null, description = null, status = null,
				startAt = Instant.parse("2026-09-06T15:30:00Z"), endAt = null, arrivalAt = null, meetingAt = null,
				venueName = null, address = null, latitude = null, longitude = null, area = null, meetingPoint = null,
				directionsNotes = null, opponentTeamId = null, opponentName = null, updatedByUserId = currentUser.userId,
				externalSyncHash = any(), sourceUpdatedAt = any(),
			)
		} returns 1
		every { auditService.record(any(), any(), any(), any(), any(), any()) } just runs

		val result = service.import(orgId, teamId, "America/New_York", "external_id,event_type,start_at\nrow-1,PRACTICE,2026-09-06T15:30:00Z\n", currentUser)

		assertEquals(1, result.updatedCount)
	}

	@Test
	fun `import collects a row-level error without aborting the batch`() {
		stubTeamAccess()
		every { eventRepository.findByExternalIdentity(orgId, "CSV_IMPORT", teamId.toString(), "row-2") } returns null
		every {
			eventRepository.insert(
				organizationId = orgId, teamId = teamId, tournamentId = null, opponentTeamId = null, opponentName = null,
				eventType = EventType.PRACTICE, title = null, description = null, startAt = null, endAt = null, arrivalAt = null,
				meetingAt = null, timezone = "America/New_York", venueName = null, address = null, latitude = null, longitude = null,
				area = null, meetingPoint = null, directionsNotes = null, visibility = EventVisibility.TEAM, createdByUserId = currentUser.userId,
				sourceType = EventSourceType.CSV_IMPORT, provider = "CSV_IMPORT", connectionId = teamId.toString(), externalEventId = "row-2",
				externalSyncHash = any(), sourceUpdatedAt = any(), initialStatus = EventStatus.TENTATIVE,
			)
		} returns sampleEvent(syncHash = "irrelevant")
		every { auditService.record(any(), any(), any(), any(), any(), any()) } just runs

		val result = service.import(
			orgId, teamId, "America/New_York",
			"external_id,event_type\n,PRACTICE\nrow-2,PRACTICE\n",
			currentUser,
		)

		assertEquals(1, result.createdCount)
		assertEquals(1, result.errors.size)
		assertEquals(2, result.errors.first().rowNumber)
	}
}
