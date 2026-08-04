package com.rally26.event.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.rally26.audit.application.AuditService
import com.rally26.authorization.application.AuthorizationService
import com.rally26.authorization.domain.Capabilities
import com.rally26.common.error.ForbiddenException
import com.rally26.common.error.NotFoundException
import com.rally26.common.error.ValidationException
import com.rally26.common.web.CurrentUser
import com.rally26.event.domain.Event
import com.rally26.event.domain.EventStatus
import com.rally26.event.domain.EventType
import com.rally26.event.domain.EventVisibility
import com.rally26.event.domain.displayTitle
import com.rally26.event.persistence.EventRepository
import com.rally26.household.persistence.HouseholdRepository
import com.rally26.membership.application.MembershipService
import com.rally26.outbox.application.OutboxWriter
import com.rally26.participant.persistence.ParticipantRepository
import com.rally26.team.persistence.TeamRepository
import com.rally26.tournament.persistence.TournamentRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

private const val ICS_SCHEDULE_LIMIT = 200

/**
 * Event CRUD (Phase 10 slice 1, ADR-026) plus the guardian/athlete "combined schedule"
 * read paths (slice 2, ADR-027) — RSVP submission itself lives in [EventRsvpService].
 * An event's manage-access scope is derived from which of [Event.teamId]/
 * [Event.tournamentId] it has (mutually exclusive with "org-wide," never both check
 * paths applied) — set once at creation and not reassignable via [update] (changing an
 * event's ownership isn't a v1 need).
 */
@Service
class EventService(
	private val eventRepository: EventRepository,
	private val teamRepository: TeamRepository,
	private val tournamentRepository: TournamentRepository,
	private val householdRepository: HouseholdRepository,
	private val participantRepository: ParticipantRepository,
	private val membershipService: MembershipService,
	private val authorizationService: AuthorizationService,
	private val auditService: AuditService,
	private val calendarProvider: CalendarProvider,
	private val mapsProvider: MapsProvider,
	private val outboxWriter: OutboxWriter,
	private val objectMapper: ObjectMapper,
) {

	/** Shared with [com.rally26.event.web.EventController]'s response mapping so calendar/directions and the JSON API compute the exact same title. */
	fun displayTitleFor(event: Event, organizationId: UUID): String {
		val teamName = event.teamId?.let { teamRepository.findById(it, organizationId)?.name }
		val opponentTeamName = event.opponentTeamId?.let { teamRepository.findById(it, organizationId)?.name }
		return displayTitle(event, teamName, opponentTeamName)
	}

	/** A single event's `.ics` file (Phase 10 slice 3, ADR-028) — same read authorization as [get]. */
	fun getIcsForEvent(organizationId: UUID, eventId: UUID, currentUser: CurrentUser): String {
		val event = get(organizationId, eventId, currentUser)
		return calendarProvider.buildIcs(event, displayTitleFor(event, organizationId))
	}

	/** A household's combined-schedule `.ics` file — same read authorization as [listForHousehold]. */
	fun getIcsForHousehold(organizationId: UUID, householdId: UUID, currentUser: CurrentUser): String {
		val events = listForHousehold(organizationId, householdId, currentUser, 0, ICS_SCHEDULE_LIMIT)
		return calendarProvider.buildIcs(events.map { EventWithDisplayTitle(it, displayTitleFor(it, organizationId)) })
	}

	/** A directions link built only from [Event.address]/[Event.latitude]/[Event.longitude] — never [Event.meetingPoint]/[Event.directionsNotes], which stay private. Same read authorization as [get]; null if the event has no location at all. */
	fun getDirections(organizationId: UUID, eventId: UUID, currentUser: CurrentUser): String? {
		val event = get(organizationId, eventId, currentUser)
		return mapsProvider.buildDirectionsUrl(event.address, event.latitude, event.longitude)
	}

	@Transactional
	fun create(
		organizationId: UUID,
		teamId: UUID?,
		tournamentId: UUID?,
		opponentTeamId: UUID?,
		opponentName: String?,
		eventType: EventType,
		title: String?,
		description: String?,
		startAt: Instant?,
		endAt: Instant?,
		arrivalAt: Instant?,
		meetingAt: Instant?,
		timezone: String,
		venueName: String?,
		address: String?,
		latitude: Double?,
		longitude: Double?,
		area: String?,
		meetingPoint: String?,
		directionsNotes: String?,
		visibility: EventVisibility,
		currentUser: CurrentUser,
	): Event {
		requireValidTimezone(timezone)
		if (opponentTeamId != null && teamId != null && opponentTeamId == teamId) {
			throw ValidationException("An event's opponent team cannot be the same as its owning team.")
		}
		requireCreateAccess(organizationId, teamId, tournamentId, currentUser)
		if (teamId != null) teamRepository.findById(teamId, organizationId) ?: throw NotFoundException("TEAM_NOT_FOUND", "The team could not be found.")
		if (tournamentId != null) tournamentRepository.findById(tournamentId, organizationId) ?: throw NotFoundException("TOURNAMENT_NOT_FOUND", "The tournament could not be found.")
		if (opponentTeamId != null) teamRepository.findById(opponentTeamId, organizationId) ?: throw NotFoundException("TEAM_NOT_FOUND", "The opponent team could not be found.")

		val event = eventRepository.insert(
			organizationId, teamId, tournamentId, opponentTeamId, opponentName, eventType, title, description,
			startAt, endAt, arrivalAt, meetingAt, timezone, venueName, address, latitude, longitude, area,
			meetingPoint, directionsNotes, visibility, currentUser.userId,
		)
		auditService.record(currentUser.userId, organizationId, "event.created", "event", event.id)
		return event
	}

	fun get(organizationId: UUID, eventId: UUID, currentUser: CurrentUser): Event {
		val event = requireOwnedEvent(organizationId, eventId)
		requireReadAccess(event, currentUser)
		return event
	}

	fun listForOrganization(organizationId: UUID, currentUser: CurrentUser, offset: Int, limit: Int): List<Event> {
		membershipService.requireActiveMembership(organizationId, currentUser)
		return eventRepository.findByOrganization(organizationId, offset, limit)
	}

	fun listForTeam(organizationId: UUID, teamId: UUID, currentUser: CurrentUser, offset: Int, limit: Int): List<Event> {
		authorizationService.requireTeamCapability(organizationId, teamId, currentUser, Capabilities.EVENT_READ)
		return eventRepository.findByTeam(teamId, organizationId, offset, limit)
	}

	fun listForTournament(organizationId: UUID, tournamentId: UUID, currentUser: CurrentUser, offset: Int, limit: Int): List<Event> {
		authorizationService.requireTournamentCapability(organizationId, tournamentId, currentUser, Capabilities.EVENT_READ)
		return eventRepository.findByTournament(tournamentId, organizationId, offset, limit)
	}

	/**
	 * A household's combined schedule (acceptance criterion #4) — every event owned by
	 * any team any of the household's active participants is on. Read access matches
	 * [com.rally26.dashboard.application.ParentDashboardService]'s existing
	 * household-view bar (any active org member, or a real guardian relationship) —
	 * viewing a schedule isn't the impersonation risk RSVP submission is, so the
	 * broader [AuthorizationService.hasHouseholdCapability] check is appropriate here,
	 * unlike [EventRsvpService.submit]'s stricter guardian-only check.
	 */
	fun listForHousehold(organizationId: UUID, householdId: UUID, currentUser: CurrentUser, offset: Int, limit: Int): List<Event> {
		householdRepository.findById(householdId, organizationId) ?: throw NotFoundException("HOUSEHOLD_NOT_FOUND", "The household could not be found.")
		if (!authorizationService.hasHouseholdCapability(organizationId, householdId, currentUser, Capabilities.EVENT_READ)) {
			throw ForbiddenException("CAPABILITY_DENIED", "You do not have access to this household's schedule.")
		}
		val teamIds = participantRepository.findByHousehold(householdId, organizationId)
			.flatMap { participantRepository.listTeamAssignments(it.id, organizationId) }
			.map { it.teamId }
			.toSet()
		return eventRepository.findByTeams(teamIds, organizationId, offset, limit)
			.filter { it.status != EventStatus.DRAFT }
	}

	/** A single participant's schedule (acceptance criterion #3) — every event owned by any team the participant is on. */
	fun listForParticipant(organizationId: UUID, participantId: UUID, currentUser: CurrentUser, offset: Int, limit: Int): List<Event> {
		val participant = participantRepository.findById(participantId, organizationId)
			?: throw NotFoundException("PARTICIPANT_NOT_FOUND", "The participant could not be found.")
		val isSelf = authorizationService.hasParticipantCapability(currentUser, participantId, Capabilities.ATHLETE_SCHEDULE_VIEW)
		val isAuthorized = isSelf || authorizationService.hasHouseholdCapability(organizationId, participant.householdId, currentUser, Capabilities.EVENT_READ)
		if (!isAuthorized) {
			throw ForbiddenException("CAPABILITY_DENIED", "You do not have access to this participant's schedule.")
		}
		val teamIds = participantRepository.listTeamAssignments(participantId, organizationId).map { it.teamId }.toSet()
		return eventRepository.findByTeams(teamIds, organizationId, offset, limit)
			.filter { it.status != EventStatus.DRAFT }
	}

	@Transactional
	fun update(
		organizationId: UUID,
		eventId: UUID,
		title: String?,
		description: String?,
		status: EventStatus?,
		startAt: Instant?,
		endAt: Instant?,
		arrivalAt: Instant?,
		meetingAt: Instant?,
		venueName: String?,
		address: String?,
		latitude: Double?,
		longitude: Double?,
		area: String?,
		meetingPoint: String?,
		directionsNotes: String?,
		opponentTeamId: UUID?,
		opponentName: String?,
		currentUser: CurrentUser,
	): Event {
		val event = requireOwnedEvent(organizationId, eventId)
		requireManageAccess(event, currentUser, Capabilities.EVENT_UPDATE)
		if (event.provider != null) {
			requireNoSourceOwnedFieldChange(status, startAt, endAt, venueName, address, latitude, longitude, opponentTeamId, opponentName)
		}
		if (opponentTeamId != null) {
			if (opponentTeamId == event.teamId) throw ValidationException("An event's opponent team cannot be the same as its owning team.")
			teamRepository.findById(opponentTeamId, organizationId) ?: throw NotFoundException("TEAM_NOT_FOUND", "The opponent team could not be found.")
		}
		eventRepository.update(
			eventId, organizationId, title, description, status, startAt, endAt, arrivalAt, meetingAt,
			venueName, address, latitude, longitude, area, meetingPoint, directionsNotes, opponentTeamId, opponentName,
			currentUser.userId,
		)
		auditService.record(currentUser.userId, organizationId, "event.updated", "event", eventId)
		val updated = eventRepository.findById(eventId, organizationId)!!
		if (event.status != EventStatus.DRAFT) notifyMeaningfulChanges(event, updated)
		return updated
	}

	@Transactional
	fun publish(organizationId: UUID, eventId: UUID, currentUser: CurrentUser): Event {
		val event = requireOwnedEvent(organizationId, eventId)
		requireManageAccess(event, currentUser, Capabilities.EVENT_PUBLISH)
		if (event.status != EventStatus.DRAFT) {
			throw ValidationException("Only a DRAFT event can be published.")
		}
		eventRepository.updateStatus(eventId, organizationId, EventStatus.TENTATIVE, currentUser.userId)
		auditService.record(currentUser.userId, organizationId, "event.published", "event", eventId)
		val published = eventRepository.findById(eventId, organizationId)!!
		val title = displayTitleFor(published, organizationId)
		val notificationType = if (published.tournamentId != null) "tournament.event_added" else "event.created"
		writeChangeNotification(published, notificationType, "\"$title\" has been added to the schedule.")
		return published
	}

	@Transactional
	fun cancel(organizationId: UUID, eventId: UUID, currentUser: CurrentUser): Event {
		val event = requireOwnedEvent(organizationId, eventId)
		requireManageAccess(event, currentUser, Capabilities.EVENT_CANCEL)
		if (event.status == EventStatus.CANCELLED || event.status == EventStatus.COMPLETED) {
			throw ValidationException("This event can no longer be cancelled.")
		}
		eventRepository.updateStatus(eventId, organizationId, EventStatus.CANCELLED, currentUser.userId)
		auditService.record(currentUser.userId, organizationId, "event.cancelled", "event", eventId)
		val cancelled = eventRepository.findById(eventId, organizationId)!!
		if (event.status != EventStatus.DRAFT) {
			writeChangeNotification(cancelled, "event.cancelled", "\"${displayTitleFor(cancelled, organizationId)}\" has been cancelled.")
		}
		return cancelled
	}

	@Transactional
	fun postpone(organizationId: UUID, eventId: UUID, currentUser: CurrentUser): Event {
		val event = requireOwnedEvent(organizationId, eventId)
		requireManageAccess(event, currentUser, Capabilities.EVENT_CANCEL)
		if (event.status == EventStatus.CANCELLED || event.status == EventStatus.COMPLETED) {
			throw ValidationException("This event can no longer be postponed.")
		}
		eventRepository.updateStatus(eventId, organizationId, EventStatus.POSTPONED, currentUser.userId)
		auditService.record(currentUser.userId, organizationId, "event.postponed", "event", eventId)
		val postponed = eventRepository.findById(eventId, organizationId)!!
		if (event.status != EventStatus.DRAFT) {
			writeChangeNotification(postponed, "event.postponed", "\"${displayTitleFor(postponed, organizationId)}\" has been postponed.")
		}
		return postponed
	}

	/**
	 * Converts an imported event back into a normal MANUAL one (Phase 12 slice 4,
	 * ADR-034) — the "detach from source" option section 14.1A requires before a
	 * source-owned field can be edited through [update]'s normal staff path.
	 * Overlay fields never needed this — they've always been freely editable.
	 */
	@Transactional
	fun detachFromSource(organizationId: UUID, eventId: UUID, currentUser: CurrentUser): Event {
		val event = requireOwnedEvent(organizationId, eventId)
		requireManageAccess(event, currentUser, Capabilities.EVENT_UPDATE)
		if (event.provider == null) {
			throw ValidationException("This event isn't imported from an external source.")
		}
		eventRepository.detachFromSource(eventId, organizationId, currentUser.userId)
		auditService.record(currentUser.userId, organizationId, "event.detached_from_source", "event", eventId)
		return eventRepository.findById(eventId, organizationId)!!
	}

	// --- Notification wiring (Phase 10 slice 4, ADR-029) ---

	/**
	 * Diffs the recipient-visible fields section 14.1A's notification list cares about
	 * and writes one outbox event per changed dimension — a single update touching both
	 * time and location fires two distinct, separately-worded notifications. A
	 * tournament child event ([Event.tournamentId] set) collapses every dimension into
	 * one `tournament.event_updated` notification instead, since its granular-per-field
	 * types have no team roster to resolve family recipients from in the common
	 * tournament-wide case (no `tournament_team` roster table exists — same gap ADR-026
	 * already documented as a non-blocker).
	 */
	private fun notifyMeaningfulChanges(before: Event, after: Event) {
		val title = displayTitleFor(after, after.organizationId)
		val timeChanged = before.startAt != after.startAt || before.endAt != after.endAt
		val locationChanged = before.venueName != after.venueName || before.address != after.address ||
			before.latitude != after.latitude || before.longitude != after.longitude
		val areaChanged = before.area != after.area
		val arrivalChanged = before.arrivalAt != after.arrivalAt

		if (after.tournamentId != null) {
			if (timeChanged || locationChanged || areaChanged || arrivalChanged) {
				writeChangeNotification(after, "tournament.event_updated", "The schedule for \"$title\" has changed.")
			}
			return
		}
		if (timeChanged) writeChangeNotification(after, "event.time_changed", "The time for \"$title\" has changed.")
		if (locationChanged) writeChangeNotification(after, "event.location_changed", "The location for \"$title\" has changed.")
		if (areaChanged) writeChangeNotification(after, "event.area_assigned", "The field/court/rink for \"$title\" has been assigned.")
		if (arrivalChanged) writeChangeNotification(after, "event.arrival_time_changed", "The arrival time for \"$title\" has changed.")
	}

	private fun writeChangeNotification(event: Event, eventType: String, changeSummary: String) {
		outboxWriter.write(
			aggregateType = "event",
			aggregateId = event.id,
			organizationId = event.organizationId,
			eventType = eventType,
			payloadJson = objectMapper.writeValueAsString(
				EventChangeNotificationPayload(event.id, displayTitleFor(event, event.organizationId), changeSummary, resolveFamilyRecipients(event)),
			),
		)
	}

	/**
	 * Households with an active participant on [Event.teamId]'s roster, filtered to
	 * whichever channel(s) each household hasn't opted out of — the same
	 * opt-out/opt-in fields Phase 8's fee-payment reminders already respect (ADR-023/
	 * ADR-024), since a schedule-change alert is a recurring-style notice, not a
	 * one-time transactional confirmation. Org-wide events ([Event.teamId] null) have no
	 * roster to resolve and get no family recipients — only the staff-facing audit
	 * event, not a household email/SMS.
	 */
	private fun resolveFamilyRecipients(event: Event): List<EventRecipientContact> =
		event.teamId?.let { teamId ->
			householdRepository.findActiveForTeam(teamId, event.organizationId).mapNotNull { household ->
				val email = household.contactEmail?.takeIf { !household.emailRemindersOptOut }
				val phone = household.contactPhone?.takeIf { household.smsRemindersOptIn }
				if (email == null && phone == null) null else EventRecipientContact(email, phone)
			}
		} ?: emptyList()

	// --- Internal helpers ---

	private fun requireOwnedEvent(organizationId: UUID, eventId: UUID): Event =
		eventRepository.findById(eventId, organizationId) ?: throw NotFoundException("EVENT_NOT_FOUND", "The event could not be found.")

	private fun requireCreateAccess(organizationId: UUID, teamId: UUID?, tournamentId: UUID?, currentUser: CurrentUser) {
		when {
			teamId != null -> authorizationService.requireTeamCapability(organizationId, teamId, currentUser, Capabilities.EVENT_CREATE)
			tournamentId != null -> authorizationService.requireTournamentCapability(organizationId, tournamentId, currentUser, Capabilities.EVENT_CREATE)
			else -> membershipService.requireManagerRole(organizationId, currentUser)
		}
	}

	/** Every mutating action (update/publish/cancel/postpone) shares this scope resolution — only the capability string checked at team/tournament scope differs by action. */
	private fun requireManageAccess(event: Event, currentUser: CurrentUser, capability: String) {
		when {
			event.teamId != null -> authorizationService.requireTeamCapability(event.organizationId, event.teamId, currentUser, capability)
			event.tournamentId != null -> authorizationService.requireTournamentCapability(event.organizationId, event.tournamentId, currentUser, capability)
			else -> membershipService.requireManagerRole(event.organizationId, currentUser)
		}
	}

	private fun requireReadAccess(event: Event, currentUser: CurrentUser) {
		if (event.teamId != null || event.opponentTeamId != null) {
			val teamIds = listOfNotNull(event.teamId, event.opponentTeamId).distinct()
			if (teamIds.any { authorizationService.hasTeamCapability(event.organizationId, it, currentUser, Capabilities.EVENT_READ) }) return
			if (event.status != EventStatus.DRAFT && teamIds.any { hasFamilyEventAccess(event.organizationId, it, currentUser) }) return
			throw ForbiddenException("CAPABILITY_DENIED", "You do not have access to this team event.")
		}
		when {
			event.tournamentId != null -> authorizationService.requireTournamentCapability(event.organizationId, event.tournamentId, currentUser, Capabilities.EVENT_READ)
			else -> membershipService.requireActiveMembership(event.organizationId, currentUser)
		}
	}

	private fun hasFamilyEventAccess(organizationId: UUID, teamId: UUID, currentUser: CurrentUser): Boolean =
		participantRepository.findActiveByTeam(teamId, organizationId).any { participant ->
			authorizationService.hasParticipantCapability(currentUser, participant.id, Capabilities.ATHLETE_SCHEDULE_VIEW) ||
				authorizationService.hasHouseholdCapability(organizationId, participant.householdId, currentUser, Capabilities.EVENT_READ)
		}

	/**
	 * Section 14.1A's source-owned fields (official opponent, start time, venue,
	 * official status) can't be silently overwritten through the normal staff-edit
	 * path on an imported event (Phase 12 slice 4, ADR-034) — call [detachFromSource]
	 * first, same as choosing "detach from source" over a temporary local override
	 * or updating the field in the source system itself. Overlay fields (title,
	 * description, arrival time, meeting point, directions notes, area, visibility)
	 * are never checked here — they've always been freely editable regardless of
	 * [Event.provider].
	 */
	private fun requireNoSourceOwnedFieldChange(
		status: EventStatus?,
		startAt: Instant?,
		endAt: Instant?,
		venueName: String?,
		address: String?,
		latitude: Double?,
		longitude: Double?,
		opponentTeamId: UUID?,
		opponentName: String?,
	) {
		val attemptedSourceOwnedChange = status != null || startAt != null || endAt != null || venueName != null ||
			address != null || latitude != null || longitude != null || opponentTeamId != null || opponentName != null
		if (attemptedSourceOwnedChange) {
			throw ValidationException(
				"This event was imported from an external source — opponent, start/end time, venue, and status can't be " +
					"edited directly. Detach it from its source first, or update the value in the source system.",
			)
		}
	}

	private fun requireValidTimezone(timezone: String) {
		try {
			ZoneId.of(timezone)
		} catch (e: Exception) {
			throw ValidationException(
				"Timezone must be a valid IANA time zone id (e.g. America/New_York).",
				listOf(com.rally26.common.error.FieldError("timezone", "Invalid time zone.")),
			)
		}
	}
}
