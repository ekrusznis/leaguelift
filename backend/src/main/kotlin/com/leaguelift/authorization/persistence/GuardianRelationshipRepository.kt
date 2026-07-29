package com.leaguelift.authorization.persistence

import com.leaguelift.authorization.domain.GuardianRelationship
import com.leaguelift.authorization.domain.GuardianRelationshipStatus
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

private const val COLUMNS =
	"id, organization_id, household_id, household_adult_id, user_id, status, created_at, updated_at"

@Repository
class GuardianRelationshipRepository(private val jdbcClient: JdbcClient) {

	fun findActiveForUser(userId: UUID): List<GuardianRelationship> =
		jdbcClient.sql("select $COLUMNS from guardian_relationship where user_id = :userId and status = 'ACTIVE'")
			.param("userId", userId)
			.query(::mapRow)
			.list()

	fun findActiveForHousehold(userId: UUID, householdId: UUID): GuardianRelationship? =
		jdbcClient.sql(
			"""
			select $COLUMNS from guardian_relationship
			where user_id = :userId and household_id = :householdId and status = 'ACTIVE'
			""".trimIndent(),
		)
			.param("userId", userId)
			.param("householdId", householdId)
			.query(::mapRow)
			.optional()
			.orElse(null)

	fun insert(organizationId: UUID, householdId: UUID, householdAdultId: UUID, userId: UUID): GuardianRelationship {
		val now = Instant.now()
		val id = UUID.randomUUID()
		jdbcClient.sql(
			"""
			insert into guardian_relationship (id, organization_id, household_id, household_adult_id, user_id, status, created_at, updated_at)
			values (:id, :organizationId, :householdId, :householdAdultId, :userId, 'ACTIVE', :now, :now)
			""".trimIndent(),
		)
			.param("id", id)
			.param("organizationId", organizationId)
			.param("householdId", householdId)
			.param("householdAdultId", householdAdultId)
			.param("userId", userId)
			.param("now", Timestamp.from(now))
			.update()
		return GuardianRelationship(id, organizationId, householdId, householdAdultId, userId, GuardianRelationshipStatus.ACTIVE, now, now)
	}

	private fun mapRow(rs: java.sql.ResultSet, rowNum: Int): GuardianRelationship =
		GuardianRelationship(
			id = rs.getObject("id", UUID::class.java),
			organizationId = rs.getObject("organization_id", UUID::class.java),
			householdId = rs.getObject("household_id", UUID::class.java),
			householdAdultId = rs.getObject("household_adult_id", UUID::class.java),
			userId = rs.getObject("user_id", UUID::class.java),
			status = GuardianRelationshipStatus.valueOf(rs.getString("status")),
			createdAt = rs.getTimestamp("created_at").toInstant(),
			updatedAt = rs.getTimestamp("updated_at").toInstant(),
		)
}
