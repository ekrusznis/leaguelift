package com.rally26.credit.domain

import java.time.Instant
import java.util.UUID

/** Junction row: which grant funded which `fee_adjustment` (Phase 23) — supports FIFO consumption across multiple grants with a full audit trail. */
data class FamilyCreditApplication(
    val id: UUID,
    val organizationId: UUID,
    val grantId: UUID,
    val feeAdjustmentId: UUID,
    val amountMinor: Long,
    val appliedByUserId: UUID,
    val createdAt: Instant,
)
