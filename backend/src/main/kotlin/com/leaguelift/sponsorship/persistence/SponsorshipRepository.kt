package com.leaguelift.sponsorship.persistence

import com.leaguelift.finance.domain.PaymentSource
import com.leaguelift.sponsorship.domain.Sponsorship
import com.leaguelift.sponsorship.domain.SponsorshipReviewStatus
import com.leaguelift.sponsorship.domain.SponsorshipStatus
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

private const val COLUMNS = """
    id, organization_id, package_id, sponsor_id, amount_minor, currency, status,
    stripe_checkout_session_id, stripe_payment_intent_id, confirmed_at, refunded_at,
    review_status, reviewed_at, reviewed_by, renewal_reminder_sent_at, created_at, payment_source
"""

/** A renewal-reminder candidate — the join of a confirmed+approved sponsorship, its package's placement end date, and its sponsor's contact email (`SponsorshipRenewalReminderService`'s data source). */
data class SponsorshipRenewalCandidate(
	val sponsorshipId: UUID,
	val organizationId: UUID,
	val packageName: String,
	val placementEndDate: java.time.LocalDate,
	val sponsorName: String,
	val sponsorContactEmail: String?,
)

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

	/** The public sponsor directory's data source — confirmed AND org-admin-approved sponsorships only (Phase 6 remainder approval workflow, ADR-019): a paid-but-not-yet-reviewed sponsorship must not appear here. */
	fun findConfirmedForOrganization(organizationId: UUID): List<Sponsorship> =
		jdbcClient.sql(
			"""
			select $COLUMNS from sponsorship
			where organization_id = :organizationId and status = 'CONFIRMED' and review_status = 'APPROVED'
			order by confirmed_at desc
			""".trimIndent(),
		)
			.param("organizationId", organizationId)
			.query(::mapRow)
			.list()

	/** The org-admin approval queue — confirmed sponsorships still awaiting a review decision. */
	fun listPendingReview(organizationId: UUID, offset: Int, limit: Int): List<Sponsorship> =
		jdbcClient.sql(
			"""
			select $COLUMNS from sponsorship
			where organization_id = :organizationId and status = 'CONFIRMED' and review_status = 'PENDING_REVIEW'
			order by confirmed_at asc
			offset :offset limit :limit
			""".trimIndent(),
		)
			.param("organizationId", organizationId)
			.param("offset", offset)
			.param("limit", limit)
			.query(::mapRow)
			.list()

	fun countPendingReview(organizationId: UUID): Long =
		jdbcClient.sql("select count(*) from sponsorship where organization_id = :organizationId and status = 'CONFIRMED' and review_status = 'PENDING_REVIEW'")
			.param("organizationId", organizationId)
			.query(Long::class.java)
			.single()

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
		return Sponsorship(
			id, organizationId, packageId, sponsorId, amountMinor, currency, SponsorshipStatus.PENDING, null, null, null, null,
			SponsorshipReviewStatus.PENDING_REVIEW, null, null, null, now,
		)
	}

	fun attachStripeSession(id: UUID, stripeCheckoutSessionId: String): Int =
		jdbcClient.sql("update sponsorship set stripe_checkout_session_id = :sessionId where id = :id")
			.param("sessionId", stripeCheckoutSessionId)
			.param("id", id)
			.update()

	fun insertOfflinePending(organizationId: UUID, packageId: UUID, sponsorId: UUID, amountMinor: Long, currency: String): Sponsorship {
		val id = UUID.randomUUID()
		val now = Instant.now()
		jdbcClient.sql(
			"""
			insert into sponsorship
				(id, organization_id, package_id, sponsor_id, amount_minor, currency, status, payment_source, created_at)
			values
				(:id, :organizationId, :packageId, :sponsorId, :amountMinor, :currency, 'PENDING', 'OFFLINE', :now)
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
		return findById(id)!!
	}

	fun markOfflineConfirmed(id: UUID, confirmedAt: Instant): Int = jdbcClient.sql(
		"""
		update sponsorship set status = 'CONFIRMED', confirmed_at = :confirmedAt
		where id = :id and status = 'PENDING' and payment_source = 'OFFLINE'
		""".trimIndent(),
	)
		.param("confirmedAt", Timestamp.from(confirmedAt))
		.param("id", id)
		.update()

	/** Only flips PENDING -> CONFIRMED. Returns rows affected (0 if already confirmed — the webhook idempotency guard). `review_status` is left at its PENDING_REVIEW default — confirmation alone must never grant public visibility (Phase 6 remainder approval workflow, ADR-019). */
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

	/** Only flips CONFIRMED -> REFUNDED — mirrors `ContributionRepository.markRefunded`/`OrderRepository.markRefunded` exactly. */
	fun markRefunded(id: UUID): Int {
		val now = Instant.now()
		return jdbcClient.sql("update sponsorship set status = 'REFUNDED', refunded_at = :now where id = :id and status = 'CONFIRMED'")
			.param("now", Timestamp.from(now))
			.param("id", id)
			.update()
	}

	/** The approval-workflow decision (Phase 6 remainder, ADR-019) — `SponsorshipService.approve`/`reject` are the only callers, both already validated the sponsorship is CONFIRMED and PENDING_REVIEW. */
	fun updateReviewStatus(id: UUID, reviewStatus: SponsorshipReviewStatus, reviewedByUserId: UUID?): Int {
		val now = Instant.now()
		return jdbcClient.sql(
			"""
			update sponsorship set review_status = :reviewStatus, reviewed_at = :now, reviewed_by = :reviewedBy
			where id = :id
			""".trimIndent(),
		)
			.param("reviewStatus", reviewStatus.name)
			.param("now", Timestamp.from(now))
			.param("reviewedBy", reviewedByUserId)
			.param("id", id)
			.update()
	}

	fun markRenewalReminderSent(id: UUID): Int {
		val now = Instant.now()
		return jdbcClient.sql("update sponsorship set renewal_reminder_sent_at = :now where id = :id")
			.param("now", Timestamp.from(now))
			.param("id", id)
			.update()
	}

	/**
	 * `SponsorshipRenewalReminderService`'s data source — confirmed, approved
	 * sponsorships whose package placement window ends within [withinDays] and haven't
	 * been reminded yet. A package with no `placement_end_date` never matches (nothing
	 * to remind about). Phase 6 remainder, ADR-019 — deliberately a direct scheduled
	 * query, not routed through the outbox worker (which doesn't exist yet, Phase 8).
	 */
	fun findNeedingRenewalReminder(withinDays: Long): List<SponsorshipRenewalCandidate> =
		jdbcClient.sql(
			"""
			select sp.id as sponsorship_id, sp.organization_id, pkg.name as package_name, pkg.placement_end_date,
			       s.name as sponsor_name, s.contact_email as sponsor_contact_email
			from sponsorship sp
			join sponsorship_package pkg on pkg.id = sp.package_id
			join sponsor s on s.id = sp.sponsor_id
			where sp.status = 'CONFIRMED'
			  and sp.review_status = 'APPROVED'
			  and sp.renewal_reminder_sent_at is null
			  and pkg.placement_end_date is not null
			  and pkg.placement_end_date >= current_date
			  and pkg.placement_end_date <= current_date + make_interval(days => :withinDays::int)
			""".trimIndent(),
		)
			.param("withinDays", withinDays)
			.query { rs, _ ->
				SponsorshipRenewalCandidate(
					sponsorshipId = rs.getObject("sponsorship_id", UUID::class.java),
					organizationId = rs.getObject("organization_id", UUID::class.java),
					packageName = rs.getString("package_name"),
					placementEndDate = rs.getDate("placement_end_date").toLocalDate(),
					sponsorName = rs.getString("sponsor_name"),
					sponsorContactEmail = rs.getString("sponsor_contact_email"),
				)
			}
			.list()

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
			refundedAt = rs.getTimestamp("refunded_at")?.toInstant(),
			reviewStatus = SponsorshipReviewStatus.valueOf(rs.getString("review_status")),
			reviewedAt = rs.getTimestamp("reviewed_at")?.toInstant(),
			reviewedById = rs.getObject("reviewed_by", UUID::class.java),
			renewalReminderSentAt = rs.getTimestamp("renewal_reminder_sent_at")?.toInstant(),
			createdAt = rs.getTimestamp("created_at").toInstant(),
			paymentSource = PaymentSource.valueOf(rs.getString("payment_source")),
		)
}
