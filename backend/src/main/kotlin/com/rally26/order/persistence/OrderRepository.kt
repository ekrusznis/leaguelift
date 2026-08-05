package com.rally26.order.persistence

import com.fasterxml.jackson.databind.ObjectMapper
import com.rally26.finance.domain.PaymentSource
import com.rally26.order.domain.Order
import com.rally26.order.domain.OrderStatus
import com.rally26.order.domain.ShippingAddress
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

private const val COLUMNS = """
    id, organization_id, store_id, status, currency, supporter_name, supporter_email,
    shipping_address, stripe_checkout_session_id, stripe_payment_intent_id, confirmed_at,
    refunded_at, created_at, payment_source
"""

@Repository
class OrderRepository(
    private val jdbcClient: JdbcClient,
    private val objectMapper: ObjectMapper,
) {
    fun findById(
        id: UUID,
        organizationId: UUID,
    ): Order? =
        jdbcClient
            .sql("""select $COLUMNS from "order" where id = :id and organization_id = :organizationId""")
            .param("id", id)
            .param("organizationId", organizationId)
            .query(::mapRow)
            .optional()
            .orElse(null)

    fun findByStripeCheckoutSessionId(sessionId: String): Order? =
        jdbcClient
            .sql("""select $COLUMNS from "order" where stripe_checkout_session_id = :sessionId""")
            .param("sessionId", sessionId)
            .query(::mapRow)
            .optional()
            .orElse(null)

    /** "Confirmed" here means "was ever confirmed" — a later refund still shows in this admin-facing history, just with status REFUNDED. */
    fun findByStore(
        storeId: UUID,
        offset: Int,
        limit: Int,
    ): List<Order> =
        jdbcClient
            .sql(
                """
                select $COLUMNS from "order"
                where store_id = :storeId and status in ('CONFIRMED', 'REFUNDED')
                order by confirmed_at desc
                offset :offset limit :limit
                """.trimIndent(),
            ).param("storeId", storeId)
            .param("offset", offset)
            .param("limit", limit)
            .query(::mapRow)
            .list()

    /** Platform-wide order counts by status (Platform Admin dashboard, Phase 7 completion) — no organization filter, unlike every other query here. */
    fun countAllByStatus(): Map<String, Long> =
        jdbcClient
            .sql("select status, count(*) as cnt from \"order\" group by status")
            .query { rs, _ -> rs.getString("status") to rs.getLong("cnt") }
            .list()
            .toMap()

    fun countConfirmedByStore(storeId: UUID): Long =
        jdbcClient
            .sql("""select count(*) from "order" where store_id = :storeId and status in ('CONFIRMED', 'REFUNDED')""")
            .param("storeId", storeId)
            .query(Long::class.java)
            .single()

    fun insertPending(
        organizationId: UUID,
        storeId: UUID,
        currency: String,
        supporterName: String?,
        supporterEmail: String?,
    ): Order {
        val id = UUID.randomUUID()
        val now = Instant.now()
        jdbcClient
            .sql(
                """
                insert into "order" (id, organization_id, store_id, status, currency, supporter_name, supporter_email, created_at)
                values (:id, :organizationId, :storeId, 'PENDING', :currency, :supporterName, :supporterEmail, :now)
                """.trimIndent(),
            ).param("id", id)
            .param("organizationId", organizationId)
            .param("storeId", storeId)
            .param("currency", currency)
            .param("supporterName", supporterName)
            .param("supporterEmail", supporterEmail)
            .param("now", Timestamp.from(now))
            .update()
        return Order(
            id,
            organizationId,
            storeId,
            OrderStatus.PENDING,
            currency,
            supporterName,
            supporterEmail,
            null,
            null,
            null,
            null,
            null,
            now,
        )
    }

    fun attachStripeSession(
        id: UUID,
        stripeCheckoutSessionId: String,
    ): Int =
        jdbcClient
            .sql("""update "order" set stripe_checkout_session_id = :sessionId where id = :id""")
            .param("sessionId", stripeCheckoutSessionId)
            .param("id", id)
            .update()

    fun insertOfflinePending(
        organizationId: UUID,
        storeId: UUID,
        currency: String,
        supporterName: String?,
        supporterEmail: String?,
        shippingAddress: ShippingAddress?,
    ): Order {
        val id = UUID.randomUUID()
        val now = Instant.now()
        jdbcClient
            .sql(
                """
                insert into "order"
                	(id, organization_id, store_id, status, currency, supporter_name, supporter_email,
                	 shipping_address, payment_source, created_at)
                values
                	(:id, :organizationId, :storeId, 'PENDING', :currency, :supporterName, :supporterEmail,
                	 cast(:shippingAddress as jsonb), 'OFFLINE', :now)
                """.trimIndent(),
            ).param("id", id)
            .param("organizationId", organizationId)
            .param("storeId", storeId)
            .param("currency", currency)
            .param("supporterName", supporterName)
            .param("supporterEmail", supporterEmail)
            .param("shippingAddress", shippingAddress?.let { objectMapper.writeValueAsString(it) })
            .param("now", Timestamp.from(now))
            .update()
        return findById(id, organizationId)!!
    }

    fun markOfflineConfirmed(
        id: UUID,
        confirmedAt: Instant,
    ): Int =
        jdbcClient
            .sql(
                """
                update "order" set status = 'CONFIRMED', confirmed_at = :confirmedAt
                where id = :id and status = 'PENDING' and payment_source = 'OFFLINE'
                """.trimIndent(),
            ).param("confirmedAt", Timestamp.from(confirmedAt))
            .param("id", id)
            .update()

    /** Only flips PENDING -> CONFIRMED. Returns rows affected (0 if already confirmed/canceled — the idempotency guard, same pattern as ContributionRepository.markConfirmed). */
    fun markConfirmed(
        id: UUID,
        shippingAddress: ShippingAddress?,
        stripePaymentIntentId: String?,
    ): Int {
        val now = Instant.now()
        return jdbcClient
            .sql(
                """
                update "order" set status = 'CONFIRMED', confirmed_at = :now, shipping_address = cast(:shippingAddress as jsonb),
                  stripe_payment_intent_id = :paymentIntentId
                where id = :id and status = 'PENDING'
                """.trimIndent(),
            ).param("now", Timestamp.from(now))
            .param("shippingAddress", shippingAddress?.let { objectMapper.writeValueAsString(it) })
            .param("paymentIntentId", stripePaymentIntentId)
            .param("id", id)
            .update()
    }

    /** Only flips CONFIRMED -> REFUNDED. Returns rows affected (0 if not confirmed or already refunded — the idempotency guard). */
    fun markRefunded(id: UUID): Int {
        val now = Instant.now()
        return jdbcClient
            .sql("""update "order" set status = 'REFUNDED', refunded_at = :now where id = :id and status = 'CONFIRMED'""")
            .param("now", Timestamp.from(now))
            .param("id", id)
            .update()
    }

    private fun mapRow(
        rs: java.sql.ResultSet,
        rowNum: Int,
    ): Order =
        Order(
            id = rs.getObject("id", UUID::class.java),
            organizationId = rs.getObject("organization_id", UUID::class.java),
            storeId = rs.getObject("store_id", UUID::class.java),
            status = OrderStatus.valueOf(rs.getString("status")),
            currency = rs.getString("currency"),
            supporterName = rs.getString("supporter_name"),
            supporterEmail = rs.getString("supporter_email"),
            shippingAddress = rs.getString("shipping_address")?.let { objectMapper.readValue(it, ShippingAddress::class.java) },
            stripeCheckoutSessionId = rs.getString("stripe_checkout_session_id"),
            stripePaymentIntentId = rs.getString("stripe_payment_intent_id"),
            confirmedAt = rs.getTimestamp("confirmed_at")?.toInstant(),
            refundedAt = rs.getTimestamp("refunded_at")?.toInstant(),
            createdAt = rs.getTimestamp("created_at").toInstant(),
            paymentSource = PaymentSource.valueOf(rs.getString("payment_source")),
        )
}
