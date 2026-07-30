package com.leaguelift.reporting.web

import com.leaguelift.reporting.persistence.CampaignRevenueRow
import com.leaguelift.reporting.persistence.FeeCollectionRow
import com.leaguelift.reporting.persistence.ProductPerformanceRow
import com.leaguelift.reporting.persistence.RefundRow
import com.leaguelift.reporting.persistence.SourceTypeRevenueRow
import com.leaguelift.reporting.persistence.TeamRevenueRow
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class SourceTypeRevenueResponse(val sourceType: String, val amountMinor: Long)
fun SourceTypeRevenueRow.toResponse() = SourceTypeRevenueResponse(sourceType, amountMinor)

data class TeamRevenueResponse(val teamId: UUID?, val teamName: String?, val amountMinor: Long)
fun TeamRevenueRow.toResponse() = TeamRevenueResponse(teamId, teamName, amountMinor)

data class RevenueReportResponse(
	val from: LocalDate,
	val to: LocalDate,
	val totalMinor: Long,
	val bySourceType: List<SourceTypeRevenueResponse>,
	val byTeam: List<TeamRevenueResponse>,
)

data class CampaignRevenueResponse(val campaignId: UUID, val campaignName: String, val amountMinor: Long)
fun CampaignRevenueRow.toResponse() = CampaignRevenueResponse(campaignId, campaignName, amountMinor)

data class ProductPerformanceResponse(val productId: UUID, val productName: String, val quantitySold: Long, val revenueMinor: Long)
fun ProductPerformanceRow.toResponse() = ProductPerformanceResponse(productId, productName, quantitySold, revenueMinor)

data class RefundResponse(val sourceType: String, val sourceId: UUID, val amountMinor: Long, val effectiveAt: Instant)
fun RefundRow.toResponse() = RefundResponse(sourceType, sourceId, amountMinor, effectiveAt)

data class RefundsReportResponse(val from: LocalDate, val to: LocalDate, val count: Int, val totalMinor: Long, val refunds: List<RefundResponse>)

data class FeeCollectionResponse(val feePaymentId: UUID, val householdId: UUID, val householdName: String, val amountMinor: Long, val paidAt: LocalDate)
fun FeeCollectionRow.toResponse() = FeeCollectionResponse(feePaymentId, householdId, householdName, amountMinor, paidAt)

data class FeeCollectionsReportResponse(
	val from: LocalDate,
	val to: LocalDate,
	val collectedMinor: Long,
	val outstandingMinor: Long,
	val payments: List<FeeCollectionResponse>,
)

data class PlatformReportResponse(
	val from: LocalDate,
	val to: LocalDate,
	val newOrganizations: Long,
	val activeOrganizations: Long,
	val newCustomers: Long,
	val grossTransactionVolumeMinor: Long,
	val refundedMinor: Long,
	val refundRatePercent: Double?,
	val webhookProcessed: Long,
	val webhookFailed: Long,
	val outboxPending: Long,
	val outboxDeadLetter: Long,
)
