package com.rally26.document.persistence

import com.rally26.document.domain.DocumentAcknowledgment
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

private const val COLUMNS =
	"id, organization_id, media_assignment_id, household_id, household_adult_id, acknowledged_by_user_id, acknowledged_at, created_at"

@Repository
class DocumentAcknowledgmentRepository(private val jdbcClient: JdbcClient) {

	fun findFor(mediaAssignmentId: UUID, householdAdultId: UUID): DocumentAcknowledgment? =
		jdbcClient.sql(
			"select $COLUMNS from document_acknowledgment where media_assignment_id = :mediaAssignmentId and household_adult_id = :householdAdultId",
		)
			.param("mediaAssignmentId", mediaAssignmentId)
			.param("householdAdultId", householdAdultId)
			.query(::mapRow)
			.optional()
			.orElse(null)

	fun listForAssignment(mediaAssignmentId: UUID): List<DocumentAcknowledgment> =
		jdbcClient.sql("select $COLUMNS from document_acknowledgment where media_assignment_id = :mediaAssignmentId order by acknowledged_at asc")
			.param("mediaAssignmentId", mediaAssignmentId)
			.query(::mapRow)
			.list()

	fun insert(
		organizationId: UUID,
		mediaAssignmentId: UUID,
		householdId: UUID,
		householdAdultId: UUID,
		acknowledgedByUserId: UUID,
	): DocumentAcknowledgment {
		val now = Instant.now()
		val id = UUID.randomUUID()
		jdbcClient.sql(
			"""
			insert into document_acknowledgment
			    (id, organization_id, media_assignment_id, household_id, household_adult_id, acknowledged_by_user_id, acknowledged_at, created_at)
			values
			    (:id, :organizationId, :mediaAssignmentId, :householdId, :householdAdultId, :acknowledgedByUserId, :now, :now)
			""".trimIndent(),
		)
			.param("id", id)
			.param("organizationId", organizationId)
			.param("mediaAssignmentId", mediaAssignmentId)
			.param("householdId", householdId)
			.param("householdAdultId", householdAdultId)
			.param("acknowledgedByUserId", acknowledgedByUserId)
			.param("now", Timestamp.from(now))
			.update()
		return DocumentAcknowledgment(id, organizationId, mediaAssignmentId, householdId, householdAdultId, acknowledgedByUserId, now, now)
	}

	private fun mapRow(rs: ResultSet, rowNum: Int): DocumentAcknowledgment =
		DocumentAcknowledgment(
			id = rs.getObject("id", UUID::class.java),
			organizationId = rs.getObject("organization_id", UUID::class.java),
			mediaAssignmentId = rs.getObject("media_assignment_id", UUID::class.java),
			householdId = rs.getObject("household_id", UUID::class.java),
			householdAdultId = rs.getObject("household_adult_id", UUID::class.java),
			acknowledgedByUserId = rs.getObject("acknowledged_by_user_id", UUID::class.java),
			acknowledgedAt = rs.getTimestamp("acknowledged_at").toInstant(),
			createdAt = rs.getTimestamp("created_at").toInstant(),
		)
}
