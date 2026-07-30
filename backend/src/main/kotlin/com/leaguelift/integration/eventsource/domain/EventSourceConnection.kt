package com.leaguelift.integration.eventsource.domain

import java.time.Instant
import java.util.UUID

/** Only providers with an ongoing, stateful connection — CSV_IMPORT never appears here (ADR-031). */
enum class EventSourceProvider { ICS_FEED, MAXPREPS, GAMECHANGER }

enum class EventSourceConnectionStatus { ACTIVE, DISCONNECTED }

enum class EventSourceSyncStatus { SUCCESS, FAILED }

/**
 * An organization's own connection to an external event source (Phase 12 slice 1,
 * ADR-031) — `feed_url` is only meaningful for [EventSourceProvider.ICS_FEED];
 * MAXPREPS/GAMECHANGER rows don't exist yet (no real connection flow is wired for
 * either — see the Integrations page's disabled cards).
 */
data class EventSourceConnection(
	val id: UUID,
	val organizationId: UUID,
	val provider: EventSourceProvider,
	val label: String,
	val feedUrl: String?,
	/** Resolves a feed's floating (no UTC offset) DTSTART/DTEND values (Phase 12 slice 3, ADR-033) — mirrors the CSV import connector's own per-upload timezone (ADR-032). */
	val timezone: String,
	/** Which team synced events belong to — null means org-wide. Mirrors CSV import's own per-upload team scope (ADR-032). */
	val teamId: UUID?,
	val status: EventSourceConnectionStatus,
	val lastSyncedAt: Instant?,
	val lastSyncStatus: EventSourceSyncStatus?,
	val lastSyncError: String?,
	val createdByUserId: UUID,
	val createdAt: Instant,
	val updatedAt: Instant,
)
