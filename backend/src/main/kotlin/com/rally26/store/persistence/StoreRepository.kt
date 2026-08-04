package com.rally26.store.persistence

import com.rally26.store.domain.Store
import com.rally26.store.domain.StoreStatus
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

private const val COLUMNS = "id, organization_id, team_id, name, slug, status, created_at, updated_at"

@Repository
class StoreRepository(private val jdbcClient: JdbcClient) {

	fun findById(id: UUID, organizationId: UUID): Store? =
		jdbcClient.sql("select $COLUMNS from store where id = :id and organization_id = :organizationId")
			.param("id", id)
			.param("organizationId", organizationId)
			.query(::mapRow)
			.optional()
			.orElse(null)

	fun findBySlug(slug: String): Store? =
		jdbcClient.sql("select $COLUMNS from store where lower(slug) = lower(:slug)")
			.param("slug", slug)
			.query(::mapRow)
			.optional()
			.orElse(null)

	fun findAll(organizationId: UUID, offset: Int, limit: Int): List<Store> =
		jdbcClient.sql(
			"""
			select $COLUMNS from store
			where organization_id = :organizationId
			order by created_at desc
			offset :offset limit :limit
			""".trimIndent(),
		)
			.param("organizationId", organizationId)
			.param("offset", offset)
			.param("limit", limit)
			.query(::mapRow)
			.list()

	fun countAll(organizationId: UUID): Long =
		jdbcClient.sql("select count(*) from store where organization_id = :organizationId")
			.param("organizationId", organizationId)
			.query(Long::class.java)
			.single()

	fun insert(organizationId: UUID, teamId: UUID?, name: String, slug: String): Store {
		val id = UUID.randomUUID()
		val now = Instant.now()
		jdbcClient.sql(
			"""
			insert into store (id, organization_id, team_id, name, slug, status, created_at, updated_at)
			values (:id, :organizationId, :teamId, :name, :slug, 'DRAFT', :now, :now)
			""".trimIndent(),
		)
			.param("id", id)
			.param("organizationId", organizationId)
			.param("teamId", teamId)
			.param("name", name)
			.param("slug", slug)
			.param("now", Timestamp.from(now))
			.update()
		return Store(id, organizationId, teamId, name, slug, StoreStatus.DRAFT, now, now)
	}

	fun updateStatus(id: UUID, organizationId: UUID, status: StoreStatus): Int {
		val now = Instant.now()
		return jdbcClient.sql("update store set status = :status, updated_at = :now where id = :id and organization_id = :organizationId")
			.param("status", status.name)
			.param("now", Timestamp.from(now))
			.param("id", id)
			.param("organizationId", organizationId)
			.update()
	}

	private fun mapRow(rs: java.sql.ResultSet, rowNum: Int): Store =
		Store(
			id = rs.getObject("id", UUID::class.java),
			organizationId = rs.getObject("organization_id", UUID::class.java),
			teamId = rs.getObject("team_id", UUID::class.java),
			name = rs.getString("name"),
			slug = rs.getString("slug"),
			status = StoreStatus.valueOf(rs.getString("status")),
			createdAt = rs.getTimestamp("created_at").toInstant(),
			updatedAt = rs.getTimestamp("updated_at").toInstant(),
		)
}
