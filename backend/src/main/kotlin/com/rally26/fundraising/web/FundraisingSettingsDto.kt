package com.rally26.fundraising.web

import com.rally26.fundraising.domain.FundraisingSettings
import java.time.Instant
import java.util.UUID

data class UpdateFundraisingSettingsRequest(
    val requireOwnerApproval: Boolean,
)

data class FundraisingSettingsResponse(
    val organizationId: UUID,
    val requireOwnerApproval: Boolean,
    val updatedByUserId: UUID?,
    val updatedAt: Instant?,
)

fun FundraisingSettings.toResponse() =
    FundraisingSettingsResponse(
        organizationId = organizationId,
        requireOwnerApproval = requireOwnerApproval,
        updatedByUserId = updatedByUserId,
        updatedAt = updatedAt,
    )
