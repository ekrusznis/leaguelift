package com.rally26.fundraising.persistence

import com.rally26.finance.domain.PaymentSource
import com.rally26.fundraising.domain.Contribution
import com.rally26.fundraising.domain.ContributionStatus
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

private const val COLUMNS =
    "id, organization_id, campaign_id, amount_minor, currency, supporter_name, is_anonymous, supporter_email, status, " +
        "stripe_checkout_session_id, stripe_payment_intent_id, confirmed_at, refunded_at, created_at, payment_source, attributed_household_id"

@Repository
class ContributionRepository(
    private val jdbcClient: JdbcClient,
) {
    fun findById(id: UUID): Contribution? =
        jdbcClient
            .sql("select $COLUMNS from contribution where id = :id")
            .param("id", id)
            .query(::mapRow)
            .optional()
            .orElse(null)

    fun findByStripeCheckoutSessionId(sessionId: String): Contribution? =
        jdbcClient
            .sql("select $COLUMNS from contribution where stripe_checkout_session_id = :sessionId")
            .param("sessionId", sessionId)
            .query(::mapRow)
            .optional()
            .orElse(null)

    /** Dispute routing (2026-08-12) — a Stripe dispute references a payment_intent, not a checkout session. */
    fun findByStripePaymentIntentId(paymentIntentId: String): Contribution? =
        jdbcClient
            .sql("select $COLUMNS from contribution where stripe_payment_intent_id = :paymentIntentId")
            .param("paymentIntentId", paymentIntentId)
            .query(::mapRow)
            .optional()
            .orElse(null)

    /** "Confirmed" here means "was ever confirmed" — a later refund still shows in this admin-facing history, just with status REFUNDED. */
    fun listConfirmedForCampaign(
        campaignId: UUID,
        offset: Int,
        limit: Int,
    ): List<Contribution> =
        jdbcClient
            .sql(
                """
                select $COLUMNS from contribution
                where campaign_id = :campaignId and status in ('CONFIRMED', 'REFUNDED')
                order by confirmed_at desc
                offset :offset limit :limit
                """.trimIndent(),
            ).param("campaignId", campaignId)
            .param("offset", offset)
            .param("limit", limit)
            .query(::mapRow)
            .list()

    fun countConfirmedForCampaign(campaignId: UUID): Long =
        jdbcClient
            .sql("select count(*) from contribution where campaign_id = :campaignId and status in ('CONFIRMED', 'REFUNDED')")
            .param("campaignId", campaignId)
            .query(Long::class.java)
            .single()

    fun sumConfirmedByCampaign(campaignId: UUID): Long =
        jdbcClient
            .sql("select coalesce(sum(amount_minor), 0) from contribution where campaign_id = :campaignId and status = 'CONFIRMED'")
            .param("campaignId", campaignId)
            .query(Long::class.java)
            .single()

    fun sumConfirmedByOrganization(organizationId: UUID): Long =
        jdbcClient
            .sql("select coalesce(sum(amount_minor), 0) from contribution where organization_id = :organizationId and status = 'CONFIRMED'")
            .param("organizationId", organizationId)
            .query(Long::class.java)
            .single()

    fun sumConfirmedByTeam(teamId: UUID): Long =
        jdbcClient
            .sql(
                """
                select coalesce(sum(c.amount_minor), 0)
                from contribution c
                join campaign camp on camp.id = c.campaign_id
                where camp.team_id = :teamId and c.status = 'CONFIRMED'
                """.trimIndent(),
            ).param("teamId", teamId)
            .query(Long::class.java)
            .single()

    /** Inserted before Stripe returns a checkout session id — see [attachStripeSession]. */
    fun insertPending(
        organizationId: UUID,
        campaignId: UUID,
        amountMinor: Long,
        currency: String,
        supporterName: String?,
        isAnonymous: Boolean,
        supporterEmail: String?,
        attributedHouseholdId: UUID? = null,
    ): Contribution {
        val id = UUID.randomUUID()
        val now = Instant.now()
        jdbcClient
            .sql(
                """
                insert into contribution
                	(id, organization_id, campaign_id, amount_minor, currency, supporter_name, is_anonymous, supporter_email, status, created_at, attributed_household_id)
                values
                	(:id, :organizationId, :campaignId, :amountMinor, :currency, :supporterName, :isAnonymous, :supporterEmail, 'PENDING', :now, :attributedHouseholdId)
                """.trimIndent(),
            ).param("id", id)
            .param("organizationId", organizationId)
            .param("campaignId", campaignId)
            .param("amountMinor", amountMinor)
            .param("currency", currency)
            .param("supporterName", supporterName)
            .param("isAnonymous", isAnonymous)
            .param("supporterEmail", supporterEmail)
            .param("now", Timestamp.from(now))
            .param("attributedHouseholdId", attributedHouseholdId)
            .update()
        return Contribution(
            id,
            organizationId,
            campaignId,
            amountMinor,
            currency,
            supporterName,
            isAnonymous,
            supporterEmail,
            ContributionStatus.PENDING,
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
            .sql("update contribution set stripe_checkout_session_id = :sessionId where id = :id")
            .param("sessionId", stripeCheckoutSessionId)
            .param("id", id)
            .update()

    fun insertOfflinePending(
        organizationId: UUID,
        campaignId: UUID,
        amountMinor: Long,
        currency: String,
        supporterName: String?,
        isAnonymous: Boolean,
        supporterEmail: String?,
    ): Contribution {
        val id = UUID.randomUUID()
        val now = Instant.now()
        jdbcClient
            .sql(
                """
                insert into contribution
                	(id, organization_id, campaign_id, amount_minor, currency, supporter_name, is_anonymous,
                	 supporter_email, status, payment_source, created_at)
                values
                	(:id, :organizationId, :campaignId, :amountMinor, :currency, :supporterName, :isAnonymous,
                	 :supporterEmail, 'PENDING', 'OFFLINE', :now)
                """.trimIndent(),
            ).param("id", id)
            .param("organizationId", organizationId)
            .param("campaignId", campaignId)
            .param("amountMinor", amountMinor)
            .param("currency", currency)
            .param("supporterName", supporterName)
            .param("isAnonymous", isAnonymous)
            .param("supporterEmail", supporterEmail)
            .param("now", Timestamp.from(now))
            .update()
        return findById(id)!!
    }

    fun markOfflineConfirmed(
        id: UUID,
        confirmedAt: Instant,
    ): Int =
        jdbcClient
            .sql(
                """
                update contribution set status = 'CONFIRMED', confirmed_at = :confirmedAt
                where id = :id and status = 'PENDING' and payment_source = 'OFFLINE'
                """.trimIndent(),
            ).param("confirmedAt", Timestamp.from(confirmedAt))
            .param("id", id)
            .update()

    /** Only flips PENDING -> CONFIRMED. Returns rows affected (0 if already confirmed/canceled — the idempotency guard). */
    fun markConfirmed(
        id: UUID,
        stripePaymentIntentId: String?,
    ): Int {
        val now = Instant.now()
        return jdbcClient
            .sql(
                """
                update contribution set status = 'CONFIRMED', confirmed_at = :now, stripe_payment_intent_id = :paymentIntentId
                where id = :id and status = 'PENDING'
                """.trimIndent(),
            ).param("now", Timestamp.from(now))
            .param("paymentIntentId", stripePaymentIntentId)
            .param("id", id)
            .update()
    }

    /** Only flips CONFIRMED -> REFUNDED. Returns rows affected (0 if not confirmed or already refunded — the idempotency guard). */
    fun markRefunded(id: UUID): Int {
        val now = Instant.now()
        return jdbcClient
            .sql("update contribution set status = 'REFUNDED', refunded_at = :now where id = :id and status = 'CONFIRMED'")
            .param("now", Timestamp.from(now))
            .param("id", id)
            .update()
    }

    private fun mapRow(
        rs: java.sql.ResultSet,
        rowNum: Int,
    ): Contribution =
        Contribution(
            id = rs.getObject("id", UUID::class.java),
            organizationId = rs.getObject("organization_id", UUID::class.java),
            campaignId = rs.getObject("campaign_id", UUID::class.java),
            amountMinor = rs.getLong("amount_minor"),
            currency = rs.getString("currency"),
            supporterName = rs.getString("supporter_name"),
            isAnonymous = rs.getBoolean("is_anonymous"),
            supporterEmail = rs.getString("supporter_email"),
            status = ContributionStatus.valueOf(rs.getString("status")),
            stripeCheckoutSessionId = rs.getString("stripe_checkout_session_id"), // nullable; JDBC getString already returns null cleanly
            stripePaymentIntentId = rs.getString("stripe_payment_intent_id"),
            confirmedAt = rs.getTimestamp("confirmed_at")?.toInstant(),
            refundedAt = rs.getTimestamp("refunded_at")?.toInstant(),
            createdAt = rs.getTimestamp("created_at").toInstant(),
            paymentSource = PaymentSource.valueOf(rs.getString("payment_source")),
            attributedHouseholdId = rs.getObject("attributed_household_id", UUID::class.java),
        )
}
