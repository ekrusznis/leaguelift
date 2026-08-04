package com.rally26.order.persistence

import com.rally26.order.domain.FulfillmentReprint
import com.rally26.order.domain.FulfillmentReprintStatus
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

private const val REPRINT_COLUMNS = "id, organization_id, fulfillment_id, order_id, status, reason, vendor_order_reference, carrier, tracking_number, tracking_url, internal_notes, requested_by_user_id, created_at, updated_at, shipped_at, delivered_at"

@Repository
class FulfillmentReprintRepository(private val jdbcClient: JdbcClient) {
	fun findById(id: UUID, organizationId: UUID): FulfillmentReprint? = jdbcClient.sql(
		"select $REPRINT_COLUMNS from fulfillment_reprint where id = :id and organization_id = :organizationId",
	)
		.param("id", id)
		.param("organizationId", organizationId)
		.query(::mapRow)
		.optional()
		.orElse(null)

	fun listByOrder(orderId: UUID): List<FulfillmentReprint> = jdbcClient.sql(
		"select $REPRINT_COLUMNS from fulfillment_reprint where order_id = :orderId order by created_at desc",
	)
		.param("orderId", orderId)
		.query(::mapRow)
		.list()

	fun findOpenByFulfillment(fulfillmentId: UUID): FulfillmentReprint? = jdbcClient.sql(
		"select $REPRINT_COLUMNS from fulfillment_reprint where fulfillment_id = :fulfillmentId and status in ('REQUESTED', 'IN_PRODUCTION', 'SHIPPED')",
	)
		.param("fulfillmentId", fulfillmentId)
		.query(::mapRow)
		.optional()
		.orElse(null)

	fun insert(
		organizationId: UUID,
		fulfillmentId: UUID,
		orderId: UUID,
		reason: String,
		vendorOrderReference: String?,
		internalNotes: String?,
		requestedByUserId: UUID,
	): FulfillmentReprint {
		val id = UUID.randomUUID()
		val now = Instant.now()
		jdbcClient.sql(
			"""
			insert into fulfillment_reprint
				(id, organization_id, fulfillment_id, order_id, status, reason, vendor_order_reference,
				 internal_notes, requested_by_user_id, created_at, updated_at)
			values
				(:id, :organizationId, :fulfillmentId, :orderId, 'REQUESTED', :reason, :vendorOrderReference,
				 :internalNotes, :requestedByUserId, :now, :now)
			""".trimIndent(),
		)
			.param("id", id)
			.param("organizationId", organizationId)
			.param("fulfillmentId", fulfillmentId)
			.param("orderId", orderId)
			.param("reason", reason)
			.param("vendorOrderReference", vendorOrderReference)
			.param("internalNotes", internalNotes)
			.param("requestedByUserId", requestedByUserId)
			.param("now", Timestamp.from(now))
			.update()
		return findById(id, organizationId)!!
	}

	fun update(
		id: UUID,
		organizationId: UUID,
		status: FulfillmentReprintStatus,
		vendorOrderReference: String?,
		carrier: String?,
		trackingNumber: String?,
		trackingUrl: String?,
		internalNotes: String?,
	): Int {
		val now = Instant.now()
		return jdbcClient.sql(
			"""
			update fulfillment_reprint
			set status = :status, vendor_order_reference = :vendorOrderReference, carrier = :carrier,
			    tracking_number = :trackingNumber, tracking_url = :trackingUrl, internal_notes = :internalNotes,
			    shipped_at = case when :status = 'SHIPPED' then coalesce(shipped_at, :now) else shipped_at end,
			    delivered_at = case when :status = 'DELIVERED' then coalesce(delivered_at, :now) else delivered_at end,
			    updated_at = :now
			where id = :id and organization_id = :organizationId
			""".trimIndent(),
		)
			.param("status", status.name)
			.param("vendorOrderReference", vendorOrderReference)
			.param("carrier", carrier)
			.param("trackingNumber", trackingNumber)
			.param("trackingUrl", trackingUrl)
			.param("internalNotes", internalNotes)
			.param("now", Timestamp.from(now))
			.param("id", id)
			.param("organizationId", organizationId)
			.update()
	}

	private fun mapRow(rs: java.sql.ResultSet, _rowNum: Int): FulfillmentReprint = FulfillmentReprint(
		id = rs.getObject("id", UUID::class.java),
		organizationId = rs.getObject("organization_id", UUID::class.java),
		fulfillmentId = rs.getObject("fulfillment_id", UUID::class.java),
		orderId = rs.getObject("order_id", UUID::class.java),
		status = FulfillmentReprintStatus.valueOf(rs.getString("status")),
		reason = rs.getString("reason"),
		vendorOrderReference = rs.getString("vendor_order_reference"),
		carrier = rs.getString("carrier"),
		trackingNumber = rs.getString("tracking_number"),
		trackingUrl = rs.getString("tracking_url"),
		internalNotes = rs.getString("internal_notes"),
		requestedByUserId = rs.getObject("requested_by_user_id", UUID::class.java),
		createdAt = rs.getTimestamp("created_at").toInstant(),
		updatedAt = rs.getTimestamp("updated_at").toInstant(),
		shippedAt = rs.getTimestamp("shipped_at")?.toInstant(),
		deliveredAt = rs.getTimestamp("delivered_at")?.toInstant(),
	)
}
