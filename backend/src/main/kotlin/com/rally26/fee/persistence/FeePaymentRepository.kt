package com.rally26.fee.persistence

import com.rally26.fee.domain.FeePayment
import com.rally26.fee.domain.FeePaymentStatus
import com.rally26.fee.domain.PaymentMethod
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.Date
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

private const val COLUMNS = """
    id, organization_id, fee_assignment_id, household_id, amount_minor, currency, method,
    paid_at, note, recorded_by_user_id, voided_at, voided_by_user_id, void_reason, created_at,
    status, stripe_checkout_session_id, stripe_payment_intent_id, payer_email, payer_name
"""

@Repository
class FeePaymentRepository(
    private val jdbcClient: JdbcClient,
) {
    fun findById(
        id: UUID,
        organizationId: UUID,
    ): FeePayment? =
        jdbcClient
            .sql("select $COLUMNS from fee_payment where id = :id and organization_id = :organizationId")
            .param("id", id)
            .param("organizationId", organizationId)
            .query(::mapRow)
            .optional()
            .orElse(null)

    fun findAllByAssignment(
        feeAssignmentId: UUID,
        organizationId: UUID,
    ): List<FeePayment> =
        jdbcClient
            .sql(
                """
                select $COLUMNS from fee_payment
                where fee_assignment_id = :feeAssignmentId and organization_id = :organizationId
                order by paid_at desc, created_at desc
                """.trimIndent(),
            ).param("feeAssignmentId", feeAssignmentId)
            .param("organizationId", organizationId)
            .query(::mapRow)
            .list()

    /** Excludes PENDING_CHECKOUT (an online payment not yet confirmed by Stripe's webhook) and CANCELED, alongside the existing voided-row exclusion — a pending or abandoned checkout attempt must never affect the household's outstanding balance. */
    fun sumActiveByAssignment(
        feeAssignmentId: UUID,
        organizationId: UUID,
    ): Long =
        jdbcClient
            .sql(
                """
                select coalesce(sum(amount_minor), 0) from fee_payment
                where fee_assignment_id = :feeAssignmentId and organization_id = :organizationId
                    and voided_at is null and status = 'CONFIRMED'
                """.trimIndent(),
            ).param("feeAssignmentId", feeAssignmentId)
            .param("organizationId", organizationId)
            .query(Long::class.java)
            .single()

    fun findByStripeCheckoutSessionId(sessionId: String): FeePayment? =
        jdbcClient
            .sql("select $COLUMNS from fee_payment where stripe_checkout_session_id = :sessionId")
            .param("sessionId", sessionId)
            .query(::mapRow)
            .optional()
            .orElse(null)

    /** Inserted with status PENDING_CHECKOUT before Stripe returns a session id — see [attachStripeSession] and [markConfirmed]. */
    fun insertPendingOnline(
        organizationId: UUID,
        feeAssignmentId: UUID,
        householdId: UUID,
        amountMinor: Long,
        currency: String,
        paidAt: LocalDate,
        recordedByUserId: UUID,
        payerEmail: String,
        payerName: String,
    ): FeePayment {
        val now = Instant.now()
        val id = UUID.randomUUID()
        jdbcClient
            .sql(
                """
                insert into fee_payment
                    (id, organization_id, fee_assignment_id, household_id, amount_minor, currency, method,
                     paid_at, recorded_by_user_id, created_at, status, payer_email, payer_name)
                values
                    (:id, :organizationId, :feeAssignmentId, :householdId, :amountMinor, :currency, 'STRIPE_ONLINE',
                     :paidAt, :recordedByUserId, :now, 'PENDING_CHECKOUT', :payerEmail, :payerName)
                """.trimIndent(),
            ).param("id", id)
            .param("organizationId", organizationId)
            .param("feeAssignmentId", feeAssignmentId)
            .param("householdId", householdId)
            .param("amountMinor", amountMinor)
            .param("currency", currency)
            .param("paidAt", Date.valueOf(paidAt))
            .param("recordedByUserId", recordedByUserId)
            .param("now", Timestamp.from(now))
            .param("payerEmail", payerEmail)
            .param("payerName", payerName)
            .update()
        return FeePayment(
            id = id,
            organizationId = organizationId,
            feeAssignmentId = feeAssignmentId,
            householdId = householdId,
            amountMinor = amountMinor,
            currency = currency,
            method = PaymentMethod.STRIPE_ONLINE,
            paidAt = paidAt,
            note = null,
            recordedByUserId = recordedByUserId,
            voidedAt = null,
            voidedByUserId = null,
            voidReason = null,
            createdAt = now,
            status = FeePaymentStatus.PENDING_CHECKOUT,
            payerEmail = payerEmail,
            payerName = payerName,
        )
    }

    fun attachStripeSession(
        id: UUID,
        stripeCheckoutSessionId: String,
    ): Int =
        jdbcClient
            .sql("update fee_payment set stripe_checkout_session_id = :sessionId where id = :id")
            .param("sessionId", stripeCheckoutSessionId)
            .param("id", id)
            .update()

    /** Only flips PENDING_CHECKOUT -> CONFIRMED. Returns rows affected (0 if already confirmed — the idempotency guard). */
    fun markConfirmed(
        id: UUID,
        stripePaymentIntentId: String?,
    ): Int =
        jdbcClient
            .sql(
                """
                update fee_payment set status = 'CONFIRMED', stripe_payment_intent_id = :paymentIntentId
                where id = :id and status = 'PENDING_CHECKOUT'
                """.trimIndent(),
            ).param("paymentIntentId", stripePaymentIntentId)
            .param("id", id)
            .update()

    fun insert(
        organizationId: UUID,
        feeAssignmentId: UUID,
        householdId: UUID,
        amountMinor: Long,
        currency: String,
        method: PaymentMethod,
        paidAt: LocalDate,
        note: String?,
        recordedByUserId: UUID,
    ): FeePayment {
        val now = Instant.now()
        val id = UUID.randomUUID()
        jdbcClient
            .sql(
                """
                insert into fee_payment
                    (id, organization_id, fee_assignment_id, household_id, amount_minor, currency, method,
                     paid_at, note, recorded_by_user_id, created_at)
                values
                    (:id, :organizationId, :feeAssignmentId, :householdId, :amountMinor, :currency, :method,
                     :paidAt, :note, :recordedByUserId, :now)
                """.trimIndent(),
            ).param("id", id)
            .param("organizationId", organizationId)
            .param("feeAssignmentId", feeAssignmentId)
            .param("householdId", householdId)
            .param("amountMinor", amountMinor)
            .param("currency", currency)
            .param("method", method.name)
            .param("paidAt", Date.valueOf(paidAt))
            .param("note", note)
            .param("recordedByUserId", recordedByUserId)
            .param("now", Timestamp.from(now))
            .update()
        return FeePayment(
            id,
            organizationId,
            feeAssignmentId,
            householdId,
            amountMinor,
            currency,
            method,
            paidAt,
            note,
            recordedByUserId,
            voidedAt = null,
            voidedByUserId = null,
            voidReason = null,
            createdAt = now,
        )
    }

    fun void(
        id: UUID,
        organizationId: UUID,
        voidedByUserId: UUID,
        voidReason: String,
    ): Int {
        val now = Instant.now()
        return jdbcClient
            .sql(
                """
                update fee_payment
                set voided_at = :now, voided_by_user_id = :voidedByUserId, void_reason = :voidReason
                where id = :id and organization_id = :organizationId and voided_at is null
                """.trimIndent(),
            ).param("now", Timestamp.from(now))
            .param("voidedByUserId", voidedByUserId)
            .param("voidReason", voidReason)
            .param("id", id)
            .param("organizationId", organizationId)
            .update()
    }

    private fun mapRow(
        rs: ResultSet,
        rowNum: Int,
    ): FeePayment =
        FeePayment(
            id = rs.getObject("id", UUID::class.java),
            organizationId = rs.getObject("organization_id", UUID::class.java),
            feeAssignmentId = rs.getObject("fee_assignment_id", UUID::class.java),
            householdId = rs.getObject("household_id", UUID::class.java),
            amountMinor = rs.getLong("amount_minor"),
            currency = rs.getString("currency"),
            method = PaymentMethod.valueOf(rs.getString("method")),
            paidAt = rs.getDate("paid_at").toLocalDate(),
            note = rs.getString("note"),
            recordedByUserId = rs.getObject("recorded_by_user_id", UUID::class.java),
            voidedAt = rs.getTimestamp("voided_at")?.toInstant(),
            voidedByUserId = rs.getObject("voided_by_user_id", UUID::class.java),
            voidReason = rs.getString("void_reason"),
            createdAt = rs.getTimestamp("created_at").toInstant(),
            status = FeePaymentStatus.valueOf(rs.getString("status")),
            stripeCheckoutSessionId = rs.getString("stripe_checkout_session_id"),
            stripePaymentIntentId = rs.getString("stripe_payment_intent_id"),
            payerEmail = rs.getString("payer_email"),
            payerName = rs.getString("payer_name"),
        )
}
