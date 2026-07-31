package com.leaguelift.dashboard.application

import com.leaguelift.event.domain.Event
import com.leaguelift.event.domain.EventSourceType
import com.leaguelift.event.domain.EventStatus
import com.leaguelift.event.domain.EventType
import com.leaguelift.event.domain.EventVisibility
import com.leaguelift.team.domain.Team
import com.leaguelift.team.domain.TeamStatus
import com.leaguelift.team.persistence.TeamRepository
import io.mockk.every
import io.mockk.mockk
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class DashboardEventMapperTest {
	private val teamRepository = mockk<TeamRepository>()
	private val mapper = DashboardEventMapper(teamRepository)
	private val organizationId = UUID.randomUUID()

	@Test
	fun `maps the real event id and structured team names into a dashboard schedule item`() {
		val team = team("U12 Blue")
		val opponent = team("Northshore FC")
		val eventId = UUID.randomUUID()
		val event = event(
			id = eventId,
			teamId = team.id,
			opponentTeamId = opponent.id,
			startAt = Instant.parse("2026-08-15T13:00:00Z"),
		)
		every { teamRepository.findById(team.id, organizationId) } returns team
		every { teamRepository.findById(opponent.id, organizationId) } returns opponent

		val result = mapper.toScheduleItem(event, organizationId)

		assertEquals(eventId.toString(), result.id)
		assertEquals("U12 Blue vs Northshore FC", result.title)
		assertEquals("9:00 AM", result.time)
		assertEquals("Competition · Memorial Sports Complex · Field 4", result.subtitle)
	}

	@Test
	fun `upcoming hides drafts and terminal events for family dashboards`() {
		val future = Instant.parse("2026-08-15T13:00:00Z")
		val events = listOf(
			event(status = EventStatus.SCHEDULED, startAt = future),
			event(status = EventStatus.DRAFT, startAt = future),
			event(status = EventStatus.CANCELLED, startAt = future),
		)

		val result = mapper.upcoming(events, now = Instant.parse("2026-08-01T00:00:00Z"))

		assertEquals(1, result.size)
		assertEquals(EventStatus.SCHEDULED, result.single().status)
	}

	private fun team(name: String) = Team(
		id = UUID.randomUUID(),
		organizationId = organizationId,
		name = name,
		sport = "Soccer",
		season = "2026",
		status = TeamStatus.ACTIVE,
		contactEmail = null,
		createdAt = Instant.EPOCH,
		updatedAt = Instant.EPOCH,
	)

	private fun event(
		id: UUID = UUID.randomUUID(),
		teamId: UUID? = null,
		opponentTeamId: UUID? = null,
		status: EventStatus = EventStatus.SCHEDULED,
		startAt: Instant? = null,
	) = Event(
		id = id,
		organizationId = organizationId,
		teamId = teamId,
		tournamentId = null,
		opponentTeamId = opponentTeamId,
		opponentName = null,
		eventType = EventType.COMPETITION,
		title = null,
		description = null,
		status = status,
		startAt = startAt,
		endAt = null,
		arrivalAt = null,
		meetingAt = null,
		timezone = "America/New_York",
		venueName = "Memorial Sports Complex",
		address = null,
		latitude = null,
		longitude = null,
		area = "Field 4",
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
		createdAt = Instant.EPOCH,
		updatedAt = Instant.EPOCH,
	)
}
