package com.rally26.subscription.persistence

import com.rally26.subscription.domain.SubscriptionPlan
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository

@Repository
class SubscriptionPlanRepository(
    private val jdbcClient: JdbcClient,
) {
    fun listActive(): List<SubscriptionPlan> =
        jdbcClient
            .sql(
                """
                select code, name, description, amount_minor, currency, billing_interval, contact_only, requires_checkout, active,
                       stripe_product_id, stripe_price_id
                from subscription_plan
                where active = true
                order by contact_only, amount_minor nulls last, code
                """.trimIndent(),
            ).query(::mapRow)
            .list()

    fun findByCode(code: String): SubscriptionPlan? = queryByCode(code, forUpdate = false)

    fun findByCodeForUpdate(code: String): SubscriptionPlan? = queryByCode(code, forUpdate = true)

    fun saveStripeIds(
        code: String,
        productId: String,
        priceId: String,
    ) {
        jdbcClient
            .sql(
                """
                update subscription_plan
                set stripe_product_id = :productId, stripe_price_id = :priceId, updated_at = now()
                where code = :code
                """.trimIndent(),
            ).param("productId", productId)
            .param("priceId", priceId)
            .param("code", code)
            .update()
    }

    private fun queryByCode(
        code: String,
        forUpdate: Boolean,
    ): SubscriptionPlan? {
        val suffix = if (forUpdate) " for update" else ""
        return jdbcClient
            .sql(
                """
                select code, name, description, amount_minor, currency, billing_interval, contact_only, requires_checkout, active,
                       stripe_product_id, stripe_price_id
                from subscription_plan
                where code = :code$suffix
                """.trimIndent(),
            ).param("code", code)
            .query(::mapRow)
            .optional()
            .orElse(null)
    }

    private fun mapRow(
        rs: java.sql.ResultSet,
        rowNum: Int,
    ): SubscriptionPlan =
        SubscriptionPlan(
            code = rs.getString("code"),
            name = rs.getString("name"),
            description = rs.getString("description"),
            amountMinor = rs.getObject("amount_minor", java.lang.Long::class.java)?.toLong(),
            currency = rs.getString("currency"),
            billingInterval = rs.getString("billing_interval"),
            contactOnly = rs.getBoolean("contact_only"),
            requiresCheckout = rs.getBoolean("requires_checkout"),
            active = rs.getBoolean("active"),
            stripeProductId = rs.getString("stripe_product_id"),
            stripePriceId = rs.getString("stripe_price_id"),
        )
}
