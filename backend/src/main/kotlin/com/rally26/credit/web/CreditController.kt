package com.rally26.credit.web

import com.rally26.common.web.CurrentUser
import com.rally26.credit.application.FamilyCreditService
import com.rally26.credit.application.HouseholdAttributionService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/** Phase 23 (DESIGN-DOC.md section 13/14.1): guardian-facing family credit balance/application/transfer and campaign attribution links. */
@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/households/{householdId}")
class CreditController(
    private val familyCreditService: FamilyCreditService,
    private val householdAttributionService: HouseholdAttributionService,
) {
    @GetMapping("/credits/balance")
    fun getBalance(
        @PathVariable organizationId: UUID,
        @PathVariable householdId: UUID,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): FamilyCreditBalanceResponse = familyCreditService.getBalance(organizationId, householdId, currentUser).toResponse()

    @GetMapping("/credits/grants")
    fun listGrants(
        @PathVariable organizationId: UUID,
        @PathVariable householdId: UUID,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): List<FamilyCreditGrantResponse> = familyCreditService.listGrants(organizationId, householdId, currentUser).map { it.toResponse() }

    @PostMapping("/credits/apply")
    fun applyToFee(
        @PathVariable organizationId: UUID,
        @PathVariable householdId: UUID,
        @Valid @RequestBody request: ApplyFamilyCreditRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Void> {
        familyCreditService.applyToFee(organizationId, householdId, request.feeAssignmentId, request.amountMinor, currentUser)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/credits/transfer")
    fun transfer(
        @PathVariable organizationId: UUID,
        @PathVariable householdId: UUID,
        @Valid @RequestBody request: TransferFamilyCreditRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<FamilyCreditTransferResponse> {
        val transfer = familyCreditService.transfer(organizationId, householdId, request.toHouseholdId, request.amountMinor, currentUser)
        return ResponseEntity.status(HttpStatus.CREATED).body(transfer.toResponse())
    }

    @GetMapping("/campaigns/{campaignId}/attribution-link")
    fun getOrCreateAttributionLink(
        @PathVariable organizationId: UUID,
        @PathVariable householdId: UUID,
        @PathVariable campaignId: UUID,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): AttributionLinkResponse =
        householdAttributionService.getOrCreateLink(organizationId, householdId, campaignId, currentUser).toResponse()

    @PatchMapping("/campaigns/{campaignId}/attribution-link")
    fun setPublicDisplayName(
        @PathVariable organizationId: UUID,
        @PathVariable householdId: UUID,
        @PathVariable campaignId: UUID,
        @Valid @RequestBody request: SetPublicDisplayNameRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): AttributionLinkResponse =
        householdAttributionService
            .setPublicDisplayName(organizationId, householdId, campaignId, request.publicDisplayName, currentUser)
            .toResponse()
}
