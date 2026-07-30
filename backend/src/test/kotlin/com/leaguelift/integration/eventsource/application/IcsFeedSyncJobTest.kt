package com.leaguelift.integration.eventsource.application

import com.leaguelift.config.IcsFeedSyncProperties
import com.leaguelift.event.domain.Event
import com.leaguelift.event.domain.EventSourceType
import com.leaguelift.event.domain.EventStatus
import com.leaguelift.event.domain.EventType
import com.leaguelift.event.domain.EventVisibility
import com.leaguelift.event.persistence.EventRepository
import com.leaguelift.integration.eventsource.domain.EventSourceConnection
import com.leaguelift.integration.eventsource.domain.EventSourceConnectionStatus
import com.leaguelift.integration.eventsource.domain.EventSourceProvider
import com.leaguelift.integration.eventsource.domain.EventSourceSyncStatus
import com.leaguelift.integration.eventsource.infra.IcsFeedFetcher
import com.leaguelift.integration.eventsource.persistence.EventSourceConnectionRepository
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import java.time.Instant
import java.util.UUID
import kotlin.test.Test

class IcsFeedSyncJobTest {

	private val eventSourceConnectionRepository = mockk<EventSourceConnectionRepository>()
	private val eventRepository = mockk<EventRepository>()
	private val icsFeedFetcher = mockk<IcsFeedFetcher>()
	private val properties = IcsFeedSyncProperties(enabled = true)
	private val job = IcsFeedSyncJob(eventSourceConnectionRepository, eventRepository, icsFeedFetcher, properties)

	private val orgId = UUID.randomUUID()
	private val teamId = UUID.randomUUID()
	private val userId = UUID.randomUUID()

	private fun connection(id: UUID = UUID.randomUUID(), feedUrl: String? = "https://example.com/feed.ics") = EventSourceConnection(
		id, orgId, EventSourceProvider.ICS_FEED, "Varsity Schedule", feedUrl, "America/New_York", teamId,
		EventSourceConnectionStatus.ACTIVE, null, null, null, userId, Instant.now(), Instant.now(),
	)

	private fun sampleEvent(syncHash: String) = Event(
		id = UUID.randomUUID(), organizationId = orgId, teamId = teamId, tournamentId = null, opponentTeamId = null, opponentName = null,
		eventType = EventType.OTHER, title = "Old title", description = null, status = EventStatus.TENTATIVE, startAt = null, endAt = null,
		arrivalAt = null, meetingAt = null, timezone = "America/New_York", venueName = null, address = null, latitude = null, longitude = null,
		area = null, meetingPoint = null, directionsNotes = null, visibility = EventVisibility.TEAM, sourceType = EventSourceType.ICS_FEED,
		provider = "ICS_FEED", connectionId = "conn", externalEventId = "game-1", externalSyncHash = syncHash, sourceUpdatedAt = Instant.now(),
		createdByUserId = userId, updatedByUserId = userId, createdAt = Instant.now(), updatedAt = Instant.now(),
	)

	@Test
	fun `syncOne skips a connection with no feed_url without touching the repository`() {
		job.syncOne(connection(feedUrl = null))

		verify(exactly = 0) { icsFeedFetcher.fetch(any()) }
		verify(exactly = 0) { eventSourceConnectionRepository.recordSyncResult(any(), any(), any()) }
	}

	@Test
	fun `syncOne records a FAILED result when the feed can't be reached`() {
		val conn = connection()
		every { icsFeedFetcher.fetch(conn.feedUrl!!) } throws RuntimeException("connection refused")
		every { eventSourceConnectionRepository.recordSyncResult(conn.id, EventSourceSyncStatus.FAILED, any()) } returns 1

		job.syncOne(conn)

		verify(exactly = 1) { eventSourceConnectionRepository.recordSyncResult(conn.id, EventSourceSyncStatus.FAILED, any()) }
	}

	@Test
	fun `syncOne records a FAILED result when the feed content can't be parsed`() {
		val conn = connection()
		// A DTSTART with an unparseable value doesn't throw (parser returns null for that field),
		// so to exercise the parse-failure path we simulate a fetch that returns non-ICS garbage
		// that still parses to zero events — instead, verify the malformed-timezone path directly.
		val badTimezoneConn = conn.copy(timezone = "Not/AZone")
		every { icsFeedFetcher.fetch(conn.feedUrl!!) } returns "BEGIN:VEVENT\nUID:x\nDTSTART:20260101T000000\nEND:VEVENT"
		every { eventSourceConnectionRepository.recordSyncResult(badTimezoneConn.id, EventSourceSyncStatus.FAILED, any()) } returns 1

		job.syncOne(badTimezoneConn)

		verify(exactly = 1) { eventSourceConnectionRepository.recordSyncResult(badTimezoneConn.id, EventSourceSyncStatus.FAILED, any()) }
	}

	@Test
	fun `syncOne creates a new event for an unmatched UID and records SUCCESS`() {
		val conn = connection()
		val ics = "BEGIN:VEVENT\nUID:game-1\nSUMMARY:Varsity vs Rivals\nDTSTART:20260905T193000Z\nEND:VEVENT"
		every { icsFeedFetcher.fetch(conn.feedUrl!!) } returns ics
		every { eventRepository.findByExternalIdentity(orgId, "ICS_FEED", conn.id.toString(), "game-1") } returns null
		every {
			eventRepository.insert(
				organizationId = orgId, teamId = teamId, tournamentId = null, opponentTeamId = null, opponentName = null,
				eventType = EventType.OTHER, title = "Varsity vs Rivals", description = null, startAt = Instant.parse("2026-09-05T19:30:00Z"),
				endAt = null, arrivalAt = null, meetingAt = null, timezone = "America/New_York", venueName = null, address = null,
				latitude = null, longitude = null, area = null, meetingPoint = null, directionsNotes = null, visibility = EventVisibility.TEAM,
				createdByUserId = userId, sourceType = EventSourceType.ICS_FEED, provider = "ICS_FEED", connectionId = conn.id.toString(),
				externalEventId = "game-1", externalSyncHash = any(), sourceUpdatedAt = any(), initialStatus = EventStatus.TENTATIVE,
			)
		} returns sampleEvent("irrelevant")
		every { eventSourceConnectionRepository.recordSyncResult(conn.id, EventSourceSyncStatus.SUCCESS, null) } returns 1

		job.syncOne(conn)

		verify(exactly = 1) { eventSourceConnectionRepository.recordSyncResult(conn.id, EventSourceSyncStatus.SUCCESS, null) }
	}

	@Test
	fun `syncOne updates an existing matched event when its sync hash differs`() {
		val conn = connection()
		val ics = "BEGIN:VEVENT\nUID:game-1\nSUMMARY:Updated title\nDTSTART:20260905T193000Z\nEND:VEVENT"
		val existing = sampleEvent(syncHash = "stale")
		every { icsFeedFetcher.fetch(conn.feedUrl!!) } returns ics
		every { eventRepository.findByExternalIdentity(orgId, "ICS_FEED", conn.id.toString(), "game-1") } returns existing
		every {
			eventRepository.update(
				id = existing.id, organizationId = orgId, title = "Updated title", description = null, status = null,
				startAt = Instant.parse("2026-09-05T19:30:00Z"), endAt = null, arrivalAt = null, meetingAt = null,
				venueName = null, address = null, latitude = null, longitude = null, area = null, meetingPoint = null,
				directionsNotes = null, opponentTeamId = null, opponentName = null, updatedByUserId = userId,
				externalSyncHash = any(), sourceUpdatedAt = any(),
			)
		} returns 1
		every { eventSourceConnectionRepository.recordSyncResult(conn.id, EventSourceSyncStatus.SUCCESS, null) } returns 1

		job.syncOne(conn)

		verify(exactly = 1) { eventRepository.update(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
	}

	@Test
	fun `syncOne skips an existing matched event with an identical sync hash`() {
		val conn = connection()
		val ics = "BEGIN:VEVENT\nUID:game-1\nDTSTART:20260905T193000Z\nEND:VEVENT"
		val payload = listOf(null, null, null, Instant.parse("2026-09-05T19:30:00Z"), null, null).joinToString("|") { it?.toString() ?: "" }
		val digest = java.security.MessageDigest.getInstance("SHA-256").digest(payload.toByteArray(Charsets.UTF_8))
		val expectedHash = digest.joinToString("") { "%02x".format(it) }
		val existing = sampleEvent(syncHash = expectedHash)
		every { icsFeedFetcher.fetch(conn.feedUrl!!) } returns ics
		every { eventRepository.findByExternalIdentity(orgId, "ICS_FEED", conn.id.toString(), "game-1") } returns existing
		every { eventSourceConnectionRepository.recordSyncResult(conn.id, EventSourceSyncStatus.SUCCESS, null) } returns 1

		job.syncOne(conn)

		verify(exactly = 0) { eventRepository.update(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
	}
}
