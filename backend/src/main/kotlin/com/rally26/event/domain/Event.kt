package com.rally26.event.domain

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

enum class EventType { COMPETITION, TOURNAMENT, PRACTICE, MEETING, OTHER }

enum class EventStatus { DRAFT, TENTATIVE, SCHEDULED, DELAYED, POSTPONED, CANCELLED, COMPLETED }

/** Who can see the event at all — distinct from [EventStatus]'s DRAFT value, which gates whether it's published yet. */
enum class EventVisibility { PUBLIC, ORGANIZATION, TEAM }

enum class EventSourceType { MANUAL, CSV_IMPORT, ICS_FEED, MAXPREPS, GAMECHANGER }

data class Event(
    val id: UUID,
    val organizationId: UUID,
    val teamId: UUID?,
    val tournamentId: UUID?,
    val opponentTeamId: UUID?,
    val opponentName: String?,
    val eventType: EventType,
    val title: String?,
    val description: String?,
    val status: EventStatus,
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
    val directionsNotes: String?,
    val visibility: EventVisibility,
    val sourceType: EventSourceType,
    val provider: String?,
    val connectionId: String?,
    val externalEventId: String?,
    val externalSyncHash: String?,
    val sourceUpdatedAt: Instant?,
    val createdByUserId: UUID,
    val updatedByUserId: UUID,
    val createdAt: Instant,
    val updatedAt: Instant,
    /** Phase 24 slice 24.5 (ADR-071): a true all-day date, never zone-converted. Mutually exclusive with [startAt] (DB check constraint). */
    val allDayDate: LocalDate? = null,
    /** A detected-but-unapplied change from this event's source (ICS/CSV) — see [PendingSourceEventSnapshot] and `EventService.applySourceUpdate`. Null when there's nothing staged. */
    val pendingSourceSnapshotJson: String? = null,
    /** The sync hash of the currently-staged [pendingSourceSnapshotJson], distinct from [externalSyncHash] (the last *applied* hash) — lets a poller avoid re-staging the same unapplied change every run. */
    val pendingSourceHash: String? = null,
)

/** A tournament child event may intentionally have TBD time/opponent/area until assigned — see DESIGN-DOC.md section 14.1A. */
val EVENT_TERMINAL_STATUSES: Set<EventStatus> = setOf(EventStatus.CANCELLED, EventStatus.COMPLETED)

/**
 * DESIGN-DOC.md section 14.1A's example: "Eastside U12 Blue vs Northshore FC," derived
 * from structured team/opponent data, with an explicit [Event.title] override
 * permitted ("State Cup Semifinal"). Not stored — computed on read so a later team
 * rename is reflected automatically rather than going stale.
 */
fun displayTitle(
    event: Event,
    teamName: String?,
    opponentTeamName: String?,
): String {
    event.title?.let { return it }
    val home = teamName ?: "TBD"
    val away = opponentTeamName ?: event.opponentName ?: "TBD"
    return when (event.eventType) {
        EventType.COMPETITION -> "$home vs $away"
        else -> home
    }
}
