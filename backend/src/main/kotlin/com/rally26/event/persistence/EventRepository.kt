package com.rally26.event.persistence

import com.rally26.event.domain.Event
import com.rally26.event.domain.EventSourceType
import com.rally26.event.domain.EventStatus
import com.rally26.event.domain.EventType
import com.rally26.event.domain.EventVisibility
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

private const val COLUMNS = """
	id, organization_id, team_id, tournament_id, opponent_team_id, opponent_name,
	event_type, title, description, status, start_at, end_at, arrival_at, meeting_at,
	timezone, venue_name, address, latitude, longitude, area, meeting_point, directions_notes,
	visibility, source_type, provider, connection_id, external_event_id, external_sync_hash,
	source_updated_at, created_by_user_id, updated_by_user_id, created_at, updated_at
"""

@Repository
class EventRepository(
    private val jdbcClient: JdbcClient,
) {
    fun findById(
        id: UUID,
        organizationId: UUID,
    ): Event? =
        jdbcClient
            .sql("select $COLUMNS from event where id = :id and organization_id = :organizationId")
            .param("id", id)
            .param("organizationId", organizationId)
            .query(::mapRow)
            .optional()
            .orElse(null)

    fun findByOrganization(
        organizationId: UUID,
        offset: Int,
        limit: Int,
    ): List<Event> =
        jdbcClient
            .sql(
                """
                select $COLUMNS from event where organization_id = :organizationId
                order by start_at asc nulls last, created_at desc
                offset :offset limit :limit
                """.trimIndent(),
            ).param("organizationId", organizationId)
            .param("offset", offset)
            .param("limit", limit)
            .query(::mapRow)
            .list()

    fun findByTeam(
        teamId: UUID,
        organizationId: UUID,
        offset: Int,
        limit: Int,
    ): List<Event> =
        jdbcClient
            .sql(
                """
                select $COLUMNS from event
                where organization_id = :organizationId and (team_id = :teamId or opponent_team_id = :teamId)
                order by start_at asc nulls last, created_at desc
                offset :offset limit :limit
                """.trimIndent(),
            ).param("organizationId", organizationId)
            .param("teamId", teamId)
            .param("offset", offset)
            .param("limit", limit)
            .query(::mapRow)
            .list()

    fun findByTournament(
        tournamentId: UUID,
        organizationId: UUID,
        offset: Int,
        limit: Int,
    ): List<Event> =
        jdbcClient
            .sql(
                """
                select $COLUMNS from event where organization_id = :organizationId and tournament_id = :tournamentId
                order by start_at asc nulls last, created_at desc
                offset :offset limit :limit
                """.trimIndent(),
            ).param("organizationId", organizationId)
            .param("tournamentId", tournamentId)
            .param("offset", offset)
            .param("limit", limit)
            .query(::mapRow)
            .list()

    /** The dedup lookup behind `event_external_identity_idx` (Phase 12 slice 2, ADR-032) — null `provider` is never passed here, manual events are exempt from this identity model entirely. */
    fun findByExternalIdentity(
        organizationId: UUID,
        provider: String,
        connectionId: String,
        externalEventId: String,
    ): Event? =
        jdbcClient
            .sql(
                """
                select $COLUMNS from event
                where organization_id = :organizationId and provider = :provider and connection_id = :connectionId and external_event_id = :externalEventId
                """.trimIndent(),
            ).param("organizationId", organizationId)
            .param("provider", provider)
            .param("connectionId", connectionId)
            .param("externalEventId", externalEventId)
            .query(::mapRow)
            .optional()
            .orElse(null)

    /** Every event owned by any of [teamIds] — the guardian/athlete "combined schedule" query (Phase 10 slice 2). */
    fun findByTeams(
        teamIds: Collection<UUID>,
        organizationId: UUID,
        offset: Int,
        limit: Int,
    ): List<Event> {
        if (teamIds.isEmpty()) return emptyList()
        return jdbcClient
            .sql(
                """
                select $COLUMNS from event
                where organization_id = :organizationId and (team_id in (:teamIds) or opponent_team_id in (:teamIds))
                order by start_at asc nulls last, created_at desc
                offset :offset limit :limit
                """.trimIndent(),
            ).param("organizationId", organizationId)
            .param("teamIds", teamIds)
            .param("offset", offset)
            .param("limit", limit)
            .query(::mapRow)
            .list()
    }

    fun insert(
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
        createdByUserId: UUID,
        sourceType: EventSourceType = EventSourceType.MANUAL,
        provider: String? = null,
        connectionId: String? = null,
        externalEventId: String? = null,
        externalSyncHash: String? = null,
        sourceUpdatedAt: Instant? = null,
        /** MANUAL creation always starts DRAFT (matches [EventStatus] check constraint's default); CSV import (Phase 12 slice 2) starts TENTATIVE instead — the org already reviewed the source file before uploading it, so forcing a manual publish per imported row would be pure friction. */
        initialStatus: EventStatus = EventStatus.DRAFT,
    ): Event {
        val id = UUID.randomUUID()
        val now = Instant.now()
        jdbcClient
            .sql(
                """
                insert into event
                	(id, organization_id, team_id, tournament_id, opponent_team_id, opponent_name,
                	 event_type, title, description, status, start_at, end_at, arrival_at, meeting_at,
                	 timezone, venue_name, address, latitude, longitude, area, meeting_point, directions_notes,
                	 visibility, source_type, provider, connection_id, external_event_id, external_sync_hash,
                	 source_updated_at, created_by_user_id, updated_by_user_id, created_at, updated_at)
                values
                	(:id, :organizationId, :teamId, :tournamentId, :opponentTeamId, :opponentName,
                	 :eventType, :title, :description, :initialStatus, :startAt, :endAt, :arrivalAt, :meetingAt,
                	 :timezone, :venueName, :address, :latitude, :longitude, :area, :meetingPoint, :directionsNotes,
                	 :visibility, :sourceType, :provider, :connectionId, :externalEventId, :externalSyncHash,
                	 :sourceUpdatedAt, :createdByUserId, :createdByUserId, :now, :now)
                """.trimIndent(),
            ).param("id", id)
            .param("organizationId", organizationId)
            .param("teamId", teamId)
            .param("tournamentId", tournamentId)
            .param("opponentTeamId", opponentTeamId)
            .param("opponentName", opponentName)
            .param("eventType", eventType.name)
            .param("title", title)
            .param("description", description)
            .param("initialStatus", initialStatus.name)
            .param("startAt", startAt?.let { Timestamp.from(it) })
            .param("endAt", endAt?.let { Timestamp.from(it) })
            .param("arrivalAt", arrivalAt?.let { Timestamp.from(it) })
            .param("meetingAt", meetingAt?.let { Timestamp.from(it) })
            .param("timezone", timezone)
            .param("venueName", venueName)
            .param("address", address)
            .param("latitude", latitude)
            .param("longitude", longitude)
            .param("area", area)
            .param("meetingPoint", meetingPoint)
            .param("directionsNotes", directionsNotes)
            .param("visibility", visibility.name)
            .param("createdByUserId", createdByUserId)
            .param("now", Timestamp.from(now))
            .param("sourceType", sourceType.name)
            .param("provider", provider)
            .param("connectionId", connectionId)
            .param("externalEventId", externalEventId)
            .param("externalSyncHash", externalSyncHash)
            .param("sourceUpdatedAt", sourceUpdatedAt?.let { Timestamp.from(it) })
            .update()
        return findById(id, organizationId)!!
    }

    /** Partial update — every field null means "leave unchanged," matching every other repository's coalesce pattern. [status] and [clearOpponent] are the two exceptions requiring an explicit non-null intent. */
    fun update(
        id: UUID,
        organizationId: UUID,
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
        updatedByUserId: UUID,
        externalSyncHash: String? = null,
        sourceUpdatedAt: Instant? = null,
    ): Int {
        val now = Instant.now()
        return jdbcClient
            .sql(
                """
                update event
                set title             = coalesce(:title, title),
                    description       = coalesce(:description, description),
                    status            = coalesce(:status, status),
                    start_at          = coalesce(:startAt, start_at),
                    end_at            = coalesce(:endAt, end_at),
                    arrival_at        = coalesce(:arrivalAt, arrival_at),
                    meeting_at        = coalesce(:meetingAt, meeting_at),
                    venue_name        = coalesce(:venueName, venue_name),
                    address           = coalesce(:address, address),
                    latitude          = coalesce(:latitude, latitude),
                    longitude         = coalesce(:longitude, longitude),
                    area              = coalesce(:area, area),
                    meeting_point     = coalesce(:meetingPoint, meeting_point),
                    directions_notes  = coalesce(:directionsNotes, directions_notes),
                    opponent_team_id  = coalesce(:opponentTeamId, opponent_team_id),
                    opponent_name     = coalesce(:opponentName, opponent_name),
                    external_sync_hash = coalesce(:externalSyncHash, external_sync_hash),
                    source_updated_at = coalesce(:sourceUpdatedAt, source_updated_at),
                    updated_by_user_id = :updatedByUserId,
                    updated_at        = :now
                where id = :id and organization_id = :organizationId
                """.trimIndent(),
            ).param("title", title)
            .param("description", description)
            .param("status", status?.name)
            .param("startAt", startAt?.let { Timestamp.from(it) })
            .param("endAt", endAt?.let { Timestamp.from(it) })
            .param("arrivalAt", arrivalAt?.let { Timestamp.from(it) })
            .param("meetingAt", meetingAt?.let { Timestamp.from(it) })
            .param("venueName", venueName)
            .param("address", address)
            .param("latitude", latitude)
            .param("longitude", longitude)
            .param("area", area)
            .param("meetingPoint", meetingPoint)
            .param("directionsNotes", directionsNotes)
            .param("opponentTeamId", opponentTeamId)
            .param("opponentName", opponentName)
            .param("externalSyncHash", externalSyncHash)
            .param("sourceUpdatedAt", sourceUpdatedAt?.let { Timestamp.from(it) })
            .param("updatedByUserId", updatedByUserId)
            .param("now", Timestamp.from(now))
            .param("id", id)
            .param("organizationId", organizationId)
            .update()
    }

    /** Converts an imported event back to MANUAL (Phase 12 slice 4, ADR-034) — clears every import-identity column, since a detached event is no longer subject to `event_external_identity_idx`'s dedup, matching the "provider is null" exemption that index's own partial-where clause already documents. */
    fun detachFromSource(
        id: UUID,
        organizationId: UUID,
        updatedByUserId: UUID,
    ): Int {
        val now = Instant.now()
        return jdbcClient
            .sql(
                """
                update event
                set source_type = 'MANUAL', provider = null, connection_id = null, external_event_id = null,
                    external_sync_hash = null, source_updated_at = null, updated_by_user_id = :updatedByUserId, updated_at = :now
                where id = :id and organization_id = :organizationId
                """.trimIndent(),
            ).param("updatedByUserId", updatedByUserId)
            .param("now", Timestamp.from(now))
            .param("id", id)
            .param("organizationId", organizationId)
            .update()
    }

    fun updateStatus(
        id: UUID,
        organizationId: UUID,
        status: EventStatus,
        updatedByUserId: UUID,
    ): Int {
        val now = Instant.now()
        return jdbcClient
            .sql(
                """
                update event set status = :status, updated_by_user_id = :updatedByUserId, updated_at = :now
                where id = :id and organization_id = :organizationId
                """.trimIndent(),
            ).param("status", status.name)
            .param("updatedByUserId", updatedByUserId)
            .param("now", Timestamp.from(now))
            .param("id", id)
            .param("organizationId", organizationId)
            .update()
    }

    private fun mapRow(
        rs: java.sql.ResultSet,
        rowNum: Int,
    ): Event =
        Event(
            id = rs.getObject("id", UUID::class.java),
            organizationId = rs.getObject("organization_id", UUID::class.java),
            teamId = rs.getObject("team_id", UUID::class.java),
            tournamentId = rs.getObject("tournament_id", UUID::class.java),
            opponentTeamId = rs.getObject("opponent_team_id", UUID::class.java),
            opponentName = rs.getString("opponent_name"),
            eventType = EventType.valueOf(rs.getString("event_type")),
            title = rs.getString("title"),
            description = rs.getString("description"),
            status = EventStatus.valueOf(rs.getString("status")),
            startAt = rs.getTimestamp("start_at")?.toInstant(),
            endAt = rs.getTimestamp("end_at")?.toInstant(),
            arrivalAt = rs.getTimestamp("arrival_at")?.toInstant(),
            meetingAt = rs.getTimestamp("meeting_at")?.toInstant(),
            timezone = rs.getString("timezone"),
            venueName = rs.getString("venue_name"),
            address = rs.getString("address"),
            latitude = rs.getObject("latitude") as Double?,
            longitude = rs.getObject("longitude") as Double?,
            area = rs.getString("area"),
            meetingPoint = rs.getString("meeting_point"),
            directionsNotes = rs.getString("directions_notes"),
            visibility = EventVisibility.valueOf(rs.getString("visibility")),
            sourceType = EventSourceType.valueOf(rs.getString("source_type")),
            provider = rs.getString("provider"),
            connectionId = rs.getString("connection_id"),
            externalEventId = rs.getString("external_event_id"),
            externalSyncHash = rs.getString("external_sync_hash"),
            sourceUpdatedAt = rs.getTimestamp("source_updated_at")?.toInstant(),
            createdByUserId = rs.getObject("created_by_user_id", UUID::class.java),
            updatedByUserId = rs.getObject("updated_by_user_id", UUID::class.java),
            createdAt = rs.getTimestamp("created_at").toInstant(),
            updatedAt = rs.getTimestamp("updated_at").toInstant(),
        )
}
