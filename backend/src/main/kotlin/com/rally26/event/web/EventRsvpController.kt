package com.rally26.event.web

import com.rally26.common.web.CurrentUser
import com.rally26.event.application.EventRsvpService
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/** RSVP (Phase 10 slice 2, ADR-027). */
@RestController
@RequestMapping("/api/v1/events/{eventId}")
class EventRsvpController(private val eventRsvpService: EventRsvpService) {

	@GetMapping("/rsvps")
	fun getRsvps(
		@PathVariable eventId: UUID,
		@RequestParam organizationId: UUID,
		@AuthenticationPrincipal currentUser: CurrentUser,
	): EventRsvpsResponse = eventRsvpService.getRsvps(organizationId, eventId, currentUser).toResponse()

	@PutMapping("/participants/{participantId}/rsvp")
	fun submitRsvp(
		@PathVariable eventId: UUID,
		@PathVariable participantId: UUID,
		@RequestParam organizationId: UUID,
		@Valid @RequestBody request: SubmitRsvpRequest,
		@AuthenticationPrincipal currentUser: CurrentUser,
	): EventRsvpResponse =
		eventRsvpService.submit(organizationId, eventId, participantId, request.response, request.note, currentUser).toResponse()
}
