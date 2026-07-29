package com.leaguelift.order.persistence

import com.leaguelift.order.domain.Fulfillment
import com.leaguelift.order.domain.FulfillmentStatus
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

private const val COLUMNS = "id, order_id, status, printify_order_id, last_error, created_at, updated_at"

@Repository
class FulfillmentRepository(private val jdbcClient: JdbcClient) {

	fun findByOrder(orderId: UUID): Fulfillment? =
		jdbcClient.sql("select $COLUMNS from fulfillment where order_id = :orderId")
			.param("orderId", orderId)
			.query(::mapRow)
			.optional()
			.orElse(null)

	fun insert(orderId: UUID, status: FulfillmentStatus, printifyOrderId: String?, lastError: String?): Fulfillment {
		val id = UUID.randomUUID()
		val now = Instant.now()
		jdbcClient.sql(
			"""
			insert into fulfillment (id, order_id, status, printify_order_id, last_error, created_at, updated_at)
			values (:id, :orderId, :status, :printifyOrderId, :lastError, :now, :now)
			""".trimIndent(),
		)
			.param("id", id)
			.param("orderId", orderId)
			.param("status", status.name)
			.param("printifyOrderId", printifyOrderId)
			.param("lastError", lastError)
			.param("now", Timestamp.from(now))
			.update()
		return Fulfillment(id, orderId, status, printifyOrderId, lastError, now, now)
	}

	private fun mapRow(rs: java.sql.ResultSet, rowNum: Int): Fulfillment =
		Fulfillment(
			id = rs.getObject("id", UUID::class.java),
			orderId = rs.getObject("order_id", UUID::class.java),
			status = FulfillmentStatus.valueOf(rs.getString("status")),
			printifyOrderId = rs.getString("printify_order_id"),
			lastError = rs.getString("last_error"),
			createdAt = rs.getTimestamp("created_at").toInstant(),
			updatedAt = rs.getTimestamp("updated_at").toInstant(),
		)
}
