package com.rally26.integration.eventsource.application

import com.rally26.audit.application.AuditService
import com.rally26.authorization.application.AuthorizationService
import com.rally26.authorization.domain.Capabilities
import com.rally26.common.error.NotFoundException
import com.rally26.common.error.ValidationException
import com.rally26.common.util.CsvUtil
import com.rally26.common.web.CurrentUser
import com.rally26.event.domain.EventSourceType
import com.rally26.event.domain.EventStatus
import com.rally26.event.domain.EventType
import com.rally26.event.domain.EventVisibility
import com.rally26.event.persistence.EventRepository
import com.rally26.membership.application.MembershipService
import com.rally26.team.persistence.TeamRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeParseException
import java.util.UUID

private const val PROVIDER = "CSV_IMPORT"
private val REQUIRED_HEADERS = setOf("external_id", "event_type")

data class CsvImportRowError(val rowNumber: Int, val message: String)
data class CsvImportResult(val createdCount: Int, val updatedCount: Int, val unchangedCount: Int, val errors: List<CsvImportRowError>)

/**
 * CSV schedule import (Phase 12 slice 2, ADR-032) — a one-time bulk action, not an
 * ongoing connection (see ADR-031 decision 1; no [com.rally26.integration.eventsource.domain.EventSourceConnection]
 * row is ever written here). Every imported event is scoped to one team (or org-wide
 * if [teamId] is null) and one shared [timezone] supplied at upload time — per-row
 * team/timezone columns would need name-matching logic this slice deliberately
 * doesn't build.
 */
@Service
class CsvEventImportService(
	private val eventRepository: EventRepository,
	private val teamRepository: TeamRepository,
	private val authorizationService: AuthorizationService,
	private val membershipService: MembershipService,
	private val auditService: AuditService,
) {

	@Transactional
	fun import(organizationId: UUID, teamId: UUID?, timezone: String, csvContent: String, currentUser: CurrentUser): CsvImportResult {
		if (teamId != null) {
			authorizationService.requireTeamCapability(organizationId, teamId, currentUser, Capabilities.EVENT_CREATE)
			teamRepository.findById(teamId, organizationId) ?: throw NotFoundException("TEAM_NOT_FOUND", "The team could not be found.")
		} else {
			membershipService.requireManagerRole(organizationId, currentUser)
		}
		requireValidTimezone(timezone)

		val rows = CsvUtil.parse(csvContent)
		if (rows.isEmpty()) {
			throw ValidationException("The CSV file is empty.")
		}
		val header = rows.first().map { it.trim().lowercase() }
		val missing = REQUIRED_HEADERS - header.toSet()
		if (missing.isNotEmpty()) {
			throw ValidationException("Missing required column(s): ${missing.joinToString(", ")}.")
		}

		val connectionId = teamId?.toString() ?: "org:$organizationId"
		val visibility = if (teamId != null) EventVisibility.TEAM else EventVisibility.ORGANIZATION
		var created = 0
		var updated = 0
		var unchanged = 0
		val errors = mutableListOf<CsvImportRowError>()

		rows.drop(1).forEachIndexed { index, rawRow ->
			val rowNumber = index + 2
			if (rawRow.all { it.isBlank() }) return@forEachIndexed
			val fields = header.zip(rawRow).toMap()
			try {
				val (createdRow, updatedRow) = importRow(organizationId, teamId, timezone, connectionId, visibility, fields, currentUser.userId)
				if (createdRow) created++ else if (updatedRow) updated++ else unchanged++
			} catch (e: ValidationException) {
				errors.add(CsvImportRowError(rowNumber, e.message ?: "Invalid row."))
			}
		}

		auditService.record(
			currentUser.userId, organizationId, "event.csv_imported", "event",
			teamId ?: organizationId,
			"""{"created":$created,"updated":$updated,"errors":${errors.size}}""",
		)
		return CsvImportResult(created, updated, unchanged, errors)
	}

	/** Returns (wasCreated, wasUpdated) — both false means the row matched an existing event with an identical sync hash, nothing to do. */
	private fun importRow(
		organizationId: UUID,
		teamId: UUID?,
		timezone: String,
		connectionId: String,
		visibility: EventVisibility,
		fields: Map<String, String>,
		currentUserId: UUID,
	): Pair<Boolean, Boolean> {
		val externalId = fields["external_id"]?.trim().orEmpty()
		if (externalId.isBlank()) {
			throw ValidationException("external_id is required.")
		}
		val eventTypeRaw = fields["event_type"]?.trim().orEmpty()
		val eventType = EventType.entries.find { it.name.equals(eventTypeRaw, ignoreCase = true) }
			?: throw ValidationException("Unrecognized event_type \"$eventTypeRaw\" (expected one of ${EventType.entries.joinToString(", ")}).")
		val startAt = parseInstant(fields["start_at"], "start_at")
		val endAt = parseInstant(fields["end_at"], "end_at")
		val arrivalAt = parseInstant(fields["arrival_at"], "arrival_at")
		val title = blankToNull(fields["title"])
		val description = blankToNull(fields["description"])
		val opponentName = blankToNull(fields["opponent_name"])
		val venueName = blankToNull(fields["venue_name"])
		val address = blankToNull(fields["address"])
		val area = blankToNull(fields["area"])

		val syncHash = sha256Hex(
			listOf(externalId, eventType.name, title, description, startAt, endAt, arrivalAt, venueName, address, area, opponentName)
				.joinToString("|") { it?.toString() ?: "" },
		)
		val now = Instant.now()
		val existing = eventRepository.findByExternalIdentity(organizationId, PROVIDER, connectionId, externalId)

		if (existing == null) {
			eventRepository.insert(
				organizationId = organizationId, teamId = teamId, tournamentId = null, opponentTeamId = null, opponentName = opponentName,
				eventType = eventType, title = title, description = description, startAt = startAt, endAt = endAt, arrivalAt = arrivalAt,
				meetingAt = null, timezone = timezone, venueName = venueName, address = address, latitude = null, longitude = null,
				area = area, meetingPoint = null, directionsNotes = null, visibility = visibility, createdByUserId = currentUserId,
				sourceType = EventSourceType.CSV_IMPORT, provider = PROVIDER, connectionId = connectionId, externalEventId = externalId,
				externalSyncHash = syncHash, sourceUpdatedAt = now, initialStatus = EventStatus.TENTATIVE,
			)
			return true to false
		}
		if (existing.externalSyncHash == syncHash) {
			return false to false
		}
		eventRepository.update(
			id = existing.id, organizationId = organizationId, title = title, description = description, status = null,
			startAt = startAt, endAt = endAt, arrivalAt = arrivalAt, meetingAt = null, venueName = venueName, address = address,
			latitude = null, longitude = null, area = area, meetingPoint = null, directionsNotes = null, opponentTeamId = null,
			opponentName = opponentName, updatedByUserId = currentUserId, externalSyncHash = syncHash, sourceUpdatedAt = now,
		)
		return false to true
	}

	private fun blankToNull(value: String?): String? = value?.trim()?.takeIf { it.isNotBlank() }

	private fun parseInstant(value: String?, fieldName: String): Instant? {
		val trimmed = value?.trim()
		if (trimmed.isNullOrBlank()) return null
		return try {
			Instant.parse(trimmed)
		} catch (e: DateTimeParseException) {
			throw ValidationException("$fieldName must be an ISO-8601 instant (e.g. 2026-09-05T15:30:00Z), got \"$trimmed\".")
		}
	}

	private fun requireValidTimezone(timezone: String) {
		try {
			ZoneId.of(timezone)
		} catch (e: Exception) {
			throw ValidationException("Timezone must be a valid IANA time zone id (e.g. America/New_York).")
		}
	}

	private fun sha256Hex(value: String): String =
		MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
