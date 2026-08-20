package com.rally26.subscription.persistence

import com.rally26.subscription.domain.OrganizationSubscription
import com.rally26.subscription.domain.OrganizationSubscriptionStatus
import com.rally26.subscription.domain.PlatformOrganizationSubscription
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

private const val SUBSCRIPTION_COLUMNS =
    "id, organization_id, plan_code, status, stripe_customer_id, stripe_subscription_id, " +
        "stripe_checkout_session_id, checkout_generation, last_payment_failure_at, last_payment_success_at, " +
        "cancel_at_period_end, plan_change_generation, downgrade_to_plan_code, current_period_end, created_at, updated_at"

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
            lastPaymentSuccessAt = null,
            cancelAtPeriodEnd = false,
        )
    }

    /** FREE-tier activation only (`OrganizationSubscriptionService.activateFreeSubscription`) — no Stripe columns are ever populated for this row. */
    fun insertActive(
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
                    (:id, :organizationId, :planCode, 'ACTIVE', :now, :now)
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
            status = OrganizationSubscriptionStatus.ACTIVE,
            stripeCustomerId = null,
            stripeSubscriptionId = null,
            stripeCheckoutSessionId = null,
            checkoutGeneration = 0,
            lastPaymentFailureAt = null,
            createdAt = now,
            updatedAt = now,
            lastPaymentSuccessAt = null,
            cancelAtPeriodEnd = false,
        )
    }

    /** Also clears any pending [OrganizationSubscription.downgradeToPlanCode] — a real plan change (upgrade completing, or a synchronous paid&lt;-&gt;paid switch) always supersedes a stale scheduled downgrade. */
    fun updatePlan(
        id: UUID,
        planCode: String,
    ) {
        jdbcClient
            .sql(
                """
                update organization_subscription
                set plan_code = :planCode, downgrade_to_plan_code = null, updated_at = now()
                where id = :id
                """.trimIndent(),
            ).param("planCode", planCode)
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
        cancelAtPeriodEnd: Boolean? = null,
    ) {
        jdbcClient
            .sql(
                """
                update organization_subscription
                set status = :status,
                    stripe_customer_id = coalesce(:customerId, stripe_customer_id),
                    stripe_subscription_id = coalesce(:subscriptionId, stripe_subscription_id),
                    cancel_at_period_end = coalesce(:cancelAtPeriodEnd, cancel_at_period_end),
                    updated_at = now()
                where id = :id
                """.trimIndent(),
            ).param("status", status.name)
            .param("customerId", customerId)
            .param("subscriptionId", subscriptionId)
            .param("cancelAtPeriodEnd", cancelAtPeriodEnd)
            .param("id", id)
            .update()
    }

    /** Bumps the Stripe idempotency-key suffix for the new subscription update/cancel calls, mirroring [saveCheckoutSession]'s checkout_generation increment. Returns the new generation for the caller to pass straight to the Stripe client. */
    fun nextPlanChangeGeneration(id: UUID): Int =
        jdbcClient
            .sql(
                """
                update organization_subscription
                set plan_change_generation = plan_change_generation + 1, updated_at = now()
                where id = :id
                returning plan_change_generation
                """.trimIndent(),
            ).param("id", id)
            .query(Int::class.java)
            .single()

    /** A downgrade to `targetPlanCode` is scheduled to complete when Stripe's current billing period ends — plan_code/status are deliberately untouched until then; see `OrganizationSubscriptionService.handleSubscriptionChanged`'s downgrade-completion branch. */
    fun markPendingDowngrade(
        id: UUID,
        targetPlanCode: String,
        effectiveAt: Instant,
    ) {
        jdbcClient
            .sql(
                """
                update organization_subscription
                set downgrade_to_plan_code = :targetPlanCode,
                    cancel_at_period_end = true,
                    current_period_end = :effectiveAt,
                    updated_at = now()
                where id = :id
                """.trimIndent(),
            ).param("targetPlanCode", targetPlanCode)
            .param("effectiveAt", Timestamp.from(effectiveAt))
            .param("id", id)
            .update()
    }

    /** Completes a scheduled downgrade to FREE once Stripe confirms the subscription actually canceled at period end. Deliberately keeps stripe_customer_id so a future FREE-&gt;paid re-upgrade reuses the same Stripe customer. */
    fun downgradeToFree(id: UUID) {
        jdbcClient
            .sql(
                """
                update organization_subscription
                set plan_code = 'FREE',
                    status = 'ACTIVE',
                    stripe_subscription_id = null,
                    downgrade_to_plan_code = null,
                    cancel_at_period_end = false,
                    current_period_end = null,
                    updated_at = now()
                where id = :id
                """.trimIndent(),
            ).param("id", id)
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

    fun markPaymentSuccess(id: UUID) {
        jdbcClient
            .sql(
                """
                update organization_subscription
                set last_payment_success_at = now(), updated_at = now()
                where id = :id
                """.trimIndent(),
            ).param("id", id)
            .update()
    }

    fun listForPlatformAdmin(
        query: String,
        status: String,
        offset: Int,
        limit: Int,
    ): List<PlatformOrganizationSubscription> =
        jdbcClient
            .sql(
                """
                select o.id as organization_id,
                       o.name as organization_name,
                       o.status as organization_status,
                       s.plan_code,
                       p.name as plan_name,
                       p.amount_minor,
                       p.currency,
                       s.status as subscription_status,
                       s.last_payment_failure_at,
                       s.last_payment_success_at,
                       (s.stripe_customer_id is not null) as has_stripe_customer,
                       (s.stripe_subscription_id is not null) as has_stripe_subscription,
                       coalesce(s.cancel_at_period_end, false) as cancel_at_period_end,
                       coalesce(s.updated_at, o.updated_at) as billing_updated_at
                from organization o
                left join organization_subscription s on s.organization_id = o.id
                left join subscription_plan p on p.code = s.plan_code
                where (:query = '' or lower(o.name) like lower('%' || :query || '%') or lower(o.slug) like lower('%' || :query || '%'))
                  and (:status = '' or coalesce(s.status, 'NONE') = :status)
                order by coalesce(s.updated_at, o.updated_at) desc, o.name
                offset :offset limit :limit
                """.trimIndent(),
            ).param("query", query)
            .param("status", status)
            .param("offset", offset)
            .param("limit", limit)
            .query(::mapPlatformRow)
            .list()

    fun countForPlatformAdmin(
        query: String,
        status: String,
    ): Long =
        jdbcClient
            .sql(
                """
                select count(*)
                from organization o
                left join organization_subscription s on s.organization_id = o.id
                where (:query = '' or lower(o.name) like lower('%' || :query || '%') or lower(o.slug) like lower('%' || :query || '%'))
                  and (:status = '' or coalesce(s.status, 'NONE') = :status)
                """.trimIndent(),
            ).param("query", query)
            .param("status", status)
            .query(Long::class.java)
            .single()

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
            lastPaymentSuccessAt = rs.getTimestamp("last_payment_success_at")?.toInstant(),
            cancelAtPeriodEnd = rs.getBoolean("cancel_at_period_end"),
            planChangeGeneration = rs.getInt("plan_change_generation"),
            downgradeToPlanCode = rs.getString("downgrade_to_plan_code"),
            currentPeriodEnd = rs.getTimestamp("current_period_end")?.toInstant(),
        )

    private fun mapPlatformRow(
        rs: java.sql.ResultSet,
        rowNum: Int,
    ): PlatformOrganizationSubscription =
        PlatformOrganizationSubscription(
            organizationId = rs.getObject("organization_id", UUID::class.java),
            organizationName = rs.getString("organization_name"),
            organizationStatus = rs.getString("organization_status"),
            planCode = rs.getString("plan_code"),
            planName = rs.getString("plan_name"),
            amountMinor = rs.getObject("amount_minor", java.lang.Long::class.java)?.toLong(),
            currency = rs.getString("currency"),
            status = rs.getString("subscription_status")?.let(OrganizationSubscriptionStatus::valueOf),
            lastPaymentFailureAt = rs.getTimestamp("last_payment_failure_at")?.toInstant(),
            lastPaymentSuccessAt = rs.getTimestamp("last_payment_success_at")?.toInstant(),
            hasStripeCustomer = rs.getBoolean("has_stripe_customer"),
            hasStripeSubscription = rs.getBoolean("has_stripe_subscription"),
            cancelAtPeriodEnd = rs.getBoolean("cancel_at_period_end"),
            updatedAt = rs.getTimestamp("billing_updated_at").toInstant(),
        )
}
