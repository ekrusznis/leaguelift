package com.rally26.integration.googlecalendar.web

import com.rally26.integration.core.web.IntegrationCatalogResponse
import com.rally26.integration.core.web.toResponse
import com.rally26.integration.googlecalendar.application.GoogleCalendarOverview
import com.rally26.integration.googlecalendar.domain.GoogleCalendarConnectionSetting
import com.rally26.integration.googlecalendar.domain.GoogleCalendarDescriptor
import com.rally26.integration.googlecalendar.domain.GoogleCalendarEventMapping
import java.time.Instant
import java.util.UUID

data class GoogleCalendarOverviewResponse(
    val catalog: IntegrationCatalogResponse,
    val setting: GoogleCalendarSettingResponse?,
    val mappingCount: Int,
    val icsFallbackAvailable: Boolean,
    val automaticSyncAvailable: Boolean,
)

data class GoogleCalendarDescriptorResponse(
    val id: String,
    val name: String,
    val timezone: String?,
    val primary: Boolean,
    val writable: Boolean,
)

data class SelectGoogleCalendarRequest(
    val calendarId: String,
)

data class GoogleCalendarSettingResponse(
    val connectionId: UUID,
    val selectedCalendarId: String?,
    val selectedCalendarName: String?,
    val selectedCalendarTimezone: String?,
    val syncDirection: String,
    val automaticSyncEnabled: Boolean,
    val lastCalendarListedAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class GoogleCalendarEventMappingResponse(
    val id: UUID,
    val connectionId: UUID,
    val eventId: UUID,
    val externalCalendarId: String,
    val externalEventId: String,
    val externalEtag: String?,
    val syncStatus: String,
    val lastExportHash: String?,
    val lastSyncedAt: Instant?,
    val lastErrorCode: String?,
    val lastErrorMessage: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

fun GoogleCalendarOverview.toResponse() = GoogleCalendarOverviewResponse(
    catalog = catalog.toResponse(),
    setting = setting?.toResponse(),
    mappingCount = mappingCount,
    icsFallbackAvailable = icsFallbackAvailable,
    automaticSyncAvailable = automaticSyncAvailable,
)

fun GoogleCalendarDescriptor.toResponse() = GoogleCalendarDescriptorResponse(id, name, timezone, primary, writable)

fun GoogleCalendarConnectionSetting.toResponse() = GoogleCalendarSettingResponse(
    connectionId,
    selectedCalendarId,
    selectedCalendarName,
    selectedCalendarTimezone,
    syncDirection.name,
    automaticSyncEnabled,
    lastCalendarListedAt,
    createdAt,
    updatedAt,
)

fun GoogleCalendarEventMapping.toResponse() = GoogleCalendarEventMappingResponse(
    id,
    connectionId,
    eventId,
    externalCalendarId,
    externalEventId,
    externalEtag,
    syncStatus.name,
    lastExportHash,
    lastSyncedAt,
    lastErrorCode,
    lastErrorMessage,
    createdAt,
    updatedAt,
)
