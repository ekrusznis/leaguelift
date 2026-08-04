package com.rally26.order.persistence

import com.rally26.order.domain.FulfillmentHistory
import com.rally26.order.domain.FulfillmentStatus
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

@Repository
class FulfillmentHistoryRepository(private val jdbcClient: JdbcClient) {
	fun insert(
		organizationId: UUID,
		fulfillmentId: UUID,
		previousStatus: FulfillmentStatus?,
		newStatus: FulfillmentStatus,
		note: String,
		actorUserId: UUID?,
	): FulfillmentHistory {
		val id = UUID.randomUUID()
		val now = Instant.now()
		jdbcClient.sql(
			"""
			insert into fulfillment_history
				(id, organization_id, fulfillment_id, previous_status, new_status, note, actor_user_id, created_at)
			values
				(:id, :organizationId, :fulfillmentId, :previousStatus, :newStatus, :note, :actorUserId, :now)
			""".trimIndent(),
		)
			.param("id", id)
			.param("organizationId", organizationId)
			.param("fulfillmentId", fulfillmentId)
			.param("previousStatus", previousStatus?.name)
			.param("newStatus", newStatus.name)
			.param("note", note)
			.param("actorUserId", actorUserId)
			.param("now", Timestamp.from(now))
			.update()
		return FulfillmentHistory(id, organizationId, fulfillmentId, previousStatus, newStatus, note, actorUserId, now)
	}

	fun listByFulfillment(fulfillmentId: UUID): List<FulfillmentHistory> = jdbcClient.sql(
		"""
		select id, organization_id, fulfillment_id, previous_status, new_status, note, actor_user_id, created_at
		from fulfillment_history where fulfillment_id = :fulfillmentId order by created_at desc, id desc
		""".trimIndent(),
	)
		.param("fulfillmentId", fulfillmentId)
		.query { rs, _ ->
			FulfillmentHistory(
				id = rs.getObject("id", UUID::class.java),
				organizationId = rs.getObject("organization_id", UUID::class.java),
				fulfillmentId = rs.getObject("fulfillment_id", UUID::class.java),
				previousStatus = rs.getString("previous_status")?.let(FulfillmentStatus::valueOf),
				newStatus = FulfillmentStatus.valueOf(rs.getString("new_status")),
				note = rs.getString("note"),
				actorUserId = rs.getObject("actor_user_id", UUID::class.java),
				createdAt = rs.getTimestamp("created_at").toInstant(),
			)
		}
		.list()
}
