package com.rally26.event.web

import com.rally26.event.domain.Event
import com.rally26.event.domain.EventStatus
import com.rally26.event.domain.EventType
import com.rally26.event.domain.EventVisibility
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

data class CreateEventRequest(
	val teamId: UUID? = null,
	val tournamentId: UUID? = null,
	val opponentTeamId: UUID? = null,
	@field:Size(max = 120) val opponentName: String? = null,
	@field:NotNull val eventType: EventType,
	@field:Size(max = 200) val title: String? = null,
	@field:Size(max = 2000) val description: String? = null,
	val startAt: Instant? = null,
	val endAt: Instant? = null,
	val arrivalAt: Instant? = null,
	val meetingAt: Instant? = null,
	@field:NotNull val timezone: String,
	@field:Size(max = 200) val venueName: String? = null,
	@field:Size(max = 300) val address: String? = null,
	val latitude: Double? = null,
	val longitude: Double? = null,
	@field:Size(max = 120) val area: String? = null,
	@field:Size(max = 300) val meetingPoint: String? = null,
	@field:Size(max = 1000) val directionsNotes: String? = null,
	val visibility: EventVisibility = EventVisibility.TEAM,
)

data class UpdateEventRequest(
	@field:Size(max = 200) val title: String? = null,
	@field:Size(max = 2000) val description: String? = null,
	val status: EventStatus? = null,
	val startAt: Instant? = null,
	val endAt: Instant? = null,
	val arrivalAt: Instant? = null,
	val meetingAt: Instant? = null,
	@field:Size(max = 200) val venueName: String? = null,
	@field:Size(max = 300) val address: String? = null,
	val latitude: Double? = null,
	val longitude: Double? = null,
	@field:Size(max = 120) val area: String? = null,
	@field:Size(max = 300) val meetingPoint: String? = null,
	@field:Size(max = 1000) val directionsNotes: String? = null,
	val opponentTeamId: UUID? = null,
	@field:Size(max = 120) val opponentName: String? = null,
)

data class EventResponse(
	val id: UUID,
	val organizationId: UUID,
	val teamId: UUID?,
	val tournamentId: UUID?,
	val opponentTeamId: UUID?,
	val opponentName: String?,
	val eventType: String,
	val displayTitle: String,
	val description: String?,
	val status: String,
	val startAt: Instant?,
	val endAt: Instant?,
	val arrivalAt: Instant?,
	val meetingAt: Instant?,
	val timezone: String,
	val venueName: String?,
	val address: String?,
	val latitude: Double?,
	val longitude: Double?,
	val area: String?,
	val meetingPoint: String?,
	/** Never present on a public-facing view — only ever populated for an authorized (staff/guardian/athlete) caller. */
	val directionsNotes: String?,
	val visibility: String,
	val sourceType: String,
	val createdAt: Instant,
	val updatedAt: Instant,
)

data class DirectionsResponse(val url: String?)

fun Event.toResponse(displayTitle: String): EventResponse = EventResponse(
	id, organizationId, teamId, tournamentId, opponentTeamId, opponentName, eventType.name, displayTitle, description,
	status.name, startAt, endAt, arrivalAt, meetingAt, timezone, venueName, address, latitude, longitude, area,
	meetingPoint, directionsNotes, visibility.name, sourceType.name, createdAt, updatedAt,
)
