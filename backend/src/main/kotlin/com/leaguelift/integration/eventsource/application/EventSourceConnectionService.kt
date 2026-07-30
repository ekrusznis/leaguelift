package com.leaguelift.integration.eventsource.application

import com.leaguelift.audit.application.AuditService
import com.leaguelift.common.error.NotFoundException
import com.leaguelift.common.error.ValidationException
import com.leaguelift.common.web.CurrentUser
import com.leaguelift.integration.eventsource.domain.EventSourceConnection
import com.leaguelift.integration.eventsource.domain.EventSourceProvider
import com.leaguelift.integration.eventsource.persistence.EventSourceConnectionRepository
import com.leaguelift.membership.application.MembershipService
import com.leaguelift.team.persistence.TeamRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.net.URI
import java.time.ZoneId
import java.util.UUID

/**
 * Org-connected event-source connections (Phase 12 slice 1, ADR-031) — the
 * Integrations page's ICS Feed connect/disconnect flow. Authorization matches
 * every other organization-settings action in this codebase
 * ([MembershipService.requireManagerRole]), not a new [com.leaguelift.authorization.application.AuthorizationService]
 * capability, since organization-context actions never route through that
 * service here (see ADR-031 decision 3).
 */
@Service
class EventSourceConnectionService(
	private val eventSourceConnectionRepository: EventSourceConnectionRepository,
	private val membershipService: MembershipService,
	private val auditService: AuditService,
	private val teamRepository: TeamRepository,
) {

	fun list(organizationId: UUID, currentUser: CurrentUser): List<EventSourceConnection> {
		membershipService.requireActiveMembership(organizationId, currentUser)
		return eventSourceConnectionRepository.listForOrganization(organizationId)
	}

	/** Only [EventSourceProvider.ICS_FEED] has a real connect flow this slice — MAXPREPS/GAMECHANGER have no wiring yet (disabled cards on the Integrations page). */
	@Transactional
	fun connectIcsFeed(organizationId: UUID, label: String, feedUrl: String, timezone: String, teamId: UUID?, currentUser: CurrentUser): EventSourceConnection {
		membershipService.requireManagerRole(organizationId, currentUser)
		if (label.isBlank()) {
			throw ValidationException("A label is required.", listOf(com.leaguelift.common.error.FieldError("label", "Label is required.")))
		}
		requireValidHttpUrl(feedUrl)
		requireValidTimezone(timezone)
		if (teamId != null) {
			teamRepository.findById(teamId, organizationId) ?: throw NotFoundException("TEAM_NOT_FOUND", "The team could not be found.")
		}
		val connection = eventSourceConnectionRepository.insert(organizationId, EventSourceProvider.ICS_FEED, label.trim(), feedUrl.trim(), timezone, teamId, currentUser.userId)
		auditService.record(currentUser.userId, organizationId, "event_source_connection.connected", "event_source_connection", connection.id)
		return connection
	}

	@Transactional
	fun disconnect(organizationId: UUID, connectionId: UUID, currentUser: CurrentUser) {
		membershipService.requireManagerRole(organizationId, currentUser)
		eventSourceConnectionRepository.findById(connectionId, organizationId)
			?: throw NotFoundException("EVENT_SOURCE_CONNECTION_NOT_FOUND", "The connection could not be found.")
		eventSourceConnectionRepository.disconnect(connectionId, organizationId)
		auditService.record(currentUser.userId, organizationId, "event_source_connection.disconnected", "event_source_connection", connectionId)
	}

	private fun requireValidTimezone(timezone: String) {
		try {
			ZoneId.of(timezone)
		} catch (e: Exception) {
			throw ValidationException(
				"Timezone must be a valid IANA time zone id (e.g. America/New_York).",
				listOf(com.leaguelift.common.error.FieldError("timezone", "Invalid time zone.")),
			)
		}
	}

	private fun requireValidHttpUrl(url: String) {
		val trimmed = url.trim()
		val parsed = try {
			URI(trimmed).toURL()
		} catch (e: Exception) {
			null
		}
		if (parsed == null || (parsed.protocol != "http" && parsed.protocol != "https")) {
			throw ValidationException(
				"Feed URL must be a valid http(s) URL.",
				listOf(com.leaguelift.common.error.FieldError("feedUrl", "Must be a valid http(s) URL.")),
			)
		}
	}
}
