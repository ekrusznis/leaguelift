package com.leaguelift.integration.eventsource.web

import com.leaguelift.common.web.CurrentUser
import com.leaguelift.integration.eventsource.application.EventSourceConnectionService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/** The Integrations page's org-connected connections (Phase 12 slice 1, ADR-031) — ICS Feed is the only real connect flow this slice. */
@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/event-source-connections")
class EventSourceConnectionController(
	private val eventSourceConnectionService: EventSourceConnectionService,
) {

	@GetMapping
	fun list(
		@PathVariable organizationId: UUID,
		@AuthenticationPrincipal currentUser: CurrentUser,
	): List<EventSourceConnectionResponse> =
		eventSourceConnectionService.list(organizationId, currentUser).map { it.toResponse() }

	@PostMapping("/ics-feed")
	@ResponseStatus(HttpStatus.CREATED)
	fun connectIcsFeed(
		@PathVariable organizationId: UUID,
		@Valid @RequestBody request: ConnectIcsFeedRequest,
		@AuthenticationPrincipal currentUser: CurrentUser,
	): EventSourceConnectionResponse =
		eventSourceConnectionService.connectIcsFeed(organizationId, request.label, request.feedUrl, request.timezone, request.teamId, currentUser).toResponse()

	@DeleteMapping("/{connectionId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	fun disconnect(
		@PathVariable organizationId: UUID,
		@PathVariable connectionId: UUID,
		@AuthenticationPrincipal currentUser: CurrentUser,
	) {
		eventSourceConnectionService.disconnect(organizationId, connectionId, currentUser)
	}
}
