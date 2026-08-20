package com.rally26.order.persistence

import com.fasterxml.jackson.databind.ObjectMapper
import com.rally26.finance.domain.PaymentSource
import com.rally26.order.domain.FulfillmentStatus
import com.rally26.order.domain.Order
import com.rally26.order.domain.OrderSearchCriteria
import com.rally26.order.domain.OrderSearchRow
import com.rally26.order.domain.OrderSearchSort
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
    refunded_at, created_at, payment_source, attributed_household_id
"""

private const val SEARCH_COLUMNS = """
    o.id, o.organization_id, o.store_id, o.status, o.currency, o.supporter_name, o.supporter_email,
    o.shipping_address, o.stripe_checkout_session_id, o.stripe_payment_intent_id, o.confirmed_at,
    o.refunded_at, o.created_at, o.payment_source, o.attributed_household_id
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

    /** Dispute routing (2026-08-12) — a Stripe dispute references a payment_intent, not a checkout session. */
    fun findByStripePaymentIntentId(paymentIntentId: String): Order? =
        jdbcClient
            .sql("""select $COLUMNS from "order" where stripe_payment_intent_id = :paymentIntentId""")
            .param("paymentIntentId", paymentIntentId)
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

    /** Guardian dashboard (Phase 24 slice 24.3) — only orders placed through a published athlete storefront ever carry this attribution; a regular authenticated Swag Shop order never does. */
    fun findByAttributedHousehold(
        organizationId: UUID,
        householdId: UUID,
        offset: Int,
        limit: Int,
    ): List<Order> =
        jdbcClient
            .sql(
                """
                select $COLUMNS from "order"
                where organization_id = :organizationId and attributed_household_id = :householdId
                order by created_at desc
                offset :offset limit :limit
                """.trimIndent(),
            ).param("organizationId", organizationId)
            .param("householdId", householdId)
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

    /**
     * `/stores/{storeId}/orders/search` — the frontend's order list has always called
     * this endpoint (`frontend/src/features/store/searchApi.ts`), but no backend
     * mapping for it ever existed (LR-025, same class as LR-016/018/020). Same base
     * CONFIRMED/REFUNDED restriction as [findByStore]; `fulfillmentStatus` needs a
     * LEFT JOIN since `fulfillment` is a separate 1:1 table an order may not have a
     * row in yet.
     */
    fun search(
        storeId: UUID,
        criteria: OrderSearchCriteria,
        offset: Int,
        limit: Int,
    ): List<OrderSearchRow> {
        val built = buildSearchSql(storeId, criteria, countOnly = false)
        var statement = jdbcClient.sql("${built.first} offset :offset limit :limit").param("offset", offset).param("limit", limit)
        built.second.forEach { (name, value) -> statement = statement.param(name, value) }
        return statement.query(::mapSearchRow).list()
    }

    fun countSearch(
        storeId: UUID,
        criteria: OrderSearchCriteria,
    ): Long {
        val built = buildSearchSql(storeId, criteria, countOnly = true)
        var statement = jdbcClient.sql(built.first)
        built.second.forEach { (name, value) -> statement = statement.param(name, value) }
        return statement.query(Long::class.java).single()
    }

    private fun buildSearchSql(
        storeId: UUID,
        criteria: OrderSearchCriteria,
        countOnly: Boolean,
    ): Pair<String, Map<String, Any>> {
        val sql =
            StringBuilder(
                if (countOnly) {
                    """select count(*) from "order" o left join fulfillment f on f.order_id = o.id"""
                } else {
                    """select $SEARCH_COLUMNS, f.status as fulfillment_status from "order" o left join fulfillment f on f.order_id = o.id"""
                },
            )
        sql.append(" where o.store_id = :storeId and o.status in ('CONFIRMED', 'REFUNDED')")
        val params = linkedMapOf<String, Any>("storeId" to storeId)

        criteria.status?.let {
            sql.append(" and o.status = :status")
            params["status"] = it.name
        }
        criteria.paymentSource?.let {
            sql.append(" and o.payment_source = :paymentSource")
            params["paymentSource"] = it.name
        }
        criteria.fulfillmentStatus?.let {
            sql.append(" and f.status = :fulfillmentStatus")
            params["fulfillmentStatus"] = it.name
        }
        criteria.keyword?.trim()?.takeIf { it.isNotEmpty() }?.let { keyword ->
            sql.append(
                " and (lower(coalesce(o.supporter_name, '')) like :keyword or lower(coalesce(o.supporter_email, '')) like :keyword)",
            )
            params["keyword"] = "%${keyword.lowercase()}%"
        }

        if (!countOnly) {
            sql.append(
                when (criteria.sort) {
                    OrderSearchSort.NEWEST -> " order by o.confirmed_at desc"
                    OrderSearchSort.OLDEST -> " order by o.confirmed_at asc"
                    OrderSearchSort.SUPPORTER_ASC -> " order by lower(coalesce(o.supporter_name, '')) asc, o.confirmed_at desc"
                    OrderSearchSort.STATUS_ASC -> " order by o.status asc, o.confirmed_at desc"
                    OrderSearchSort.FULFILLMENT_ASC -> " order by f.status asc nulls first, o.confirmed_at desc"
                },
            )
        }
        return sql.toString() to params
    }

    private fun mapSearchRow(
        rs: java.sql.ResultSet,
        rowNum: Int,
    ): OrderSearchRow =
        OrderSearchRow(
            order = mapRow(rs, rowNum),
            fulfillmentStatus = rs.getString("fulfillment_status")?.let(FulfillmentStatus::valueOf),
        )

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
        attributedHouseholdId: UUID? = null,
    ): Order {
        val id = UUID.randomUUID()
        val now = Instant.now()
        jdbcClient
            .sql(
                """
                insert into "order"
                	(id, organization_id, store_id, status, currency, supporter_name, supporter_email, created_at, attributed_household_id)
                values
                	(:id, :organizationId, :storeId, 'PENDING', :currency, :supporterName, :supporterEmail, :now, :attributedHouseholdId)
                """.trimIndent(),
            ).param("id", id)
            .param("organizationId", organizationId)
            .param("storeId", storeId)
            .param("currency", currency)
            .param("supporterName", supporterName)
            .param("supporterEmail", supporterEmail)
            .param("now", Timestamp.from(now))
            .param("attributedHouseholdId", attributedHouseholdId)
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
            attributedHouseholdId = attributedHouseholdId,
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
            attributedHouseholdId = rs.getObject("attributed_household_id", UUID::class.java),
        )
}
