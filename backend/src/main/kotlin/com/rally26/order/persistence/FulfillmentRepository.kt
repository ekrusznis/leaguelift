package com.rally26.order.persistence

import com.rally26.order.domain.Fulfillment
import com.rally26.order.domain.FulfillmentSource
import com.rally26.order.domain.FulfillmentStatus
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

private const val FULFILLMENT_COLUMNS = """
    f.id, f.order_id, f.source, f.status, f.printify_order_id, f.manual_vendor_id,
    mv.name as manual_vendor_name, f.vendor_order_reference, f.carrier, f.tracking_number,
    f.tracking_url, f.internal_notes, f.attention_reason, f.last_error, f.status_changed_at,
    f.shipped_at, f.delivered_at, f.created_at, f.updated_at
"""

@Repository
class FulfillmentRepository(
    private val jdbcClient: JdbcClient,
) {
    fun findByOrder(orderId: UUID): Fulfillment? =
        jdbcClient
            .sql(
                """
                select $FULFILLMENT_COLUMNS from fulfillment f
                left join manual_vendor mv on mv.id = f.manual_vendor_id
                where f.order_id = :orderId
                """.trimIndent(),
            ).param("orderId", orderId)
            .query(::mapRow)
            .optional()
            .orElse(null)

    fun insert(
        orderId: UUID,
        source: FulfillmentSource,
        status: FulfillmentStatus,
        printifyOrderId: String?,
        manualVendorId: UUID?,
        lastError: String?,
    ): Fulfillment {
        val id = UUID.randomUUID()
        val now = Instant.now()
        val attentionReason = if (status == FulfillmentStatus.NEEDS_ATTENTION) lastError ?: "Manual review required." else null
        jdbcClient
            .sql(
                """
                insert into fulfillment
                	(id, order_id, source, status, printify_order_id, manual_vendor_id, last_error,
                	 attention_reason, status_changed_at, created_at, updated_at)
                values
                	(:id, :orderId, :source, :status, :printifyOrderId, :manualVendorId, :lastError,
                	 :attentionReason, :now, :now, :now)
                """.trimIndent(),
            ).param("id", id)
            .param("orderId", orderId)
            .param("source", source.name)
            .param("status", status.name)
            .param("printifyOrderId", printifyOrderId)
            .param("manualVendorId", manualVendorId)
            .param("lastError", lastError)
            .param("attentionReason", attentionReason)
            .param("now", Timestamp.from(now))
            .update()
        return findByOrder(orderId)!!
    }

    fun updateOperationalState(
        id: UUID,
        status: FulfillmentStatus,
        manualVendorId: UUID?,
        vendorOrderReference: String?,
        carrier: String?,
        trackingNumber: String?,
        trackingUrl: String?,
        internalNotes: String?,
        attentionReason: String?,
    ): Int {
        val now = Instant.now()
        val shippedAt = if (status == FulfillmentStatus.SHIPPED) Timestamp.from(now) else null
        val deliveredAt = if (status == FulfillmentStatus.DELIVERED) Timestamp.from(now) else null
        return jdbcClient
            .sql(
                """
                update fulfillment
                set status = :status,
                    manual_vendor_id = :manualVendorId,
                    vendor_order_reference = :vendorOrderReference,
                    carrier = :carrier,
                    tracking_number = :trackingNumber,
                    tracking_url = :trackingUrl,
                    internal_notes = :internalNotes,
                    attention_reason = :attentionReason,
                    status_changed_at = case when status <> :status then :now else status_changed_at end,
                    shipped_at = case
                        when :status = 'SHIPPED' then coalesce(shipped_at, :shippedAt)
                        when :status in ('READY', 'IN_PRODUCTION', 'NEEDS_ATTENTION', 'CANCELED') then null
                        else shipped_at end,
                    delivered_at = case when :status = 'DELIVERED' then coalesce(delivered_at, :deliveredAt) else delivered_at end,
                    updated_at = :now
                where id = :id
                """.trimIndent(),
            ).param("status", status.name)
            .param("manualVendorId", manualVendorId)
            .param("vendorOrderReference", vendorOrderReference)
            .param("carrier", carrier)
            .param("trackingNumber", trackingNumber)
            .param("trackingUrl", trackingUrl)
            .param("internalNotes", internalNotes)
            .param("attentionReason", attentionReason)
            .param("now", Timestamp.from(now))
            .param("shippedAt", shippedAt)
            .param("deliveredAt", deliveredAt)
            .param("id", id)
            .update()
    }

    fun countExceptionsByOrganization(organizationId: UUID): Long =
        jdbcClient
            .sql(
                """
                select count(*)
                from fulfillment f
                join "order" o on o.id = f.order_id
                where o.organization_id = :organizationId
                  and f.status in ('FAILED', 'NEEDS_ATTENTION')
                """.trimIndent(),
            ).param("organizationId", organizationId)
            .query(Long::class.java)
            .single()

    private fun mapRow(
        rs: java.sql.ResultSet,
        _rowNum: Int,
    ): Fulfillment =
        Fulfillment(
            id = rs.getObject("id", UUID::class.java),
            orderId = rs.getObject("order_id", UUID::class.java),
            source = FulfillmentSource.valueOf(rs.getString("source")),
            status = FulfillmentStatus.valueOf(rs.getString("status")),
            printifyOrderId = rs.getString("printify_order_id"),
            manualVendorId = rs.getObject("manual_vendor_id", UUID::class.java),
            manualVendorName = rs.getString("manual_vendor_name"),
            vendorOrderReference = rs.getString("vendor_order_reference"),
            carrier = rs.getString("carrier"),
            trackingNumber = rs.getString("tracking_number"),
            trackingUrl = rs.getString("tracking_url"),
            internalNotes = rs.getString("internal_notes"),
            attentionReason = rs.getString("attention_reason"),
            lastError = rs.getString("last_error"),
            statusChangedAt = rs.getTimestamp("status_changed_at").toInstant(),
            shippedAt = rs.getTimestamp("shipped_at")?.toInstant(),
            deliveredAt = rs.getTimestamp("delivered_at")?.toInstant(),
            createdAt = rs.getTimestamp("created_at").toInstant(),
            updatedAt = rs.getTimestamp("updated_at").toInstant(),
        )
}
