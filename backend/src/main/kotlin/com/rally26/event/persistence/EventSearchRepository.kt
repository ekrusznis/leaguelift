package com.rally26.event.persistence

import com.rally26.event.domain.Event
import com.rally26.event.domain.EventListCriteria
import com.rally26.event.domain.EventListSort
import com.rally26.event.domain.EventSourceType
import com.rally26.event.domain.EventStatus
import com.rally26.event.domain.EventType
import com.rally26.event.domain.EventVisibility
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.util.UUID

private const val SEARCH_EVENT_COLUMNS = """
    id, organization_id, team_id, tournament_id, opponent_team_id, opponent_name,
    event_type, title, description, status, start_at, end_at, arrival_at, meeting_at,
    timezone, venue_name, address, latitude, longitude, area, meeting_point, directions_notes,
    visibility, source_type, provider, connection_id, external_event_id, external_sync_hash,
    source_updated_at, created_by_user_id, updated_by_user_id, created_at, updated_at, all_day_date,
    pending_source_snapshot_json, pending_source_hash
"""

@Repository
class EventSearchRepository(
    private val jdbcClient: JdbcClient,
) {
    fun searchOrganization(
        organizationId: UUID,
        criteria: EventListCriteria,
        offset: Int,
        limit: Int,
    ): List<Event> = search(organizationId, null, null, null, criteria, false, offset, limit)

    fun countOrganization(
        organizationId: UUID,
        criteria: EventListCriteria,
    ): Long = count(organizationId, null, null, null, criteria, false)

    fun searchTeam(
        organizationId: UUID,
        teamId: UUID,
        criteria: EventListCriteria,
        offset: Int,
        limit: Int,
    ): List<Event> = search(organizationId, teamId, null, null, criteria, false, offset, limit)

    fun countTeam(
        organizationId: UUID,
        teamId: UUID,
        criteria: EventListCriteria,
    ): Long = count(organizationId, teamId, null, null, criteria, false)

    fun searchTournament(
        organizationId: UUID,
        tournamentId: UUID,
        criteria: EventListCriteria,
        offset: Int,
        limit: Int,
    ): List<Event> = search(organizationId, null, tournamentId, null, criteria, false, offset, limit)

    fun countTournament(
        organizationId: UUID,
        tournamentId: UUID,
        criteria: EventListCriteria,
    ): Long = count(organizationId, null, tournamentId, null, criteria, false)

    fun searchTeams(
        organizationId: UUID,
        teamIds: Collection<UUID>,
        criteria: EventListCriteria,
        offset: Int,
        limit: Int,
    ): List<Event> {
        if (teamIds.isEmpty()) return emptyList()
        return search(organizationId, null, null, teamIds, criteria, true, offset, limit)
    }

    fun countTeams(
        organizationId: UUID,
        teamIds: Collection<UUID>,
        criteria: EventListCriteria,
    ): Long {
        if (teamIds.isEmpty()) return 0
        return count(organizationId, null, null, teamIds, criteria, true)
    }

    private fun search(
        organizationId: UUID,
        teamId: UUID?,
        tournamentId: UUID?,
        teamIds: Collection<UUID>?,
        criteria: EventListCriteria,
        excludeDrafts: Boolean,
        offset: Int,
        limit: Int,
    ): List<Event> {
        val built = buildSql(organizationId, teamId, tournamentId, teamIds, criteria, excludeDrafts, countOnly = false)
        var statement =
            jdbcClient
                .sql("${built.first} offset :offset limit :limit")
                .param("organizationId", organizationId)
                .param("offset", offset)
                .param("limit", limit)
        built.second.forEach { (name, value) -> statement = statement.param(name, value) }
        return statement.query(::mapRow).list()
    }

    private fun count(
        organizationId: UUID,
        teamId: UUID?,
        tournamentId: UUID?,
        teamIds: Collection<UUID>?,
        criteria: EventListCriteria,
        excludeDrafts: Boolean,
    ): Long {
        val built = buildSql(organizationId, teamId, tournamentId, teamIds, criteria, excludeDrafts, countOnly = true)
        var statement = jdbcClient.sql(built.first).param("organizationId", organizationId)
        built.second.forEach { (name, value) -> statement = statement.param(name, value) }
        return statement.query(Long::class.java).single()
    }

    private fun buildSql(
        organizationId: UUID,
        teamId: UUID?,
        tournamentId: UUID?,
        teamIds: Collection<UUID>?,
        criteria: EventListCriteria,
        excludeDrafts: Boolean,
        countOnly: Boolean,
    ): Pair<String, Map<String, Any>> {
        val sql =
            StringBuilder(
                if (countOnly) {
                    "select count(*) from event e where e.organization_id = :organizationId"
                } else {
                    "select $SEARCH_EVENT_COLUMNS from event e where e.organization_id = :organizationId"
                },
            )
        val params = linkedMapOf<String, Any>()

        teamId?.let {
            sql.append(" and (e.team_id = :teamId or e.opponent_team_id = :teamId)")
            params["teamId"] = it
        }
        tournamentId?.let {
            sql.append(" and e.tournament_id = :tournamentId")
            params["tournamentId"] = it
        }
        teamIds?.let {
            sql.append(" and (e.team_id in (:teamIds) or e.opponent_team_id in (:teamIds))")
            params["teamIds"] = it
        }
        if (excludeDrafts) sql.append(" and e.status <> 'DRAFT'")

        criteria.keyword?.trim()?.takeIf { it.isNotEmpty() }?.let { keyword ->
            sql.append(
                """
                and (
                   lower(coalesce(e.title, '')) like :keyword
                   or lower(coalesce(e.opponent_name, '')) like :keyword
                   or lower(coalesce(e.venue_name, '')) like :keyword
                   or lower(coalesce(e.address, '')) like :keyword
                   or lower(coalesce(e.area, '')) like :keyword
                   or lower(coalesce(e.description, '')) like :keyword
                )
                """.trimIndent(),
            )
            params["keyword"] = "%${keyword.lowercase()}%"
        }
        criteria.eventType?.let {
            sql.append(" and e.event_type = :eventType")
            params["eventType"] = it.name
        }
        criteria.status?.let {
            sql.append(" and e.status = :status")
            params["status"] = it.name
        }
        criteria.from?.let {
            sql.append(" and coalesce(e.start_at, e.all_day_date::timestamp with time zone) >= :fromInstant")
            params["fromInstant"] = Timestamp.from(it)
        }
        criteria.to?.let {
            sql.append(" and coalesce(e.start_at, e.all_day_date::timestamp with time zone) <= :toInstant")
            params["toInstant"] = Timestamp.from(it)
        }

        if (!countOnly) {
            sql.append(
                when (criteria.sort) {
                    EventListSort.DATE_ASC ->
                        " order by coalesce(e.start_at, e.all_day_date::timestamp with time zone) asc nulls last, e.created_at desc"
                    EventListSort.DATE_DESC ->
                        " order by coalesce(e.start_at, e.all_day_date::timestamp with time zone) desc nulls last, e.created_at desc"
                    EventListSort.TITLE_ASC ->
                        " order by lower(coalesce(e.title, e.opponent_name, e.event_type::text)) asc, e.created_at desc"
                    EventListSort.CREATED_DESC ->
                        " order by e.created_at desc"
                },
            )
        }
        return sql.toString() to params
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
            allDayDate = rs.getDate("all_day_date")?.toLocalDate(),
            pendingSourceSnapshotJson = rs.getString("pending_source_snapshot_json"),
            pendingSourceHash = rs.getString("pending_source_hash"),
        )
}
