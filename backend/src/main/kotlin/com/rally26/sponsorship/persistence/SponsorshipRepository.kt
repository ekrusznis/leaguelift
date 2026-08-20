package com.rally26.sponsorship.persistence

import com.rally26.finance.domain.PaymentSource
import com.rally26.sponsorship.domain.Sponsorship
import com.rally26.sponsorship.domain.SponsorshipReviewStatus
import com.rally26.sponsorship.domain.SponsorshipSearchCriteria
import com.rally26.sponsorship.domain.SponsorshipSearchRow
import com.rally26.sponsorship.domain.SponsorshipSearchSort
import com.rally26.sponsorship.domain.SponsorshipStatus
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
class SponsorshipRepository(
    private val jdbcClient: JdbcClient,
) {
    fun findById(id: UUID): Sponsorship? =
        jdbcClient
            .sql("select $COLUMNS from sponsorship where id = :id")
            .param("id", id)
            .query(::mapRow)
            .optional()
            .orElse(null)

    fun findByStripeCheckoutSessionId(sessionId: String): Sponsorship? =
        jdbcClient
            .sql("select $COLUMNS from sponsorship where stripe_checkout_session_id = :sessionId")
            .param("sessionId", sessionId)
            .query(::mapRow)
            .optional()
            .orElse(null)

    /** Dispute routing (2026-08-12) — a Stripe dispute references a payment_intent, not a checkout session. */
    fun findByStripePaymentIntentId(paymentIntentId: String): Sponsorship? =
        jdbcClient
            .sql("select $COLUMNS from sponsorship where stripe_payment_intent_id = :paymentIntentId")
            .param("paymentIntentId", paymentIntentId)
            .query(::mapRow)
            .optional()
            .orElse(null)

    /** "Confirmed" here means "was ever confirmed" — mirrors `ContributionRepository.listConfirmedForCampaign`'s comment; a later refund still shows in this admin-facing history. */
    fun listConfirmedForPackage(
        packageId: UUID,
        offset: Int,
        limit: Int,
    ): List<Sponsorship> =
        jdbcClient
            .sql(
                """
                select $COLUMNS from sponsorship
                where package_id = :packageId and status in ('CONFIRMED', 'REFUNDED')
                order by confirmed_at desc
                offset :offset limit :limit
                """.trimIndent(),
            ).param("packageId", packageId)
            .param("offset", offset)
            .param("limit", limit)
            .query(::mapRow)
            .list()

    fun countConfirmedForPackage(packageId: UUID): Long =
        jdbcClient
            .sql("select count(*) from sponsorship where package_id = :packageId and status in ('CONFIRMED', 'REFUNDED')")
            .param("packageId", packageId)
            .query(Long::class.java)
            .single()

    /** The public sponsor directory's data source — confirmed AND org-admin-approved sponsorships only (Phase 6 remainder approval workflow, ADR-019): a paid-but-not-yet-reviewed sponsorship must not appear here. */
    fun findConfirmedForOrganization(organizationId: UUID): List<Sponsorship> =
        jdbcClient
            .sql(
                """
                select $COLUMNS from sponsorship
                where organization_id = :organizationId and status = 'CONFIRMED' and review_status = 'APPROVED'
                order by confirmed_at desc
                """.trimIndent(),
            ).param("organizationId", organizationId)
            .query(::mapRow)
            .list()

    /** The org-admin approval queue — confirmed sponsorships still awaiting a review decision. */
    fun listPendingReview(
        organizationId: UUID,
        offset: Int,
        limit: Int,
    ): List<Sponsorship> =
        jdbcClient
            .sql(
                """
                select $COLUMNS from sponsorship
                where organization_id = :organizationId and status = 'CONFIRMED' and review_status = 'PENDING_REVIEW'
                order by confirmed_at asc
                offset :offset limit :limit
                """.trimIndent(),
            ).param("organizationId", organizationId)
            .param("offset", offset)
            .param("limit", limit)
            .query(::mapRow)
            .list()

    fun countPendingReview(organizationId: UUID): Long =
        jdbcClient
            .sql(
                "select count(*) from sponsorship where organization_id = :organizationId and status = 'CONFIRMED' and review_status = 'PENDING_REVIEW'",
            ).param("organizationId", organizationId)
            .query(Long::class.java)
            .single()

    /**
     * `/sponsorships/search` — the "Review pending sponsorships" tab's underlying data
     * source (`frontend/src/features/sponsorship/searchApi.ts`'s `useSponsorshipSearch`)
     * had no backend mapping at all (LR-028, same class as LR-016/018/020/025/026/027).
     * Same base CONFIRMED/REFUNDED restriction as [findConfirmedForOrganization], but
     * `reviewStatus` is a real optional filter here rather than hardcoded, since this
     * view needs to show every review state, not just one queue. Joins `sponsor` and
     * `sponsorship_package` directly for the flat shape the frontend needs, rather than
     * N+1'ing per row.
     */
    fun search(
        organizationId: UUID,
        criteria: SponsorshipSearchCriteria,
        offset: Int,
        limit: Int,
    ): List<SponsorshipSearchRow> {
        val built = buildSearchSql(organizationId, criteria, countOnly = false)
        var statement = jdbcClient.sql("${built.first} offset :offset limit :limit").param("offset", offset).param("limit", limit)
        built.second.forEach { (name, value) -> statement = statement.param(name, value) }
        return statement.query(::mapSearchRow).list()
    }

    fun countSearch(
        organizationId: UUID,
        criteria: SponsorshipSearchCriteria,
    ): Long {
        val built = buildSearchSql(organizationId, criteria, countOnly = true)
        var statement = jdbcClient.sql(built.first)
        built.second.forEach { (name, value) -> statement = statement.param(name, value) }
        return statement.query(Long::class.java).single()
    }

    private fun buildSearchSql(
        organizationId: UUID,
        criteria: SponsorshipSearchCriteria,
        countOnly: Boolean,
    ): Pair<String, Map<String, Any>> {
        val sql =
            StringBuilder(
                if (countOnly) {
                    """select count(*) from sponsorship sh
                       join sponsor sp on sp.id = sh.sponsor_id
                       join sponsorship_package pkg on pkg.id = sh.package_id"""
                } else {
                    """select sh.id, sh.package_id, pkg.name as package_name, sh.status, sh.payment_source, sh.amount_minor, sh.currency,
                       sh.sponsor_id, sp.name as sponsor_name, sp.contact_email as sponsor_contact_email, sp.company_name as sponsor_company_name,
                       sh.confirmed_at, sh.refunded_at, sh.review_status, sh.reviewed_at, sh.created_at
                       from sponsorship sh
                       join sponsor sp on sp.id = sh.sponsor_id
                       join sponsorship_package pkg on pkg.id = sh.package_id"""
                },
            )
        sql.append(" where sh.organization_id = :organizationId and sh.status in ('CONFIRMED', 'REFUNDED')")
        val params = linkedMapOf<String, Any>("organizationId" to organizationId)

        criteria.packageId?.let {
            sql.append(" and sh.package_id = :packageId")
            params["packageId"] = it
        }
        criteria.status?.let {
            sql.append(" and sh.status = :status")
            params["status"] = it.name
        }
        criteria.reviewStatus?.let {
            sql.append(" and sh.review_status = :reviewStatus")
            params["reviewStatus"] = it.name
        }
        criteria.paymentSource?.let {
            sql.append(" and sh.payment_source = :paymentSource")
            params["paymentSource"] = it.name
        }
        criteria.keyword?.trim()?.takeIf { it.isNotEmpty() }?.let { keyword ->
            sql.append(
                " and (lower(sp.name) like :keyword or lower(coalesce(sp.contact_email, '')) like :keyword" +
                    " or lower(coalesce(sp.company_name, '')) like :keyword)",
            )
            params["keyword"] = "%${keyword.lowercase()}%"
        }

        if (!countOnly) {
            sql.append(
                when (criteria.sort) {
                    SponsorshipSearchSort.NEWEST -> " order by sh.created_at desc"
                    SponsorshipSearchSort.OLDEST -> " order by sh.created_at asc"
                    SponsorshipSearchSort.SPONSOR_ASC -> " order by lower(sp.name) asc"
                    SponsorshipSearchSort.AMOUNT_ASC -> " order by sh.amount_minor asc"
                    SponsorshipSearchSort.AMOUNT_DESC -> " order by sh.amount_minor desc"
                    SponsorshipSearchSort.PACKAGE_ASC -> " order by lower(pkg.name) asc"
                    SponsorshipSearchSort.REVIEW_STATUS_ASC -> " order by sh.review_status asc, sh.created_at asc"
                },
            )
        }
        return sql.toString() to params
    }

    private fun mapSearchRow(
        rs: java.sql.ResultSet,
        rowNum: Int,
    ): SponsorshipSearchRow =
        SponsorshipSearchRow(
            id = rs.getObject("id", UUID::class.java),
            packageId = rs.getObject("package_id", UUID::class.java),
            packageName = rs.getString("package_name"),
            status = SponsorshipStatus.valueOf(rs.getString("status")),
            paymentSource = PaymentSource.valueOf(rs.getString("payment_source")),
            amountMinor = rs.getLong("amount_minor"),
            currency = rs.getString("currency"),
            sponsorId = rs.getObject("sponsor_id", UUID::class.java),
            sponsorName = rs.getString("sponsor_name"),
            sponsorContactEmail = rs.getString("sponsor_contact_email"),
            sponsorCompanyName = rs.getString("sponsor_company_name"),
            confirmedAt = rs.getTimestamp("confirmed_at")?.toInstant(),
            refundedAt = rs.getTimestamp("refunded_at")?.toInstant(),
            reviewStatus = SponsorshipReviewStatus.valueOf(rs.getString("review_status")),
            reviewedAt = rs.getTimestamp("reviewed_at")?.toInstant(),
            createdAt = rs.getTimestamp("created_at").toInstant(),
        )

    /** Inserted before Stripe returns a checkout session id — see `attachStripeSession` (mirrors `ContributionRepository.insertPending`). */
    fun insertPending(
        organizationId: UUID,
        packageId: UUID,
        sponsorId: UUID,
        amountMinor: Long,
        currency: String,
    ): Sponsorship {
        val id = UUID.randomUUID()
        val now = Instant.now()
        jdbcClient
            .sql(
                """
                insert into sponsorship (id, organization_id, package_id, sponsor_id, amount_minor, currency, status, created_at)
                values (:id, :organizationId, :packageId, :sponsorId, :amountMinor, :currency, 'PENDING', :now)
                """.trimIndent(),
            ).param("id", id)
            .param("organizationId", organizationId)
            .param("packageId", packageId)
            .param("sponsorId", sponsorId)
            .param("amountMinor", amountMinor)
            .param("currency", currency)
            .param("now", Timestamp.from(now))
            .update()
        return Sponsorship(
            id,
            organizationId,
            packageId,
            sponsorId,
            amountMinor,
            currency,
            SponsorshipStatus.PENDING,
            null,
            null,
            null,
            null,
            SponsorshipReviewStatus.PENDING_REVIEW,
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
            .sql("update sponsorship set stripe_checkout_session_id = :sessionId where id = :id")
            .param("sessionId", stripeCheckoutSessionId)
            .param("id", id)
            .update()

    fun insertOfflinePending(
        organizationId: UUID,
        packageId: UUID,
        sponsorId: UUID,
        amountMinor: Long,
        currency: String,
    ): Sponsorship {
        val id = UUID.randomUUID()
        val now = Instant.now()
        jdbcClient
            .sql(
                """
                insert into sponsorship
                	(id, organization_id, package_id, sponsor_id, amount_minor, currency, status, payment_source, created_at)
                values
                	(:id, :organizationId, :packageId, :sponsorId, :amountMinor, :currency, 'PENDING', 'OFFLINE', :now)
                """.trimIndent(),
            ).param("id", id)
            .param("organizationId", organizationId)
            .param("packageId", packageId)
            .param("sponsorId", sponsorId)
            .param("amountMinor", amountMinor)
            .param("currency", currency)
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
                update sponsorship set status = 'CONFIRMED', confirmed_at = :confirmedAt
                where id = :id and status = 'PENDING' and payment_source = 'OFFLINE'
                """.trimIndent(),
            ).param("confirmedAt", Timestamp.from(confirmedAt))
            .param("id", id)
            .update()

    /** Only flips PENDING -> CONFIRMED. Returns rows affected (0 if already confirmed — the webhook idempotency guard). `review_status` is left at its PENDING_REVIEW default — confirmation alone must never grant public visibility (Phase 6 remainder approval workflow, ADR-019). */
    fun markConfirmed(
        id: UUID,
        stripePaymentIntentId: String?,
    ): Int {
        val now = Instant.now()
        return jdbcClient
            .sql(
                """
                update sponsorship set status = 'CONFIRMED', confirmed_at = :now, stripe_payment_intent_id = :paymentIntentId
                where id = :id and status = 'PENDING'
                """.trimIndent(),
            ).param("now", Timestamp.from(now))
            .param("paymentIntentId", stripePaymentIntentId)
            .param("id", id)
            .update()
    }

    /** Only flips CONFIRMED -> REFUNDED — mirrors `ContributionRepository.markRefunded`/`OrderRepository.markRefunded` exactly. */
    fun markRefunded(id: UUID): Int {
        val now = Instant.now()
        return jdbcClient
            .sql("update sponsorship set status = 'REFUNDED', refunded_at = :now where id = :id and status = 'CONFIRMED'")
            .param("now", Timestamp.from(now))
            .param("id", id)
            .update()
    }

    /** The approval-workflow decision (Phase 6 remainder, ADR-019) — `SponsorshipService.approve`/`reject` are the only callers, both already validated the sponsorship is CONFIRMED and PENDING_REVIEW. */
    fun updateReviewStatus(
        id: UUID,
        reviewStatus: SponsorshipReviewStatus,
        reviewedByUserId: UUID?,
    ): Int {
        val now = Instant.now()
        return jdbcClient
            .sql(
                """
                update sponsorship set review_status = :reviewStatus, reviewed_at = :now, reviewed_by = :reviewedBy
                where id = :id
                """.trimIndent(),
            ).param("reviewStatus", reviewStatus.name)
            .param("now", Timestamp.from(now))
            .param("reviewedBy", reviewedByUserId)
            .param("id", id)
            .update()
    }

    fun markRenewalReminderSent(id: UUID): Int {
        val now = Instant.now()
        return jdbcClient
            .sql("update sponsorship set renewal_reminder_sent_at = :now where id = :id")
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
        jdbcClient
            .sql(
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
            ).param("withinDays", withinDays)
            .query { rs, _ ->
                SponsorshipRenewalCandidate(
                    sponsorshipId = rs.getObject("sponsorship_id", UUID::class.java),
                    organizationId = rs.getObject("organization_id", UUID::class.java),
                    packageName = rs.getString("package_name"),
                    placementEndDate = rs.getDate("placement_end_date").toLocalDate(),
                    sponsorName = rs.getString("sponsor_name"),
                    sponsorContactEmail = rs.getString("sponsor_contact_email"),
                )
            }.list()

    private fun mapRow(
        rs: java.sql.ResultSet,
        rowNum: Int,
    ): Sponsorship =
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
