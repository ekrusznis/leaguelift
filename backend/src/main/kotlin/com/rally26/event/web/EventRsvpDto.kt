package com.rally26.event.web

import com.rally26.event.application.EventRsvpsResult
import com.rally26.event.domain.EventRsvp
import com.rally26.event.domain.RsvpResponse
import com.rally26.event.domain.RsvpSummary
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

data class SubmitRsvpRequest(
    @field:NotNull val response: RsvpResponse,
    @field:Size(max = 500) val note: String? = null,
)

data class EventRsvpResponse(
    val id: UUID,
    val eventId: UUID,
    val participantId: UUID,
    /** Null for the guardian/athlete summary-only view, where [EventRsvpsResult.responses] is always empty anyway. */
    val participantName: String?,
    val response: String,
    val note: String?,
    val source: String,
    val createdAt: Instant,
    val updatedAt: Instant,
)

fun EventRsvp.toResponse(participantName: String? = null) =
    EventRsvpResponse(id, eventId, participantId, participantName, response.name, note, source.name, createdAt, updatedAt)

data class RsvpSummaryResponse(
    val attending: Long,
    val notAttending: Long,
    val maybe: Long,
    val noResponse: Long,
)

fun RsvpSummary.toResponse() = RsvpSummaryResponse(attending, notAttending, maybe, noResponse)

data class EventRsvpsResponse(
    val summary: RsvpSummaryResponse,
    val responses: List<EventRsvpResponse>,
)

fun EventRsvpsResult.toResponse() = EventRsvpsResponse(summary.toResponse(), responses.map { it.toResponse(participantNames[it.participantId]) })
