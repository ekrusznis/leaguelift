package com.leaguelift.sponsorship.persistence

import com.leaguelift.sponsorship.domain.Sponsor
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

private const val COLUMNS = "id, organization_id, name, contact_email, created_at, updated_at"

@Repository
class SponsorRepository(private val jdbcClient: JdbcClient) {

	fun findById(id: UUID): Sponsor? =
		jdbcClient.sql("select $COLUMNS from sponsor where id = :id")
			.param("id", id)
			.query(::mapRow)
			.optional()
			.orElse(null)

	fun findById(id: UUID, organizationId: UUID): Sponsor? =
		jdbcClient.sql("select $COLUMNS from sponsor where id = :id and organization_id = :organizationId")
			.param("id", id)
			.param("organizationId", organizationId)
			.query(::mapRow)
			.optional()
			.orElse(null)

	/** Inserted inline by `SponsorshipService.createCheckoutSession` — see `Sponsor`'s class doc for why there's no separate manual-entry admin flow this slice. */
	fun insert(organizationId: UUID, name: String, contactEmail: String?): Sponsor {
		val id = UUID.randomUUID()
		val now = Instant.now()
		jdbcClient.sql(
			"""
			insert into sponsor (id, organization_id, name, contact_email, created_at, updated_at)
			values (:id, :organizationId, :name, :contactEmail, :now, :now)
			""".trimIndent(),
		)
			.param("id", id)
			.param("organizationId", organizationId)
			.param("name", name)
			.param("contactEmail", contactEmail)
			.param("now", Timestamp.from(now))
			.update()
		return Sponsor(id, organizationId, name, contactEmail, now, now)
	}

	private fun mapRow(rs: java.sql.ResultSet, rowNum: Int): Sponsor =
		Sponsor(
			id = rs.getObject("id", UUID::class.java),
			organizationId = rs.getObject("organization_id", UUID::class.java),
			name = rs.getString("name"),
			contactEmail = rs.getString("contact_email"),
			createdAt = rs.getTimestamp("created_at").toInstant(),
			updatedAt = rs.getTimestamp("updated_at").toInstant(),
		)
}
