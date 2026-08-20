package com.rally26.foundingorg.web

import com.rally26.foundingorg.application.FoundingCodeValidation
import com.rally26.foundingorg.domain.FoundingOrgPromoCode
import com.rally26.foundingorg.domain.FoundingPilotStatus
import java.time.Instant
import java.util.UUID

data class FoundingCodeValidationResponse(
    val valid: Boolean,
    val reason: String?,
)

fun FoundingCodeValidation.toResponse() = FoundingCodeValidationResponse(valid, reason)

data class FoundingOrgPromoCodeResponse(
    val id: UUID,
    val code: String,
    val organizationId: UUID?,
    val redeemedAt: Instant?,
    val pilotEndsAt: Instant?,
    val pilotStatus: FoundingPilotStatus,
)

fun FoundingOrgPromoCode.toResponse() =
    FoundingOrgPromoCodeResponse(
        id = id,
        code = code,
        organizationId = organizationId,
        redeemedAt = redeemedAt,
        pilotEndsAt = pilotEndsAt,
        pilotStatus = pilotStatus,
    )
