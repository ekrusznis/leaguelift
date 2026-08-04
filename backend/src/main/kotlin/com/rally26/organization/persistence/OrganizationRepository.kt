package com.rally26.organization.persistence

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.rally26.organization.domain.Organization
import com.rally26.organization.domain.OrganizationStatus
import com.rally26.organization.domain.OrganizationType
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

private const val ORGANIZATION_COLUMNS =
	"id, name, slug, organization_type, status, sports, contact_email, contact_phone, created_at, updated_at"

@Repository
class OrganizationRepository(
	private val jdbcClient: JdbcClient,
	private val objectMapper: ObjectMapper,
) {

	fun findById(id: UUID): Organization? =
		jdbcClient.sql("select $ORGANIZATION_COLUMNS from organization where id = :id")
			.param("id", id)
			.query(::mapRow)
			.optional()
			.orElse(null)

	fun findBySlug(slug: String): Organization? =
		jdbcClient.sql("select $ORGANIZATION_COLUMNS from organization where slug = :slug")
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
			select o.id, o.name, o.slug, o.organization_type, o.status, o.sports,
			       o.contact_email, o.contact_phone, o.created_at, o.updated_at
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

	/**
	 * Every organization on the platform, regardless of the caller's membership —
	 * unlike [findForUser], which is the organization-isolation boundary every
	 * org-scoped feature must use. Only for the Platform Admin dashboard/context,
	 * which is gated by [com.rally26.authorization.application.AuthorizationService]
	 * requiring a real `platform.organization.view` capability, never by omission of a
	 * membership filter alone (DESIGN-DOC.md section 7.2 — platform access is a
	 * separate permission, checked at the service layer, not inferred from which query
	 * ran).
	 */
	fun findAllForPlatformAdmin(offset: Int, limit: Int): List<Organization> =
		jdbcClient.sql(
			"""
			select $ORGANIZATION_COLUMNS from organization
			order by created_at desc
			offset :offset limit :limit
			""".trimIndent(),
		)
			.param("offset", offset)
			.param("limit", limit)
			.query(::mapRow)
			.list()

	fun countAllForPlatformAdmin(): Long =
		jdbcClient.sql("select count(*) from organization").query(Long::class.java).single()

	fun insert(name: String, slug: String, organizationType: OrganizationType): Organization {
		val now = Instant.now()
		val id = UUID.randomUUID()
		jdbcClient.sql(
			"""
			insert into organization (id, name, slug, organization_type, status, sports, created_at, updated_at)
			values (:id, :name, :slug, :organizationType, 'ACTIVE', '[]'::jsonb, :now, :now)
			""".trimIndent(),
		)
			.param("id", id)
			.param("name", name)
			.param("slug", slug)
			.param("organizationType", organizationType.name)
			.param("now", Timestamp.from(now))
			.update()
		return Organization(
			id = id,
			name = name,
			slug = slug,
			organizationType = organizationType,
			status = OrganizationStatus.ACTIVE,
			sports = emptyList(),
			contactEmail = null,
			contactPhone = null,
			createdAt = now,
			updatedAt = now,
		)
	}

	/**
	 * Partial update: any parameter left null keeps the existing column value (same
	 * "null means don't change" convention as the original name/type update). There is
	 * currently no way to explicitly clear contactEmail/contactPhone/sports back to
	 * empty via this method — acceptable for this slice; revisit with an explicit
	 * "clear" signal (e.g. a sealed Patch<T> wrapper) if that need shows up.
	 */
	fun updateProfile(
		id: UUID,
		name: String?,
		organizationType: OrganizationType?,
		sports: List<String>?,
		contactEmail: String?,
		contactPhone: String?,
	): Int {
		val now = Instant.now()
		val sportsJson = sports?.let { objectMapper.writeValueAsString(it) }
		return jdbcClient.sql(
			"""
			update organization
			set name = coalesce(:name, name),
			    organization_type = coalesce(:organizationType, organization_type),
			    sports = coalesce(cast(:sports as jsonb), sports),
			    contact_email = coalesce(:contactEmail, contact_email),
			    contact_phone = coalesce(:contactPhone, contact_phone),
			    updated_at = :now
			where id = :id
			""".trimIndent(),
		)
			.param("name", name)
			.param("organizationType", organizationType?.name)
			.param("sports", sportsJson)
			.param("contactEmail", contactEmail)
			.param("contactPhone", contactPhone)
			.param("now", Timestamp.from(now))
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
			sports = rs.getString("sports")?.let { objectMapper.readValue<List<String>>(it) } ?: emptyList(),
			contactEmail = rs.getString("contact_email"),
			contactPhone = rs.getString("contact_phone"),
			createdAt = rs.getTimestamp("created_at").toInstant(),
			updatedAt = rs.getTimestamp("updated_at").toInstant(),
		)
}
