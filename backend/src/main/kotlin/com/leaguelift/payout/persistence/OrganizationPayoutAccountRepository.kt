package com.leaguelift.payout.persistence

import com.leaguelift.payout.domain.OrganizationPayoutAccount
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

private const val COLUMNS = """
    id, organization_id, stripe_account_id, details_submitted, charges_enabled, payouts_enabled, created_at, updated_at
"""

@Repository
class OrganizationPayoutAccountRepository(private val jdbcClient: JdbcClient) {

	fun findByOrganizationId(organizationId: UUID): OrganizationPayoutAccount? =
		jdbcClient.sql("select $COLUMNS from organization_payout_account where organization_id = :organizationId")
			.param("organizationId", organizationId)
			.query(::mapRow).optional().orElse(null)

	fun insert(organizationId: UUID, stripeAccountId: String): OrganizationPayoutAccount {
		val now = Instant.now()
		val id = UUID.randomUUID()
		jdbcClient.sql(
			"""
            insert into organization_payout_account
                (id, organization_id, stripe_account_id, details_submitted, charges_enabled, payouts_enabled, created_at, updated_at)
            values
                (:id, :organizationId, :stripeAccountId, false, false, false, :now, :now)
            """.trimIndent(),
		)
			.param("id", id).param("organizationId", organizationId).param("stripeAccountId", stripeAccountId)
			.param("now", Timestamp.from(now))
			.update()
		return OrganizationPayoutAccount(
			id, organizationId, stripeAccountId,
			detailsSubmitted = false, chargesEnabled = false, payoutsEnabled = false,
			createdAt = now, updatedAt = now,
		)
	}

	/** Platform-wide count of organizations with a fully payouts-enabled Stripe Connect account (Platform Admin dashboard, Phase 7 completion). */
	fun countPayoutsEnabled(): Long =
		jdbcClient.sql("select count(*) from organization_payout_account where payouts_enabled = true")
			.query(Long::class.java)
			.single()

	fun updateStatus(organizationId: UUID, detailsSubmitted: Boolean, chargesEnabled: Boolean, payoutsEnabled: Boolean): Int {
		val now = Instant.now()
		return jdbcClient.sql(
			"""
            update organization_payout_account
            set details_submitted = :detailsSubmitted, charges_enabled = :chargesEnabled,
                payouts_enabled = :payoutsEnabled, updated_at = :now
            where organization_id = :organizationId
            """.trimIndent(),
		)
			.param("detailsSubmitted", detailsSubmitted).param("chargesEnabled", chargesEnabled)
			.param("payoutsEnabled", payoutsEnabled).param("now", Timestamp.from(now))
			.param("organizationId", organizationId)
			.update()
	}

	private fun mapRow(rs: ResultSet, rowNum: Int): OrganizationPayoutAccount =
		OrganizationPayoutAccount(
			id = rs.getObject("id", UUID::class.java),
			organizationId = rs.getObject("organization_id", UUID::class.java),
			stripeAccountId = rs.getString("stripe_account_id"),
			detailsSubmitted = rs.getBoolean("details_submitted"),
			chargesEnabled = rs.getBoolean("charges_enabled"),
			payoutsEnabled = rs.getBoolean("payouts_enabled"),
			createdAt = rs.getTimestamp("created_at").toInstant(),
			updatedAt = rs.getTimestamp("updated_at").toInstant(),
		)
}
