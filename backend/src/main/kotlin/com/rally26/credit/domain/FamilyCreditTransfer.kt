package com.rally26.credit.domain

import java.time.Instant
import java.util.UUID

/**
 * Requires organization owner/manager review before credit actually moves
 * (mirrors ProfileCorrectionStatus's PENDING/APPROVED/REJECTED review pattern).
 * On PENDING, the sender's grants are already decremented (held) so the
 * balance can't be double-spent; the receiver's grant is only created on
 * APPROVED. REJECTED restores the held amount to the sender as a new grant.
 */
enum class CreditTransferStatus { PENDING, APPROVED, REJECTED }

/** Peer-to-peer family credit transfer (Phase 23) — only real when `OrganizationCreditSettings.p2pTransferEnabled` is true for the organization; ships false everywhere by default. */
data class FamilyCreditTransfer(
    val id: UUID,
    val organizationId: UUID,
    val fromHouseholdId: UUID,
    val toHouseholdId: UUID,
    val amountMinor: Long,
    val initiatedByUserId: UUID,
    val status: CreditTransferStatus,
    val reviewedByUserId: UUID?,
    val reviewNote: String?,
    val createdAt: Instant,
    val reviewedAt: Instant?,
)
