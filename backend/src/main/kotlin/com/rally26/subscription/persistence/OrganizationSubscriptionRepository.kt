package com.rally26.subscription.persistence

import com.rally26.subscription.domain.OrganizationSubscription
import com.rally26.subscription.domain.OrganizationSubscriptionStatus
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

private const val SUBSCRIPTION_COLUMNS =
    "id, organization_id, plan_code, status, stripe_customer_id, stripe_subscription_id, " +
        "stripe_checkout_session_id, checkout_generation, last_payment_failure_at, created_at, updated_at"

@Repository
class OrganizationSubscriptionRepository(
    private val jdbcClient: JdbcClient,
) {
    fun findByOrganizationId(organizationId: UUID): OrganizationSubscription? = queryOne("organization_id = :value", organizationId)

    fun findByOrganizationIdForUpdate(organizationId: UUID): OrganizationSubscription? =
        queryOne("organization_id = :value", organizationId, forUpdate = true)

    fun findByStripeCustomerId(customerId: String): OrganizationSubscription? = queryOne("stripe_customer_id = :value", customerId)

    fun findByStripeSubscriptionId(subscriptionId: String): OrganizationSubscription? =
        queryOne("stripe_subscription_id = :value", subscriptionId)

    fun findById(id: UUID): OrganizationSubscription? = queryOne("id = :value", id)

    fun insert(
        organizationId: UUID,
        planCode: String,
    ): OrganizationSubscription {
        val id = UUID.randomUUID()
        val now = Instant.now()
        jdbcClient
            .sql(
                """
                insert into organization_subscription
                    (id, organization_id, plan_code, status, created_at, updated_at)
                values
                    (:id, :organizationId, :planCode, 'CHECKOUT_PENDING', :now, :now)
                """.trimIndent(),
            ).param("id", id)
            .param("organizationId", organizationId)
            .param("planCode", planCode)
            .param("now", Timestamp.from(now))
            .update()
        return OrganizationSubscription(
            id = id,
            organizationId = organizationId,
            planCode = planCode,
            status = OrganizationSubscriptionStatus.CHECKOUT_PENDING,
            stripeCustomerId = null,
            stripeSubscriptionId = null,
            stripeCheckoutSessionId = null,
            checkoutGeneration = 0,
            lastPaymentFailureAt = null,
            createdAt = now,
            updatedAt = now,
        )
    }

    fun updatePlan(
        id: UUID,
        planCode: String,
    ) {
        jdbcClient
            .sql("update organization_subscription set plan_code = :planCode, updated_at = now() where id = :id")
            .param("planCode", planCode)
            .param("id", id)
            .update()
    }

    fun saveCustomerId(
        id: UUID,
        customerId: String,
    ) {
        jdbcClient
            .sql("update organization_subscription set stripe_customer_id = :customerId, updated_at = now() where id = :id")
            .param("customerId", customerId)
            .param("id", id)
            .update()
    }

    fun saveCheckoutSession(
        id: UUID,
        checkoutSessionId: String,
        generation: Int,
    ) {
        jdbcClient
            .sql(
                """
                update organization_subscription
                set status = 'CHECKOUT_PENDING', stripe_checkout_session_id = :checkoutSessionId,
                    checkout_generation = :generation, updated_at = now()
                where id = :id
                """.trimIndent(),
            ).param("checkoutSessionId", checkoutSessionId)
            .param("generation", generation)
            .param("id", id)
            .update()
    }

    fun syncExternalState(
        id: UUID,
        status: OrganizationSubscriptionStatus,
        customerId: String?,
        subscriptionId: String?,
    ) {
        jdbcClient
            .sql(
                """
                update organization_subscription
                set status = :status,
                    stripe_customer_id = coalesce(:customerId, stripe_customer_id),
                    stripe_subscription_id = coalesce(:subscriptionId, stripe_subscription_id),
                    updated_at = now()
                where id = :id
                """.trimIndent(),
            ).param("status", status.name)
            .param("customerId", customerId)
            .param("subscriptionId", subscriptionId)
            .param("id", id)
            .update()
    }

    fun markPaymentFailure(id: UUID) {
        jdbcClient
            .sql(
                """
                update organization_subscription
                set status = case
                        when status in ('CANCELED', 'INCOMPLETE') then status
                        else 'PAST_DUE'
                    end,
                    last_payment_failure_at = now(),
                    updated_at = now()
                where id = :id
                """.trimIndent(),
            ).param("id", id)
            .update()
    }

    private fun queryOne(
        predicate: String,
        value: Any,
        forUpdate: Boolean = false,
    ): OrganizationSubscription? {
        val suffix = if (forUpdate) " for update" else ""
        return jdbcClient
            .sql("select $SUBSCRIPTION_COLUMNS from organization_subscription where $predicate$suffix")
            .param("value", value)
            .query(::mapRow)
            .optional()
            .orElse(null)
    }

    private fun mapRow(
        rs: java.sql.ResultSet,
        rowNum: Int,
    ): OrganizationSubscription =
        OrganizationSubscription(
            id = rs.getObject("id", UUID::class.java),
            organizationId = rs.getObject("organization_id", UUID::class.java),
            planCode = rs.getString("plan_code"),
            status = OrganizationSubscriptionStatus.valueOf(rs.getString("status")),
            stripeCustomerId = rs.getString("stripe_customer_id"),
            stripeSubscriptionId = rs.getString("stripe_subscription_id"),
            stripeCheckoutSessionId = rs.getString("stripe_checkout_session_id"),
            checkoutGeneration = rs.getInt("checkout_generation"),
            lastPaymentFailureAt = rs.getTimestamp("last_payment_failure_at")?.toInstant(),
            createdAt = rs.getTimestamp("created_at").toInstant(),
            updatedAt = rs.getTimestamp("updated_at").toInstant(),
        )
}
