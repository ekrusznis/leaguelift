package com.rally26.reporting.persistence

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

data class SourceTypeRevenueRow(val sourceType: String, val amountMinor: Long)
data class TeamRevenueRow(val teamId: UUID?, val teamName: String?, val amountMinor: Long)
data class CampaignRevenueRow(val campaignId: UUID, val campaignName: String, val amountMinor: Long)
data class ProductPerformanceRow(val productId: UUID, val productName: String, val quantitySold: Long, val revenueMinor: Long)
data class RefundRow(val sourceType: String, val sourceId: UUID, val amountMinor: Long, val effectiveAt: Instant)
data class FeeCollectionRow(val feePaymentId: UUID, val householdId: UUID, val householdName: String, val amountMinor: Long, val paidAt: java.time.LocalDate)

/**
 * Org reports (Phase 9 slice 1, ADR-025) — every query here is read-only and reaches
 * across module boundaries (`ledger_entry`, `contribution`, `campaign`, `"order"`,
 * `store`, `order_item`, `product`, `fee_payment`, `household`, `team`) the same way
 * `PlatformAdminDashboardService`/`OwnerDashboardService` already do for their own
 * summary cards — a reporting module is exactly the place that's expected to read
 * across everything, unlike a feature module's own repository.
 */
@Repository
class ReportingRepository(private val jdbcClient: JdbcClient) {

	/** "Revenue by source" — CONTRIBUTION (campaign contributions + sponsorships, which reuse the CONTRIBUTION entry type) and GROSS_SALE (orders), by `ledger_entry.source_type`. */
	fun revenueBySourceType(organizationId: UUID, from: Instant, to: Instant): List<SourceTypeRevenueRow> =
		jdbcClient.sql(
			"""
			select source_type, coalesce(sum(amount_minor), 0) as amount_minor
			from ledger_entry
			where organization_id = :organizationId and direction = 'CREDIT'
			  and entry_type in ('CONTRIBUTION', 'GROSS_SALE')
			  and effective_at >= :from and effective_at < :to
			group by source_type
			order by amount_minor desc
			""".trimIndent(),
		)
			.param("organizationId", organizationId).param("from", Timestamp.from(from)).param("to", Timestamp.from(to))
			.query { rs, _ -> SourceTypeRevenueRow(rs.getString("source_type"), rs.getLong("amount_minor")) }
			.list()

	/**
	 * "Revenue by team" — only contributions and orders can be team-attributed today
	 * (via `campaign.team_id`/`store.team_id`); sponsorship packages have no team_id
	 * (org-level only), so sponsorship revenue always falls into the null/"organization-
	 * wide" bucket here. Tournament attribution isn't possible at all — no
	 * revenue-generating entity (campaign, store, sponsorship package) has a
	 * `tournament_id` in the current schema (see ADR-025).
	 */
	fun revenueByTeam(organizationId: UUID, from: Instant, to: Instant): List<TeamRevenueRow> =
		jdbcClient.sql(
			"""
			select team_id, team_name, coalesce(sum(amount_minor), 0) as amount_minor
			from (
				select c.team_id as team_id, t.name as team_name, le.amount_minor as amount_minor
				from ledger_entry le
				join contribution c on c.id = le.source_id and le.source_type = 'CONTRIBUTION'
				left join team t on t.id = c.team_id
				where le.organization_id = :organizationId and le.direction = 'CREDIT' and le.entry_type = 'CONTRIBUTION'
				  and le.effective_at >= :from and le.effective_at < :to
				union all
				select s.team_id as team_id, t.name as team_name, le.amount_minor as amount_minor
				from ledger_entry le
				join "order" o on o.id = le.source_id and le.source_type = 'ORDER'
				join store s on s.id = o.store_id
				left join team t on t.id = s.team_id
				where le.organization_id = :organizationId and le.direction = 'CREDIT' and le.entry_type = 'GROSS_SALE'
				  and le.effective_at >= :from and le.effective_at < :to
			) attributed
			group by team_id, team_name
			order by amount_minor desc
			""".trimIndent(),
		)
			.param("organizationId", organizationId).param("from", Timestamp.from(from)).param("to", Timestamp.from(to))
			.query { rs, _ ->
				TeamRevenueRow(rs.getObject("team_id", UUID::class.java), rs.getString("team_name"), rs.getLong("amount_minor"))
			}
			.list()

	/** Campaign has no `team_id`/`tournament_id` restriction on inclusion here — every campaign that raised anything in range shows up, org-wide or team-owned alike. */
	fun revenueByCampaign(organizationId: UUID, from: Instant, to: Instant): List<CampaignRevenueRow> =
		jdbcClient.sql(
			"""
			select c.id as campaign_id, c.name as campaign_name, coalesce(sum(le.amount_minor), 0) as amount_minor
			from ledger_entry le
			join contribution c on c.id = le.source_id and le.source_type = 'CONTRIBUTION'
			where le.organization_id = :organizationId and le.direction = 'CREDIT' and le.entry_type = 'CONTRIBUTION'
			  and le.effective_at >= :from and le.effective_at < :to
			group by c.id, c.name
			order by amount_minor desc
			""".trimIndent(),
		)
			.param("organizationId", organizationId).param("from", Timestamp.from(from)).param("to", Timestamp.from(to))
			.query { rs, _ -> CampaignRevenueRow(rs.getObject("campaign_id", UUID::class.java), rs.getString("campaign_name"), rs.getLong("amount_minor")) }
			.list()

	/** Product performance is scoped to confirmed orders' `confirmed_at` falling in range, not the order's original `created_at`. */
	fun productPerformance(organizationId: UUID, from: Instant, to: Instant): List<ProductPerformanceRow> =
		jdbcClient.sql(
			"""
			select p.id as product_id, p.name as product_name,
			       sum(oi.quantity) as quantity_sold, sum(oi.quantity * oi.unit_price_minor) as revenue_minor
			from order_item oi
			join "order" o on o.id = oi.order_id
			join product_variant pv on pv.id = oi.product_variant_id
			join product p on p.id = pv.product_id
			where o.organization_id = :organizationId and o.status = 'CONFIRMED'
			  and o.confirmed_at >= :from and o.confirmed_at < :to
			group by p.id, p.name
			order by revenue_minor desc
			""".trimIndent(),
		)
			.param("organizationId", organizationId).param("from", Timestamp.from(from)).param("to", Timestamp.from(to))
			.query { rs, _ ->
				ProductPerformanceRow(rs.getObject("product_id", UUID::class.java), rs.getString("product_name"), rs.getLong("quantity_sold"), rs.getLong("revenue_minor"))
			}
			.list()

	fun refunds(organizationId: UUID, from: Instant, to: Instant): List<RefundRow> =
		jdbcClient.sql(
			"""
			select source_type, source_id, amount_minor, effective_at
			from ledger_entry
			where organization_id = :organizationId and entry_type = 'REFUND' and direction = 'DEBIT'
			  and effective_at >= :from and effective_at < :to
			order by effective_at desc
			""".trimIndent(),
		)
			.param("organizationId", organizationId).param("from", Timestamp.from(from)).param("to", Timestamp.from(to))
			.query { rs, _ ->
				RefundRow(rs.getString("source_type"), rs.getObject("source_id", UUID::class.java), rs.getLong("amount_minor"), rs.getTimestamp("effective_at").toInstant())
			}
			.list()

	/** Platform-wide new-organization count in range (Phase 9 slice 3, ADR-025) — growth metric, distinct from `OrganizationRepository.countAllForPlatformAdmin`'s all-time total. */
	fun countNewOrganizations(from: Instant, to: Instant): Long =
		jdbcClient.sql("select count(*) from organization where created_at >= :from and created_at < :to")
			.param("from", Timestamp.from(from)).param("to", Timestamp.from(to))
			.query(Long::class.java).single()

	/** Platform-wide new-account count in range — "customers" per DESIGN-DOC.md section 13's Reporting catalog. */
	fun countNewCustomers(from: Instant, to: Instant): Long =
		jdbcClient.sql("select count(*) from app_user where created_at >= :from and created_at < :to")
			.param("from", Timestamp.from(from)).param("to", Timestamp.from(to))
			.query(Long::class.java).single()

	/** Platform-wide gross transaction volume (GTV) in range — every CONTRIBUTION/GROSS_SALE credit across every organization, no org filter (mirrors `PlatformAdminDashboardService.getPaymentsSummary`'s existing all-time equivalent, but date-ranged). */
	fun platformGrossTransactionVolume(from: Instant, to: Instant): Long =
		jdbcClient.sql(
			"""
			select coalesce(sum(amount_minor), 0) from ledger_entry
			where direction = 'CREDIT' and entry_type in ('CONTRIBUTION', 'GROSS_SALE')
			  and effective_at >= :from and effective_at < :to
			""".trimIndent(),
		)
			.param("from", Timestamp.from(from)).param("to", Timestamp.from(to))
			.query(Long::class.java).single()

	fun platformRefundedAmount(from: Instant, to: Instant): Long =
		jdbcClient.sql(
			"""
			select coalesce(sum(amount_minor), 0) from ledger_entry
			where entry_type = 'REFUND' and direction = 'DEBIT'
			  and effective_at >= :from and effective_at < :to
			""".trimIndent(),
		)
			.param("from", Timestamp.from(from)).param("to", Timestamp.from(to))
			.query(Long::class.java).single()

	/**
	 * Fee payments actually recorded (`paid_at`) in range — distinct from
	 * `FeeRepository.getFinancialSummary`'s point-in-time outstanding/collected-to-date
	 * totals. [householdId] narrows to one household's payments (Phase 9 slice 2's
	 * household report); omitted (null) for the org-wide fee-collections report.
	 */
	fun feeCollections(organizationId: UUID, from: java.time.LocalDate, to: java.time.LocalDate, householdId: UUID? = null): List<FeeCollectionRow> =
		jdbcClient.sql(
			"""
			select fp.id as fee_payment_id, fp.household_id, h.display_name as household_name, fp.amount_minor, fp.paid_at
			from fee_payment fp
			join household h on h.id = fp.household_id
			where fp.organization_id = :organizationId and fp.voided_at is null
			  and fp.paid_at >= :from and fp.paid_at <= :to
			  and (:householdId::uuid is null or fp.household_id = :householdId)
			order by fp.paid_at desc
			""".trimIndent(),
		)
			.param("organizationId", organizationId).param("from", java.sql.Date.valueOf(from)).param("to", java.sql.Date.valueOf(to))
			.param("householdId", householdId)
			.query { rs, _ ->
				FeeCollectionRow(
					rs.getObject("fee_payment_id", UUID::class.java), rs.getObject("household_id", UUID::class.java),
					rs.getString("household_name"), rs.getLong("amount_minor"), rs.getDate("paid_at").toLocalDate(),
				)
			}
			.list()
}
