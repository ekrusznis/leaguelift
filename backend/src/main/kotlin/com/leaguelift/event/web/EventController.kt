package com.leaguelift.event.web

import com.leaguelift.common.web.CurrentUser
import com.leaguelift.event.application.EventService
import com.leaguelift.event.domain.Event
import jakarta.validation.Valid
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/** Event CRUD (Phase 10 slice 1, ADR-026); calendar/directions (slice 3, ADR-028) — staff-facing endpoints only; RSVP and guardian/athlete read access arrive in slice 2 (ADR-027). */
@RestController
@RequestMapping("/api/v1")
class EventController(
	private val eventService: EventService,
) {

	@PostMapping("/organizations/{organizationId}/events")
	fun create(
		@PathVariable organizationId: UUID,
		@Valid @RequestBody request: CreateEventRequest,
		@AuthenticationPrincipal currentUser: CurrentUser,
	): EventResponse {
		val event = eventService.create(
			organizationId, request.teamId, request.tournamentId, request.opponentTeamId, request.opponentName,
			request.eventType, request.title, request.description, request.startAt, request.endAt, request.arrivalAt,
			request.meetingAt, request.timezone, request.venueName, request.address, request.latitude, request.longitude,
			request.area, request.meetingPoint, request.directionsNotes, request.visibility, currentUser,
		)
		return event.toResponseWithNames(organizationId)
	}

	@GetMapping("/organizations/{organizationId}/events")
	fun listForOrganization(
		@PathVariable organizationId: UUID,
		@RequestParam(defaultValue = "0") page: Int,
		@RequestParam(defaultValue = "20") size: Int,
		@AuthenticationPrincipal currentUser: CurrentUser,
	): List<EventResponse> =
		eventService.listForOrganization(organizationId, currentUser, page * size, size).map { it.toResponseWithNames(organizationId) }

	@GetMapping("/organizations/{organizationId}/events/{eventId}")
	fun get(
		@PathVariable organizationId: UUID,
		@PathVariable eventId: UUID,
		@AuthenticationPrincipal currentUser: CurrentUser,
	): EventResponse = eventService.get(organizationId, eventId, currentUser).toResponseWithNames(organizationId)

	@PatchMapping("/organizations/{organizationId}/events/{eventId}")
	fun update(
		@PathVariable organizationId: UUID,
		@PathVariable eventId: UUID,
		@Valid @RequestBody request: UpdateEventRequest,
		@AuthenticationPrincipal currentUser: CurrentUser,
	): EventResponse {
		val event = eventService.update(
			organizationId, eventId, request.title, request.description, request.status, request.startAt, request.endAt,
			request.arrivalAt, request.meetingAt, request.venueName, request.address, request.latitude, request.longitude,
			request.area, request.meetingPoint, request.directionsNotes, request.opponentTeamId, request.opponentName, currentUser,
		)
		return event.toResponseWithNames(organizationId)
	}

	@PostMapping("/organizations/{organizationId}/events/{eventId}/publish")
	fun publish(
		@PathVariable organizationId: UUID,
		@PathVariable eventId: UUID,
		@AuthenticationPrincipal currentUser: CurrentUser,
	): EventResponse = eventService.publish(organizationId, eventId, currentUser).toResponseWithNames(organizationId)

	@PostMapping("/organizations/{organizationId}/events/{eventId}/cancel")
	fun cancel(
		@PathVariable organizationId: UUID,
		@PathVariable eventId: UUID,
		@AuthenticationPrincipal currentUser: CurrentUser,
	): EventResponse = eventService.cancel(organizationId, eventId, currentUser).toResponseWithNames(organizationId)

	@PostMapping("/organizations/{organizationId}/events/{eventId}/postpone")
	fun postpone(
		@PathVariable organizationId: UUID,
		@PathVariable eventId: UUID,
		@AuthenticationPrincipal currentUser: CurrentUser,
	): EventResponse = eventService.postpone(organizationId, eventId, currentUser).toResponseWithNames(organizationId)

	@PostMapping("/organizations/{organizationId}/events/{eventId}/detach-from-source")
	fun detachFromSource(
		@PathVariable organizationId: UUID,
		@PathVariable eventId: UUID,
		@AuthenticationPrincipal currentUser: CurrentUser,
	): EventResponse = eventService.detachFromSource(organizationId, eventId, currentUser).toResponseWithNames(organizationId)

	@GetMapping("/teams/{teamId}/events")
	fun listForTeam(
		@PathVariable teamId: UUID,
		@RequestParam organizationId: UUID,
		@RequestParam(defaultValue = "0") page: Int,
		@RequestParam(defaultValue = "20") size: Int,
		@AuthenticationPrincipal currentUser: CurrentUser,
	): List<EventResponse> =
		eventService.listForTeam(organizationId, teamId, currentUser, page * size, size).map { it.toResponseWithNames(organizationId) }

	@PostMapping("/teams/{teamId}/events")
	fun createForTeam(
		@PathVariable teamId: UUID,
		@RequestParam organizationId: UUID,
		@Valid @RequestBody request: CreateEventRequest,
		@AuthenticationPrincipal currentUser: CurrentUser,
	): EventResponse {
		val event = eventService.create(
			organizationId, teamId, request.tournamentId, request.opponentTeamId, request.opponentName,
			request.eventType, request.title, request.description, request.startAt, request.endAt, request.arrivalAt,
			request.meetingAt, request.timezone, request.venueName, request.address, request.latitude, request.longitude,
			request.area, request.meetingPoint, request.directionsNotes, request.visibility, currentUser,
		)
		return event.toResponseWithNames(organizationId)
	}

	@GetMapping("/tournaments/{tournamentId}/events")
	fun listForTournament(
		@PathVariable tournamentId: UUID,
		@RequestParam organizationId: UUID,
		@RequestParam(defaultValue = "0") page: Int,
		@RequestParam(defaultValue = "20") size: Int,
		@AuthenticationPrincipal currentUser: CurrentUser,
	): List<EventResponse> =
		eventService.listForTournament(organizationId, tournamentId, currentUser, page * size, size).map { it.toResponseWithNames(organizationId) }

	@PostMapping("/tournaments/{tournamentId}/events")
	fun createForTournament(
		@PathVariable tournamentId: UUID,
		@RequestParam organizationId: UUID,
		@Valid @RequestBody request: CreateEventRequest,
		@AuthenticationPrincipal currentUser: CurrentUser,
	): EventResponse {
		val event = eventService.create(
			organizationId, request.teamId, tournamentId, request.opponentTeamId, request.opponentName,
			request.eventType, request.title, request.description, request.startAt, request.endAt, request.arrivalAt,
			request.meetingAt, request.timezone, request.venueName, request.address, request.latitude, request.longitude,
			request.area, request.meetingPoint, request.directionsNotes, request.visibility, currentUser,
		)
		return event.toResponseWithNames(organizationId)
	}

	@GetMapping("/households/{householdId}/events")
	fun listForHousehold(
		@PathVariable householdId: UUID,
		@RequestParam organizationId: UUID,
		@RequestParam(defaultValue = "0") page: Int,
		@RequestParam(defaultValue = "20") size: Int,
		@AuthenticationPrincipal currentUser: CurrentUser,
	): List<EventResponse> =
		eventService.listForHousehold(organizationId, householdId, currentUser, page * size, size).map { it.toResponseWithNames(organizationId) }

	@GetMapping("/participants/{participantId}/events")
	fun listForParticipant(
		@PathVariable participantId: UUID,
		@RequestParam organizationId: UUID,
		@RequestParam(defaultValue = "0") page: Int,
		@RequestParam(defaultValue = "20") size: Int,
		@AuthenticationPrincipal currentUser: CurrentUser,
	): List<EventResponse> =
		eventService.listForParticipant(organizationId, participantId, currentUser, page * size, size).map { it.toResponseWithNames(organizationId) }

	@GetMapping("/organizations/{organizationId}/events/{eventId}/calendar.ics", produces = ["text/calendar"])
	fun eventIcs(
		@PathVariable organizationId: UUID,
		@PathVariable eventId: UUID,
		@AuthenticationPrincipal currentUser: CurrentUser,
	): ResponseEntity<String> = icsResponse(eventService.getIcsForEvent(organizationId, eventId, currentUser), "event-$eventId.ics")

	@GetMapping("/organizations/{organizationId}/events/{eventId}/directions")
	fun directions(
		@PathVariable organizationId: UUID,
		@PathVariable eventId: UUID,
		@AuthenticationPrincipal currentUser: CurrentUser,
	): DirectionsResponse = DirectionsResponse(eventService.getDirections(organizationId, eventId, currentUser))

	@GetMapping("/households/{householdId}/calendar.ics", produces = ["text/calendar"])
	fun householdIcs(
		@PathVariable householdId: UUID,
		@RequestParam organizationId: UUID,
		@AuthenticationPrincipal currentUser: CurrentUser,
	): ResponseEntity<String> = icsResponse(eventService.getIcsForHousehold(organizationId, householdId, currentUser), "household-$householdId.ics")

	private fun icsResponse(ics: String, filename: String): ResponseEntity<String> =
		ResponseEntity.ok()
			.contentType(MediaType.parseMediaType("text/calendar"))
			.header("Content-Disposition", "attachment; filename=\"$filename\"")
			.body(ics)

	private fun Event.toResponseWithNames(organizationId: UUID): EventResponse =
		toResponse(eventService.displayTitleFor(this, organizationId))
}
