package com.rally26.integration.eventsource.persistence

import com.rally26.integration.eventsource.domain.EventSourceConnection
import com.rally26.integration.eventsource.domain.EventSourceConnectionStatus
import com.rally26.integration.eventsource.domain.EventSourceProvider
import com.rally26.integration.eventsource.domain.EventSourceSyncStatus
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

private const val COLS =
	"id, organization_id, provider, label, feed_url, timezone, team_id, status, last_synced_at, last_sync_status, last_sync_error, created_by_user_id, created_at, updated_at"

@Repository
class EventSourceConnectionRepository(private val jdbcClient: JdbcClient) {

	fun findById(id: UUID, organizationId: UUID): EventSourceConnection? =
		jdbcClient.sql("select $COLS from event_source_connection where id = :id and organization_id = :organizationId")
			.param("id", id).param("organizationId", organizationId)
			.query(::mapRow).optional().orElse(null)

	fun listForOrganization(organizationId: UUID): List<EventSourceConnection> =
		jdbcClient.sql(
			"""
			select $COLS from event_source_connection
			where organization_id = :organizationId
			order by created_at asc
			""".trimIndent(),
		)
			.param("organizationId", organizationId)
			.query(::mapRow).list()

	/** Every ACTIVE ICS_FEED connection across all organizations — the poller's own scan query (Phase 12 slice 3). */
	fun listActiveIcsFeedConnections(): List<EventSourceConnection> =
		jdbcClient.sql(
			"select $COLS from event_source_connection where provider = 'ICS_FEED' and status = 'ACTIVE'",
		)
			.query(::mapRow).list()

	fun insert(organizationId: UUID, provider: EventSourceProvider, label: String, feedUrl: String?, timezone: String, teamId: UUID?, createdByUserId: UUID): EventSourceConnection {
		val now = Instant.now()
		val id = UUID.randomUUID()
		jdbcClient.sql(
			"""
			insert into event_source_connection
				(id, organization_id, provider, label, feed_url, timezone, team_id, status, created_by_user_id, created_at, updated_at)
			values
				(:id, :organizationId, :provider, :label, :feedUrl, :timezone, :teamId, 'ACTIVE', :createdByUserId, :now, :now)
			""".trimIndent(),
		)
			.param("id", id).param("organizationId", organizationId).param("provider", provider.name)
			.param("label", label).param("feedUrl", feedUrl).param("timezone", timezone).param("teamId", teamId).param("createdByUserId", createdByUserId)
			.param("now", toTimestamp(now)).update()
		return EventSourceConnection(id, organizationId, provider, label, feedUrl, timezone, teamId, EventSourceConnectionStatus.ACTIVE, null, null, null, createdByUserId, now, now)
	}

	fun disconnect(id: UUID, organizationId: UUID): Int {
		val now = Instant.now()
		return jdbcClient.sql(
			"update event_source_connection set status = 'DISCONNECTED', updated_at = :now where id = :id and organization_id = :organizationId",
		)
			.param("now", toTimestamp(now)).param("id", id).param("organizationId", organizationId).update()
	}

	/** Records a sync attempt's outcome (Phase 12 slice 3's poller) — never touches `status`, only sync bookkeeping. */
	fun recordSyncResult(id: UUID, syncStatus: EventSourceSyncStatus, error: String?): Int {
		val now = Instant.now()
		return jdbcClient.sql(
			"""
			update event_source_connection
			set last_synced_at = :now, last_sync_status = :syncStatus, last_sync_error = :error, updated_at = :now
			where id = :id
			""".trimIndent(),
		)
			.param("now", toTimestamp(now)).param("syncStatus", syncStatus.name).param("error", error).param("id", id).update()
	}

	private fun toTimestamp(instant: Instant) = java.sql.Timestamp.from(instant)

	private fun mapRow(rs: java.sql.ResultSet, row: Int) = EventSourceConnection(
		id = rs.getObject("id", UUID::class.java),
		organizationId = rs.getObject("organization_id", UUID::class.java),
		provider = EventSourceProvider.valueOf(rs.getString("provider")),
		label = rs.getString("label"),
		feedUrl = rs.getString("feed_url"),
		timezone = rs.getString("timezone"),
		teamId = rs.getObject("team_id", UUID::class.java),
		status = EventSourceConnectionStatus.valueOf(rs.getString("status")),
		lastSyncedAt = rs.getTimestamp("last_synced_at")?.toInstant(),
		lastSyncStatus = rs.getString("last_sync_status")?.let { EventSourceSyncStatus.valueOf(it) },
		lastSyncError = rs.getString("last_sync_error"),
		createdByUserId = rs.getObject("created_by_user_id", UUID::class.java),
		createdAt = rs.getTimestamp("created_at").toInstant(),
		updatedAt = rs.getTimestamp("updated_at").toInstant(),
	)
}
