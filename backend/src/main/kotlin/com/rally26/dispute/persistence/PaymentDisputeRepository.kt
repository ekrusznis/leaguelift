package com.rally26.dispute.persistence

import com.rally26.dispute.domain.DisputeSourceType
import com.rally26.dispute.domain.DisputeStatus
import com.rally26.dispute.domain.PaymentDispute
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

private const val COLUMNS = """
    id, organization_id, source_type, source_id, stripe_dispute_id, stripe_charge_id,
    amount_minor, currency, reason, status, evidence_due_by, opened_at, resolved_at,
    created_at, updated_at
"""

@Repository
class PaymentDisputeRepository(
    private val jdbcClient: JdbcClient,
) {
    fun insert(
        organizationId: UUID,
        sourceType: DisputeSourceType,
        sourceId: UUID,
        stripeDisputeId: String,
        stripeChargeId: String,
        amountMinor: Long,
        currency: String,
        reason: String,
        status: DisputeStatus,
        evidenceDueBy: Instant?,
        openedAt: Instant,
    ): PaymentDispute {
        val id = UUID.randomUUID()
        val now = Instant.now()
        jdbcClient
            .sql(
                """
                insert into payment_dispute
                    (id, organization_id, source_type, source_id, stripe_dispute_id, stripe_charge_id,
                     amount_minor, currency, reason, status, evidence_due_by, opened_at, created_at, updated_at)
                values
                    (:id, :organizationId, :sourceType, :sourceId, :stripeDisputeId, :stripeChargeId,
                     :amountMinor, :currency, :reason, :status, :evidenceDueBy, :openedAt, :now, :now)
                """.trimIndent(),
            ).param("id", id)
            .param("organizationId", organizationId)
            .param("sourceType", sourceType.name)
            .param("sourceId", sourceId)
            .param("stripeDisputeId", stripeDisputeId)
            .param("stripeChargeId", stripeChargeId)
            .param("amountMinor", amountMinor)
            .param("currency", currency)
            .param("reason", reason)
            .param("status", status.name)
            .param("evidenceDueBy", evidenceDueBy?.let { Timestamp.from(it) })
            .param("openedAt", Timestamp.from(openedAt))
            .param("now", Timestamp.from(now))
            .update()
        return PaymentDispute(
            id, organizationId, sourceType, sourceId, stripeDisputeId, stripeChargeId,
            amountMinor, currency, reason, status, evidenceDueBy, openedAt, null, now, now,
        )
    }

    fun findByStripeDisputeId(stripeDisputeId: String): PaymentDispute? =
        jdbcClient
            .sql("select $COLUMNS from payment_dispute where stripe_dispute_id = :stripeDisputeId")
            .param("stripeDisputeId", stripeDisputeId)
            .query(::mapRow)
            .optional()
            .orElse(null)

    fun findByOrganization(organizationId: UUID): List<PaymentDispute> =
        jdbcClient
            .sql("select $COLUMNS from payment_dispute where organization_id = :organizationId order by opened_at desc")
            .param("organizationId", organizationId)
            .query(::mapRow)
            .list()

    fun resolve(
        stripeDisputeId: String,
        status: DisputeStatus,
        resolvedAt: Instant,
    ) {
        jdbcClient
            .sql(
                "update payment_dispute set status = :status, resolved_at = :resolvedAt, updated_at = :resolvedAt " +
                    "where stripe_dispute_id = :stripeDisputeId",
            ).param("status", status.name)
            .param("resolvedAt", Timestamp.from(resolvedAt))
            .param("stripeDisputeId", stripeDisputeId)
            .update()
    }

    private fun mapRow(
        rs: ResultSet,
        rowNum: Int,
    ): PaymentDispute =
        PaymentDispute(
            id = rs.getObject("id", UUID::class.java),
            organizationId = rs.getObject("organization_id", UUID::class.java),
            sourceType = DisputeSourceType.valueOf(rs.getString("source_type")),
            sourceId = rs.getObject("source_id", UUID::class.java),
            stripeDisputeId = rs.getString("stripe_dispute_id"),
            stripeChargeId = rs.getString("stripe_charge_id"),
            amountMinor = rs.getLong("amount_minor"),
            currency = rs.getString("currency"),
            reason = rs.getString("reason"),
            status = DisputeStatus.valueOf(rs.getString("status")),
            evidenceDueBy = rs.getTimestamp("evidence_due_by")?.toInstant(),
            openedAt = rs.getTimestamp("opened_at").toInstant(),
            resolvedAt = rs.getTimestamp("resolved_at")?.toInstant(),
            createdAt = rs.getTimestamp("created_at").toInstant(),
            updatedAt = rs.getTimestamp("updated_at").toInstant(),
        )
}
