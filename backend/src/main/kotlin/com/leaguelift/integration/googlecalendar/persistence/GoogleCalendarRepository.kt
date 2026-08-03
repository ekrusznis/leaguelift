package com.leaguelift.integration.googlecalendar.persistence

import com.leaguelift.integration.googlecalendar.domain.GoogleCalendarConnectionSetting
import com.leaguelift.integration.googlecalendar.domain.GoogleCalendarEventMapping
import com.leaguelift.integration.googlecalendar.domain.GoogleCalendarMappingStatus
import com.leaguelift.integration.googlecalendar.domain.GoogleCalendarSyncDirection
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.util.*

@Repository
class GoogleCalendarRepository(
    private val jdbcClient: JdbcClient,
) {
    fun findSetting(connectionId: UUID): GoogleCalendarConnectionSetting? =
        jdbcClient
            .sql("select $SETTING_COLUMNS from google_calendar_connection_setting where connection_id = :connectionId")
            .param("connectionId", connectionId)
            .query(::mapSetting)
            .optional()
            .orElse(null)

    fun upsertSetting(
        connectionId: UUID,
        calendarId: String,
        calendarName: String,
        timezone: String?,
    ): GoogleCalendarConnectionSetting {
        jdbcClient
            .sql(
                """
                insert into google_calendar_connection_setting
                    (connection_id, selected_calendar_id, selected_calendar_name, selected_calendar_timezone,
                     sync_direction, automatic_sync_enabled, last_calendar_listed_at, created_at, updated_at)
                values
                    (:connectionId, :calendarId, :calendarName, :timezone,
                     'LEAGUELIFT_TO_GOOGLE', false, now(), now(), now())
                on conflict (connection_id) do update
                set selected_calendar_id = excluded.selected_calendar_id,
                    selected_calendar_name = excluded.selected_calendar_name,
                    selected_calendar_timezone = excluded.selected_calendar_timezone,
                    automatic_sync_enabled = false,
                    last_calendar_listed_at = now(),
                    updated_at = now()
                """.trimIndent(),
            ).param("connectionId", connectionId)
            .param("calendarId", calendarId.take(500))
            .param("calendarName", calendarName.take(300))
            .param("timezone", timezone?.take(100))
            .update()
        return requireNotNull(findSetting(connectionId))
    }

    fun markCalendarsListed(connectionId: UUID) {
        jdbcClient
            .sql(
                """
                insert into google_calendar_connection_setting
                    (connection_id, sync_direction, automatic_sync_enabled, last_calendar_listed_at, created_at, updated_at)
                values (:connectionId, 'LEAGUELIFT_TO_GOOGLE', false, now(), now(), now())
                on conflict (connection_id) do update
                set last_calendar_listed_at = now(), updated_at = now()
                """.trimIndent(),
            ).param("connectionId", connectionId)
            .update()
    }

    fun clearSelection(connectionId: UUID): GoogleCalendarConnectionSetting? {
        jdbcClient
            .sql(
                """
                update google_calendar_connection_setting
                set selected_calendar_id = null, selected_calendar_name = null,
                    selected_calendar_timezone = null, automatic_sync_enabled = false,
                    updated_at = now()
                where connection_id = :connectionId
                """.trimIndent(),
            ).param("connectionId", connectionId)
            .update()
        return findSetting(connectionId)
    }

    fun listMappings(connectionId: UUID): List<GoogleCalendarEventMapping> =
        jdbcClient
            .sql(
                """
                select $MAPPING_COLUMNS
                from google_calendar_event_mapping
                where connection_id = :connectionId
                order by updated_at desc
                """.trimIndent(),
            ).param("connectionId", connectionId)
            .query(::mapMapping)
            .list()

    fun upsertMapping(
        connectionId: UUID,
        eventId: UUID,
        externalCalendarId: String,
        externalEventId: String,
        externalEtag: String?,
        exportHash: String,
    ): GoogleCalendarEventMapping {
        jdbcClient
            .sql(
                """
                insert into google_calendar_event_mapping
                    (connection_id, event_id, external_calendar_id, external_event_id,
                     external_etag, sync_status, last_export_hash, last_synced_at, created_at, updated_at)
                values
                    (:connectionId, :eventId, :calendarId, :externalEventId,
                     :externalEtag, 'SYNCED', :exportHash, now(), now(), now())
                on conflict (connection_id, event_id) do update
                set external_calendar_id = excluded.external_calendar_id,
                    external_event_id = excluded.external_event_id,
                    external_etag = excluded.external_etag,
                    sync_status = 'SYNCED', last_export_hash = excluded.last_export_hash,
                    last_synced_at = now(), last_error_code = null, last_error_message = null,
                    updated_at = now()
                """.trimIndent(),
            ).param("connectionId", connectionId)
            .param("eventId", eventId)
            .param("calendarId", externalCalendarId.take(500))
            .param("externalEventId", externalEventId.take(500))
            .param("externalEtag", externalEtag?.take(500))
            .param("exportHash", exportHash)
            .update()
        return jdbcClient
            .sql(
                "select $MAPPING_COLUMNS from google_calendar_event_mapping where connection_id = :connectionId and event_id = :eventId",
            ).param("connectionId", connectionId)
            .param("eventId", eventId)
            .query(::mapMapping)
            .single()
    }

    fun markMappingFailed(
        connectionId: UUID,
        eventId: UUID,
        errorCode: String,
        errorMessage: String,
    ) {
        jdbcClient
            .sql(
                """
                update google_calendar_event_mapping
                set sync_status = 'FAILED', last_error_code = :errorCode,
                    last_error_message = :errorMessage, updated_at = now()
                where connection_id = :connectionId and event_id = :eventId
                """.trimIndent(),
            ).param("connectionId", connectionId)
            .param("eventId", eventId)
            .param("errorCode", errorCode.take(120))
            .param("errorMessage", errorMessage.take(500))
            .update()
    }

    private fun mapSetting(
        rs: ResultSet,
        rowNum: Int,
    ) = GoogleCalendarConnectionSetting(
        connectionId = rs.getObject("connection_id", UUID::class.java),
        selectedCalendarId = rs.getString("selected_calendar_id"),
        selectedCalendarName = rs.getString("selected_calendar_name"),
        selectedCalendarTimezone = rs.getString("selected_calendar_timezone"),
        syncDirection = GoogleCalendarSyncDirection.valueOf(rs.getString("sync_direction")),
        automaticSyncEnabled = rs.getBoolean("automatic_sync_enabled"),
        lastCalendarListedAt = rs.getTimestamp("last_calendar_listed_at")?.toInstant(),
        createdAt = rs.getTimestamp("created_at").toInstant(),
        updatedAt = rs.getTimestamp("updated_at").toInstant(),
    )

    private fun mapMapping(
        rs: ResultSet,
        rowNum: Int,
    ) = GoogleCalendarEventMapping(
        id = rs.getObject("id", UUID::class.java),
        connectionId = rs.getObject("connection_id", UUID::class.java),
        eventId = rs.getObject("event_id", UUID::class.java),
        externalCalendarId = rs.getString("external_calendar_id"),
        externalEventId = rs.getString("external_event_id"),
        externalEtag = rs.getString("external_etag"),
        syncStatus = GoogleCalendarMappingStatus.valueOf(rs.getString("sync_status")),
        lastExportHash = rs.getString("last_export_hash"),
        lastSyncedAt = rs.getTimestamp("last_synced_at")?.toInstant(),
        lastErrorCode = rs.getString("last_error_code"),
        lastErrorMessage = rs.getString("last_error_message"),
        createdAt = rs.getTimestamp("created_at").toInstant(),
        updatedAt = rs.getTimestamp("updated_at").toInstant(),
    )

    private companion object {
        const val SETTING_COLUMNS = "connection_id, selected_calendar_id, selected_calendar_name, selected_calendar_timezone, sync_direction, automatic_sync_enabled, last_calendar_listed_at, created_at, updated_at"
        const val MAPPING_COLUMNS = "id, connection_id, event_id, external_calendar_id, external_event_id, external_etag, sync_status, last_export_hash, last_synced_at, last_error_code, last_error_message, created_at, updated_at"
    }
}
