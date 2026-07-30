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
	val status: EventSourceConnectionStatus,
	val lastSyncedAt: Instant?,
	val lastSyncStatus: EventSourceSyncStatus?,
	val lastSyncError: String?,
	val createdByUserId: UUID,
	val createdAt: Instant,
	val updatedAt: Instant,
)
