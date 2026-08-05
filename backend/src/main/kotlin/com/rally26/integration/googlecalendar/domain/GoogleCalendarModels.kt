package com.rally26.integration.googlecalendar.domain

import java.time.Instant
import java.util.UUID

enum class GoogleCalendarSyncDirection { RALLY26_TO_GOOGLE }

enum class GoogleCalendarMappingStatus { PENDING, SYNCED, FAILED, DELETED }

data class GoogleCalendarDescriptor(
    val id: String,
    val name: String,
    val timezone: String?,
    val primary: Boolean,
    val writable: Boolean,
)

data class GoogleCalendarConnectionSetting(
    val connectionId: UUID,
    val selectedCalendarId: String?,
    val selectedCalendarName: String?,
    val selectedCalendarTimezone: String?,
    val syncDirection: GoogleCalendarSyncDirection,
    val automaticSyncEnabled: Boolean,
    val lastCalendarListedAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class GoogleCalendarEventMapping(
    val id: UUID,
    val connectionId: UUID,
    val eventId: UUID,
    val externalCalendarId: String,
    val externalEventId: String,
    val externalEtag: String?,
    val syncStatus: GoogleCalendarMappingStatus,
    val lastExportHash: String?,
    val lastSyncedAt: Instant?,
    val lastErrorCode: String?,
    val lastErrorMessage: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class GoogleCalendarEventPayload(
    val sourceEventId: UUID,
    val title: String,
    val description: String?,
    val startAt: Instant?,
    val endAt: Instant?,
    val timezone: String,
    val location: String?,
    val cancelled: Boolean,
)

data class GoogleCalendarWriteResult(
    val externalEventId: String,
    val externalEtag: String?,
)
