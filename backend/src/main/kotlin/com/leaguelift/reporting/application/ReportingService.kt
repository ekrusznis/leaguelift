package com.leaguelift.reporting.application

import com.leaguelift.authorization.application.AuthorizationService
import com.leaguelift.authorization.domain.Capabilities
import com.leaguelift.common.error.NotFoundException
import com.leaguelift.common.util.CsvUtil
import com.leaguelift.common.web.CurrentUser
import com.leaguelift.fee.domain.FeeAssignmentStatus
import com.leaguelift.fee.domain.computeFeeBalance
import com.leaguelift.fee.persistence.FeeAdjustmentRepository
import com.leaguelift.fee.persistence.FeePaymentRepository
import com.leaguelift.fee.persistence.FeeRepository
import com.leaguelift.household.persistence.HouseholdRepository
import com.leaguelift.membership.application.MembershipService
import com.leaguelift.organization.persistence.OrganizationRepository
import com.leaguelift.outbox.persistence.OutboxEventRepository
import com.leaguelift.reporting.persistence.CampaignRevenueRow
import com.leaguelift.reporting.persistence.FeeCollectionRow
import com.leaguelift.reporting.persistence.ProductPerformanceRow
import com.leaguelift.reporting.persistence.RefundRow
import com.leaguelift.reporting.persistence.ReportingRepository
import com.leaguelift.reporting.persistence.SourceTypeRevenueRow
import com.leaguelift.reporting.persistence.TeamRevenueRow
import com.leaguelift.webhook.domain.WebhookProcessingStatus
import com.leaguelift.webhook.persistence.WebhookEventRepository
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

private const val DEFAULT_RANGE_DAYS = 30L
private const val HOUSEHOLD_FEE_ASSIGNMENT_LIMIT = 50

data class RevenueReport(val from: LocalDate, val to: LocalDate, val bySourceType: List<SourceTypeRevenueRow>, val byTeam: List<TeamRevenueRow>) {
	val totalMinor: Long get() = bySourceType.sumOf { it.amountMinor }
}

data class RefundsReport(val from: LocalDate, val to: LocalDate, val refunds: List<RefundRow>) {
	val count: Int get() = refunds.size
	val totalMinor: Long get() = refunds.sumOf { it.amountMinor }
}

/** Shared shape for both the org-wide fee-collections report and one household's fee report (Phase 9 slices 1/2, ADR-025) — same fields, different query scope. */
data class FeeCollectionsReport(val from: LocalDate, val to: LocalDate, val payments: List<FeeCollectionRow>, val outstandingMinor: Long) {
	val collectedMinor: Long get() = payments.sumOf { it.amountMinor }
}

/**
 * Platform-wide report (Phase 9 slice 3, ADR-025). Deliberately excludes subscription
 * revenue (no billing/subscription-plan model exists yet, DESIGN-DOC.md section 19.3
 * open question #12) and dispute rate (disputes/chargebacks aren't tracked anywhere —
 * a Live Payments launch-gate item, section 14.4) — see the ADR for the full list of
 * catalog items this slice couldn't build real data for.
 */
data class PlatformReport(
	val from: LocalDate,
	val to: LocalDate,
	val newOrganizations: Long,
	val activeOrganizations: Long,
	val newCustomers: Long,
	val grossTransactionVolumeMinor: Long,
	val refundedMinor: Long,
	val webhookProcessed: Long,
	val webhookFailed: Long,
	val outboxPending: Long,
	val outboxDeadLetter: Long,
) {
	/** Null (not zero) when there's nothing to divide by — an empty period isn't a 0% refund rate, it's undefined. */
	val refundRatePercent: Double?
		get() = if (grossTransactionVolumeMinor == 0L) null else (refundedMinor.toDouble() / grossTransactionVolumeMinor.toDouble()) * 100.0
}

/**
 * Org reports (Phase 9 slice 1, ADR-025) — every report requires the read-only
 * reporting role boundary (OWNER, ADMINISTRATOR, FINANCE_MANAGER, or VIEWER).
 * Mutating fee/payment operations remain behind their existing manager checks.
 * `from`/`to` default to the trailing 30 days when omitted, a common reporting-tool
 * convention this codebase hadn't needed before (every other date-range-shaped query
 * so far — sponsorship/fee reminders — used a forward-looking "due within N days"
 * window instead of an arbitrary caller-supplied range).
 */
@Service
class ReportingService(
	private val reportingRepository: ReportingRepository,
	private val feeRepository: FeeRepository,
	private val feePaymentRepository: FeePaymentRepository,
	private val feeAdjustmentRepository: FeeAdjustmentRepository,
	private val householdRepository: HouseholdRepository,
	private val organizationRepository: OrganizationRepository,
	private val webhookEventRepository: WebhookEventRepository,
	private val outboxEventRepository: OutboxEventRepository,
	private val membershipService: MembershipService,
	private val authorizationService: AuthorizationService,
) {

	fun getRevenueReport(organizationId: UUID, from: LocalDate?, to: LocalDate?, currentUser: CurrentUser): RevenueReport {
		membershipService.requireReportingRole(organizationId, currentUser)
		val (resolvedFrom, resolvedTo) = resolveRange(from, to)
		val (fromInstant, toInstant) = toInstantRange(resolvedFrom, resolvedTo)
		return RevenueReport(
			resolvedFrom, resolvedTo,
			reportingRepository.revenueBySourceType(organizationId, fromInstant, toInstant),
			reportingRepository.revenueByTeam(organizationId, fromInstant, toInstant),
		)
	}

	fun exportRevenueReportCsv(organizationId: UUID, from: LocalDate?, to: LocalDate?, currentUser: CurrentUser): String {
		val report = getRevenueReport(organizationId, from, to, currentUser)
		val lines = mutableListOf(listOf("Source Type", "Team", "Amount").joinToString(",") { CsvUtil.escape(it) })
		for (row in report.bySourceType) {
			lines += listOf(row.sourceType, "", CsvUtil.formatMinor(row.amountMinor)).joinToString(",") { CsvUtil.escape(it) }
		}
		for (row in report.byTeam) {
			lines += listOf("", row.teamName ?: "Organization-wide", CsvUtil.formatMinor(row.amountMinor)).joinToString(",") { CsvUtil.escape(it) }
		}
		return lines.joinToString("\r\n")
	}

	fun getCampaignBreakdown(organizationId: UUID, from: LocalDate?, to: LocalDate?, currentUser: CurrentUser): List<CampaignRevenueRow> {
		membershipService.requireReportingRole(organizationId, currentUser)
		val (resolvedFrom, resolvedTo) = resolveRange(from, to)
		val (fromInstant, toInstant) = toInstantRange(resolvedFrom, resolvedTo)
		return reportingRepository.revenueByCampaign(organizationId, fromInstant, toInstant)
	}

	fun getProductPerformance(organizationId: UUID, from: LocalDate?, to: LocalDate?, currentUser: CurrentUser): List<ProductPerformanceRow> {
		membershipService.requireReportingRole(organizationId, currentUser)
		val (resolvedFrom, resolvedTo) = resolveRange(from, to)
		val (fromInstant, toInstant) = toInstantRange(resolvedFrom, resolvedTo)
		return reportingRepository.productPerformance(organizationId, fromInstant, toInstant)
	}

	fun getRefundsReport(organizationId: UUID, from: LocalDate?, to: LocalDate?, currentUser: CurrentUser): RefundsReport {
		membershipService.requireReportingRole(organizationId, currentUser)
		val (resolvedFrom, resolvedTo) = resolveRange(from, to)
		val (fromInstant, toInstant) = toInstantRange(resolvedFrom, resolvedTo)
		return RefundsReport(resolvedFrom, resolvedTo, reportingRepository.refunds(organizationId, fromInstant, toInstant))
	}

	fun getFeeCollectionsReport(organizationId: UUID, from: LocalDate?, to: LocalDate?, currentUser: CurrentUser): FeeCollectionsReport {
		membershipService.requireReportingRole(organizationId, currentUser)
		val (resolvedFrom, resolvedTo) = resolveRange(from, to)
		val payments = reportingRepository.feeCollections(organizationId, resolvedFrom, resolvedTo)
		val outstanding = feeRepository.getFinancialSummary(organizationId).outstandingMinor
		return FeeCollectionsReport(resolvedFrom, resolvedTo, payments, outstanding)
	}

	/**
	 * A single household's fee report (Phase 9 slice 2, ADR-025) — deliberately scoped to
	 * fees/payments/balance only per the founder's decision; credits/orders/contributions
	 * stay out until household-level order/contribution attribution and credit rules
	 * exist (see the ADR). Requires org membership (any active member, matching
	 * `FeeService.listForHousehold`'s existing bar), not a household-guardian check —
	 * this is an org-facing report on a specific household, not the household's own
	 * self-service Parent dashboard view.
	 */
	fun getHouseholdFeeReport(organizationId: UUID, householdId: UUID, from: LocalDate?, to: LocalDate?, currentUser: CurrentUser): FeeCollectionsReport {
		membershipService.requireActiveMembership(organizationId, currentUser)
		householdRepository.findById(householdId, organizationId)
			?: throw NotFoundException("HOUSEHOLD_NOT_FOUND", "The household could not be found.")
		val (resolvedFrom, resolvedTo) = resolveRange(from, to)
		val payments = reportingRepository.feeCollections(organizationId, resolvedFrom, resolvedTo, householdId)
		val outstanding = householdOutstandingBalance(organizationId, householdId)
		return FeeCollectionsReport(resolvedFrom, resolvedTo, payments, outstanding)
	}

	fun getPlatformReport(from: LocalDate?, to: LocalDate?, currentUser: CurrentUser): PlatformReport {
		authorizationService.requirePlatformCapability(currentUser, Capabilities.PLATFORM_AUDIT_VIEW)
		val (resolvedFrom, resolvedTo) = resolveRange(from, to)
		val (fromInstant, toInstant) = toInstantRange(resolvedFrom, resolvedTo)
		return PlatformReport(
			resolvedFrom, resolvedTo,
			newOrganizations = reportingRepository.countNewOrganizations(fromInstant, toInstant),
			activeOrganizations = organizationRepository.countAllForPlatformAdmin(),
			newCustomers = reportingRepository.countNewCustomers(fromInstant, toInstant),
			grossTransactionVolumeMinor = reportingRepository.platformGrossTransactionVolume(fromInstant, toInstant),
			refundedMinor = reportingRepository.platformRefundedAmount(fromInstant, toInstant),
			webhookProcessed = webhookEventRepository.countByProcessingStatus(WebhookProcessingStatus.PROCESSED),
			webhookFailed = webhookEventRepository.countByProcessingStatus(WebhookProcessingStatus.FAILED),
			outboxPending = outboxEventRepository.countByStatus("PENDING"),
			outboxDeadLetter = outboxEventRepository.countByStatus("DEAD_LETTER"),
		)
	}

	private fun householdOutstandingBalance(organizationId: UUID, householdId: UUID): Long {
		val outstandingStatuses = setOf(FeeAssignmentStatus.OPEN, FeeAssignmentStatus.PARTIALLY_PAID)
		return feeRepository.findByHousehold(householdId, organizationId, offset = 0, limit = HOUSEHOLD_FEE_ASSIGNMENT_LIMIT)
			.filter { it.status in outstandingStatuses }
			.sumOf { assignment ->
				val paid = feePaymentRepository.sumActiveByAssignment(assignment.id, organizationId)
				val adjusted = feeAdjustmentRepository.sumActiveByAssignment(assignment.id, organizationId)
				computeFeeBalance(assignment.originalAmountMinor, paid, adjusted).balanceMinor
			}
	}

	private fun resolveRange(from: LocalDate?, to: LocalDate?): Pair<LocalDate, LocalDate> {
		val resolvedTo = to ?: LocalDate.now()
		val resolvedFrom = from ?: resolvedTo.minusDays(DEFAULT_RANGE_DAYS)
		return resolvedFrom to resolvedTo
	}

	/** `to` is treated as inclusive of the whole calendar day. */
	private fun toInstantRange(from: LocalDate, to: LocalDate): Pair<Instant, Instant> =
		from.atStartOfDay(ZoneOffset.UTC).toInstant() to to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()
}
