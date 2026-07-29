package com.leaguelift.sponsorship.persistence

import com.leaguelift.sponsorship.domain.Sponsorship
import com.leaguelift.sponsorship.domain.SponsorshipStatus
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

private const val COLUMNS = """
    id, organization_id, package_id, sponsor_id, amount_minor, currency, status,
    stripe_checkout_session_id, stripe_payment_intent_id, confirmed_at, created_at
"""

@Repository
class SponsorshipRepository(private val jdbcClient: JdbcClient) {

	fun findById(id: UUID): Sponsorship? =
		jdbcClient.sql("select $COLUMNS from sponsorship where id = :id")
			.param("id", id)
			.query(::mapRow)
			.optional()
			.orElse(null)

	fun findByStripeCheckoutSessionId(sessionId: String): Sponsorship? =
		jdbcClient.sql("select $COLUMNS from sponsorship where stripe_checkout_session_id = :sessionId")
			.param("sessionId", sessionId)
			.query(::mapRow)
			.optional()
			.orElse(null)

	/** "Confirmed" here means "was ever confirmed" — mirrors `ContributionRepository.listConfirmedForCampaign`'s comment; a later refund still shows in this admin-facing history. */
	fun listConfirmedForPackage(packageId: UUID, offset: Int, limit: Int): List<Sponsorship> =
		jdbcClient.sql(
			"""
			select $COLUMNS from sponsorship
			where package_id = :packageId and status in ('CONFIRMED', 'REFUNDED')
			order by confirmed_at desc
			offset :offset limit :limit
			""".trimIndent(),
		)
			.param("packageId", packageId)
			.param("offset", offset)
			.param("limit", limit)
			.query(::mapRow)
			.list()

	fun countConfirmedForPackage(packageId: UUID): Long =
		jdbcClient.sql("select count(*) from sponsorship where package_id = :packageId and status in ('CONFIRMED', 'REFUNDED')")
			.param("packageId", packageId)
			.query(Long::class.java)
			.single()

	/** The public sponsor directory's data source — every currently-confirmed sponsorship across the organization, newest first. */
	fun findConfirmedForOrganization(organizationId: UUID): List<Sponsorship> =
		jdbcClient.sql(
			"""
			select $COLUMNS from sponsorship
			where organization_id = :organizationId and status = 'CONFIRMED'
			order by confirmed_at desc
			""".trimIndent(),
		)
			.param("organizationId", organizationId)
			.query(::mapRow)
			.list()

	/** Inserted before Stripe returns a checkout session id — see `attachStripeSession` (mirrors `ContributionRepository.insertPending`). */
	fun insertPending(organizationId: UUID, packageId: UUID, sponsorId: UUID, amountMinor: Long, currency: String): Sponsorship {
		val id = UUID.randomUUID()
		val now = Instant.now()
		jdbcClient.sql(
			"""
			insert into sponsorship (id, organization_id, package_id, sponsor_id, amount_minor, currency, status, created_at)
			values (:id, :organizationId, :packageId, :sponsorId, :amountMinor, :currency, 'PENDING', :now)
			""".trimIndent(),
		)
			.param("id", id)
			.param("organizationId", organizationId)
			.param("packageId", packageId)
			.param("sponsorId", sponsorId)
			.param("amountMinor", amountMinor)
			.param("currency", currency)
			.param("now", Timestamp.from(now))
			.update()
		return Sponsorship(id, organizationId, packageId, sponsorId, amountMinor, currency, SponsorshipStatus.PENDING, null, null, null, now)
	}

	fun attachStripeSession(id: UUID, stripeCheckoutSessionId: String): Int =
		jdbcClient.sql("update sponsorship set stripe_checkout_session_id = :sessionId where id = :id")
			.param("sessionId", stripeCheckoutSessionId)
			.param("id", id)
			.update()

	/** Only flips PENDING -> CONFIRMED. Returns rows affected (0 if already confirmed — the webhook idempotency guard). */
	fun markConfirmed(id: UUID, stripePaymentIntentId: String?): Int {
		val now = Instant.now()
		return jdbcClient.sql(
			"""
			update sponsorship set status = 'CONFIRMED', confirmed_at = :now, stripe_payment_intent_id = :paymentIntentId
			where id = :id and status = 'PENDING'
			""".trimIndent(),
		)
			.param("now", Timestamp.from(now))
			.param("paymentIntentId", stripePaymentIntentId)
			.param("id", id)
			.update()
	}

	private fun mapRow(rs: java.sql.ResultSet, rowNum: Int): Sponsorship =
		Sponsorship(
			id = rs.getObject("id", UUID::class.java),
			organizationId = rs.getObject("organization_id", UUID::class.java),
			packageId = rs.getObject("package_id", UUID::class.java),
			sponsorId = rs.getObject("sponsor_id", UUID::class.java),
			amountMinor = rs.getLong("amount_minor"),
			currency = rs.getString("currency"),
			status = SponsorshipStatus.valueOf(rs.getString("status")),
			stripeCheckoutSessionId = rs.getString("stripe_checkout_session_id"),
			stripePaymentIntentId = rs.getString("stripe_payment_intent_id"),
			confirmedAt = rs.getTimestamp("confirmed_at")?.toInstant(),
			createdAt = rs.getTimestamp("created_at").toInstant(),
		)
}
