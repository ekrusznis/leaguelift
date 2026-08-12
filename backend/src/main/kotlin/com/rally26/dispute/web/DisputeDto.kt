package com.rally26.dispute.web

import com.rally26.dispute.domain.DisputeSourceType
import com.rally26.dispute.domain.DisputeStatus
import com.rally26.dispute.domain.PaymentDispute
import java.time.Instant
import java.util.UUID

data class DisputeResponse(
    val id: UUID,
    val sourceType: DisputeSourceType,
    val sourceId: UUID,
    val amountMinor: Long,
    val currency: String,
    val reason: String,
    val status: DisputeStatus,
    val evidenceDueBy: Instant?,
    val openedAt: Instant,
    val resolvedAt: Instant?,
)

fun PaymentDispute.toResponse() =
    DisputeResponse(
        id = id,
        sourceType = sourceType,
        sourceId = sourceId,
        amountMinor = amountMinor,
        currency = currency,
        reason = reason,
        status = status,
        evidenceDueBy = evidenceDueBy,
        openedAt = openedAt,
        resolvedAt = resolvedAt,
    )
