package com.rally26.fee.domain

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** Org-wide read model for the collections dashboard and CSV export — a FeeAssignment joined with household/participant display names and its computed balance. */
data class FeeAssignmentSummary(
	val id: UUID,
	val organizationId: UUID,
	val householdId: UUID,
	val householdName: String,
	val participantId: UUID?,
	val participantName: String?,
	val feeTemplateId: UUID?,
	val description: String,
	val originalAmountMinor: Long,
	val currency: String,
	val dueDate: LocalDate?,
	val status: FeeAssignmentStatus,
	val paidMinor: Long,
	val adjustedMinor: Long,
	val createdAt: Instant,
	val updatedAt: Instant,
) {
	val balance: FeeBalance get() = computeFeeBalance(originalAmountMinor, paidMinor, adjustedMinor)
}

/** Org-wide aggregate for the owner dashboard's Financial Overview card. */
data class OrganizationFeeFinancialSummary(
	val feesAssignedMinor: Long,
	val feesCollectedMinor: Long,
	val outstandingMinor: Long,
)
