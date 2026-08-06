package com.rally26.credit.web

import com.rally26.credit.domain.CampaignHouseholdAttributionLink
import com.rally26.credit.domain.FamilyCreditBalance
import com.rally26.credit.domain.FamilyCreditGrant
import com.rally26.credit.domain.FamilyCreditTransfer
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

data class FamilyCreditBalanceResponse(
    val currency: String,
    val availableMinor: Long,
    val pendingMinor: Long,
    val expiringSoonMinor: Long,
    val appliedAllTimeMinor: Long,
    val p2pTransferEnabled: Boolean,
)

fun FamilyCreditBalance.toResponse() =
    FamilyCreditBalanceResponse(currency, availableMinor, pendingMinor, expiringSoonMinor, appliedAllTimeMinor, p2pTransferEnabled)

data class FamilyCreditGrantResponse(
    val id: UUID,
    val amountMinor: Long,
    val remainingMinor: Long,
    val currency: String,
    val status: String,
    val sourceType: String,
    val grantedAt: Instant,
    val expiresAt: Instant?,
)

fun FamilyCreditGrant.toResponse() =
    FamilyCreditGrantResponse(id, amountMinor, remainingMinor, currency, status.name, sourceType.name, grantedAt, expiresAt)

data class ApplyFamilyCreditRequest(
    @field:NotNull val feeAssignmentId: UUID,
    @field:NotNull @field:Min(1) val amountMinor: Long,
)

data class TransferFamilyCreditRequest(
    @field:NotNull val toHouseholdId: UUID,
    @field:NotNull @field:Min(1) val amountMinor: Long,
)

data class FamilyCreditTransferResponse(
    val id: UUID,
    val fromHouseholdId: UUID,
    val toHouseholdId: UUID,
    val amountMinor: Long,
    val status: String,
    val reviewNote: String?,
    val createdAt: Instant,
    val reviewedAt: Instant?,
)

fun FamilyCreditTransfer.toResponse() =
    FamilyCreditTransferResponse(id, fromHouseholdId, toHouseholdId, amountMinor, status.name, reviewNote, createdAt, reviewedAt)

data class ReviewCreditTransferRequest(
    @field:Size(max = 500) val reviewNote: String? = null,
)

data class AttributionLinkResponse(
    val code: String,
    val publicDisplayName: String?,
)

fun CampaignHouseholdAttributionLink.toResponse() = AttributionLinkResponse(code, publicDisplayName)

data class SetPublicDisplayNameRequest(
    @field:Size(max = 120) val publicDisplayName: String?,
)
