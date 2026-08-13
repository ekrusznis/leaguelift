package com.rally26.event.domain

/**
 * A detected-but-not-yet-applied change from a synced source (ICS feed poll, CSV
 * re-import) — staged on `event.pending_source_snapshot_json` instead of being
 * written straight to the live event fields. A staff member reviews the diff and
 * explicitly applies it via `EventService.applySourceUpdate`, which is the only
 * thing that ever turns this into a real field change. Covers the union of fields
 * either source ever sets (ICS never sets `arrivalAt`/`area`; CSV never sets
 * `status`) — a field this source doesn't touch stays null here, same "null means
 * no opinion" convention `EventRepository.update`'s coalesce already uses.
 *
 * Timestamps are `String` (ISO-8601 via `Instant.toString()`), not `Instant` —
 * this codebase's established convention for anything serialized through a plain
 * `ObjectMapper`/`jacksonObjectMapper()` (as opposed to the Spring-managed bean,
 * which has `JavaTimeModule` auto-configured): a raw `Instant` field throws
 * `InvalidDefinitionException` under the module-less mapper instances this
 * codebase's own test suite constructs.
 */
data class PendingSourceEventSnapshot(
    val title: String?,
    val description: String?,
    val status: EventStatus?,
    val startAt: String?,
    val endAt: String?,
    val arrivalAt: String?,
    val venueName: String?,
    val address: String?,
    val area: String?,
    val opponentName: String?,
)

/** One field's before/after value for the "apply update from source" confirmation diff and its audit trail. Values are already display strings — never the raw typed value — so the frontend/audit log never needs field-specific formatting logic. */
data class EventFieldChange(
    val field: String,
    val oldValue: String?,
    val newValue: String?,
)
