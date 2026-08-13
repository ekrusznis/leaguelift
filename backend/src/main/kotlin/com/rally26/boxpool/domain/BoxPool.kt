package com.rally26.boxpool.domain

import java.time.Instant
import java.util.UUID

data class BoxPool(
    val id: UUID,
    val campaignId: UUID,
    val organizationId: UUID,
    val sport: String,
    val rows: Int,
    val cols: Int,
    val pricePerBoxMinor: Long,
    val rowAxisLabel: String?,
    val colAxisLabel: String?,
    val prizeDescription: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

/** A box's own claim lifecycle — separate from the linked `Contribution`'s own PENDING/CONFIRMED/CANCELED/REFUNDED status. `RESERVED` is time-boxed (`reservedUntil`) and lazily treated as available again once expired, rather than needing a scheduled sweep job. */
enum class BoxPoolBoxStatus { OPEN, RESERVED, CLAIMED }

data class BoxPoolBox(
    val id: UUID,
    val boxPoolId: UUID,
    val rowIndex: Int,
    val colIndex: Int,
    val status: BoxPoolBoxStatus,
    val claimantName: String?,
    val claimantEmail: String?,
    val contributionId: UUID?,
    val reservedUntil: Instant?,
    val claimedAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant,
)
