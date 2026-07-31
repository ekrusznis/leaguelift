package com.leaguelift.dashboard.application

import com.leaguelift.dashboard.web.ScheduleItem
import com.leaguelift.event.domain.EVENT_TERMINAL_STATUSES
import com.leaguelift.event.domain.Event
import com.leaguelift.event.domain.EventStatus
import com.leaguelift.event.domain.displayTitle
import com.leaguelift.team.persistence.TeamRepository
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import java.util.UUID

private const val DASHBOARD_EVENT_LIMIT = 50

/**
 * Converts the real Phase 10 event model into the compact schedule shape shared by
 * the persona dashboards. Keeping this in one mapper prevents Owner, Coach, Parent,
 * and Athlete cards from formatting the same event differently or inventing sample
 * IDs that cannot open the real event-detail route.
 */
@Component
class DashboardEventMapper(private val teamRepository: TeamRepository) {

	fun upcoming(events: List<Event>, includeDrafts: Boolean = false, now: Instant = Instant.now()): List<Event> =
		events.asSequence()
			.filter { includeDrafts || it.status != EventStatus.DRAFT }
			.filter { it.status !in EVENT_TERMINAL_STATUSES }
			.filter { it.startAt == null || !it.startAt.isBefore(now) }
			.sortedBy { it.startAt ?: Instant.MAX }
			.take(DASHBOARD_EVENT_LIMIT)
			.toList()

	fun toScheduleItem(event: Event, organizationId: UUID): ScheduleItem {
		val teamName = event.teamId?.let { teamRepository.findById(it, organizationId)?.name }
		val opponentName = event.opponentTeamId?.let { teamRepository.findById(it, organizationId)?.name }
		val zone = runCatching { ZoneId.of(event.timezone) }.getOrDefault(ZoneId.systemDefault())
		val starts = event.startAt?.atZone(zone)
		val details = listOfNotNull(
			event.eventType.name.lowercase().replaceFirstChar { it.titlecase(Locale.US) },
			event.venueName,
			event.area,
		).distinct().joinToString(" · ")
		return ScheduleItem(
			id = event.id.toString(),
			day = starts?.dayOfWeek?.getDisplayName(TextStyle.SHORT, Locale.US)?.uppercase(Locale.US) ?: "TBD",
			date = starts?.dayOfMonth?.toString() ?: "TBD",
			title = displayTitle(event, teamName, opponentName),
			subtitle = details,
			time = starts?.format(DateTimeFormatter.ofPattern("h:mm a", Locale.US)) ?: "TBD",
			tag = event.status.name.lowercase().replaceFirstChar { it.titlecase(Locale.US) },
		)
	}
}
