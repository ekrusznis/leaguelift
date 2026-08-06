package com.rally26.credit.web

import com.rally26.credit.domain.CreditExpirationPolicy
import com.rally26.credit.domain.OrganizationCreditSettings
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull

data class OrganizationCreditSettingsResponse(
    val defaultCreditPercent: Int,
    val expirationPolicy: String,
    val expirationMonths: Int?,
    val p2pTransferEnabled: Boolean,
)

fun OrganizationCreditSettings.toResponse() =
    OrganizationCreditSettingsResponse(defaultCreditPercent, expirationPolicy.name, expirationMonths, p2pTransferEnabled)

data class UpdateOrganizationCreditSettingsRequest(
    @field:NotNull @field:Min(0) @field:Max(10000) val defaultCreditPercent: Int,
    @field:NotNull val expirationPolicy: CreditExpirationPolicy,
    @field:Min(1) val expirationMonths: Int? = null,
    @field:NotNull val p2pTransferEnabled: Boolean,
)
