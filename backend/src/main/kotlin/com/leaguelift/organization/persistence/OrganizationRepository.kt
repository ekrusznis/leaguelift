package com.leaguelift.organization.persistence

import com.leaguelift.organization.domain.Organization
import com.leaguelift.organization.domain.OrganizationStatus
import com.leaguelift.organization.domain.OrganizationType
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

@Repository
class OrganizationRepository(private val jdbcClient: JdbcClient) {

	fun findById(id: UUID): Organization? =
		jdbcClient.sql(
			"""
			select id, name, slug, organization_type, status, created_at, updated_at
			from organization
			where id = :id
			""".trimIndent(),
		)
			.param("id", id)
			.query(::mapRow)
			.optional()
			.orElse(null)

	fun findBySlug(slug: String): Organization? =
		jdbcClient.sql(
			"""
			select id, name, slug, organization_type, status, created_at, updated_at
			from organization
			where slug = :slug
			""".trimIndent(),
		)
			.param("slug", slug)
			.query(::mapRow)
			.optional()
			.orElse(null)

	/**
	 * Organizations the given user has an ACTIVE membership in. This is the
	 * organization-isolation boundary at the query level (DESIGN-DOC.md section
	 * 14.1) — callers must use this rather than a plain `select * from organization`.
	 */
	fun findForUser(userId: UUID, offset: Int, limit: Int): List<Organization> =
		jdbcClient.sql(
			"""
			select o.id, o.name, o.slug, o.organization_type, o.status, o.created_at, o.updated_at
			from organization o
			join organization_membership m on m.organization_id = o.id
			where m.user_id = :userId and m.status = 'ACTIVE'
			order by o.created_at desc
			offset :offset limit :limit
			""".trimIndent(),
		)
			.param("userId", userId)
			.param("offset", offset)
			.param("limit", limit)
			.query(::mapRow)
			.list()

	fun countForUser(userId: UUID): Long =
		jdbcClient.sql(
			"""
			select count(*) from organization o
			join organization_membership m on m.organization_id = o.id
			where m.user_id = :userId and m.status = 'ACTIVE'
			""".trimIndent(),
		)
			.param("userId", userId)
			.query(Long::class.java)
			.single()

	fun insert(name: String, slug: String, organizationType: OrganizationType): Organization {
		val now = Instant.now()
		val id = UUID.randomUUID()
		jdbcClient.sql(
			"""
			insert into organization (id, name, slug, organization_type, status, created_at, updated_at)
			values (:id, :name, :slug, :organizationType, 'ACTIVE', :now, :now)
			""".trimIndent(),
		)
			.param("id", id)
			.param("name", name)
			.param("slug", slug)
			.param("organizationType", organizationType.name)
			.param("now", now)
			.update()
		return Organization(id, name, slug, organizationType, OrganizationStatus.ACTIVE, now, now)
	}

	fun updateNameAndType(id: UUID, name: String?, organizationType: OrganizationType?): Int {
		val now = Instant.now()
		return jdbcClient.sql(
			"""
			update organization
			set name = coalesce(:name, name),
			    organization_type = coalesce(:organizationType, organization_type),
			    updated_at = :now
			where id = :id
			""".trimIndent(),
		)
			.param("name", name)
			.param("organizationType", organizationType?.name)
			.param("now", now)
			.param("id", id)
			.update()
	}

	private fun mapRow(rs: java.sql.ResultSet, rowNum: Int): Organization =
		Organization(
			id = rs.getObject("id", UUID::class.java),
			name = rs.getString("name"),
			slug = rs.getString("slug"),
			organizationType = OrganizationType.valueOf(rs.getString("organization_type")),
			status = OrganizationStatus.valueOf(rs.getString("status")),
			createdAt = rs.getTimestamp("created_at").toInstant(),
			updatedAt = rs.getTimestamp("updated_at").toInstant(),
		)
}
