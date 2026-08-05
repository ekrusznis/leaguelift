package com.rally26.offlinefinance.web

import com.rally26.common.web.CurrentUser
import com.rally26.common.web.PageResponse
import com.rally26.offlinefinance.application.OfflineFinancialRecordService
import com.rally26.offlinefinance.domain.OfflineFinancialRecordType
import com.rally26.offlinefinance.domain.OfflineVerificationStatus
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/offline-financial-records")
class OfflineFinancialRecordController(
    private val service: OfflineFinancialRecordService,
) {
    @GetMapping
    fun list(
        @PathVariable organizationId: UUID,
        @RequestParam(required = false) verificationStatus: OfflineVerificationStatus?,
        @RequestParam(required = false) recordType: OfflineFinancialRecordType?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "25") size: Int,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): PageResponse<OfflineFinancialRecordResponse> {
        val safePage = page.coerceAtLeast(0)
        val safeSize = size.coerceIn(1, 100)
        val items =
            service
                .list(
                    organizationId,
                    verificationStatus,
                    recordType,
                    safePage * safeSize,
                    safeSize,
                    currentUser,
                ).map { it.toResponse() }
        val total = service.count(organizationId, verificationStatus, recordType, currentUser)
        return PageResponse(items, safePage, safeSize, total)
    }

    @GetMapping("/{recordId}")
    fun get(
        @PathVariable organizationId: UUID,
        @PathVariable recordId: UUID,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): OfflineFinancialRecordResponse = service.get(organizationId, recordId, currentUser).toResponse()

    @PostMapping("/contributions")
    fun createContribution(
        @PathVariable organizationId: UUID,
        @Valid @RequestBody request: CreateOfflineContributionRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<OfflineFinancialRecordResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(
            service
                .createContribution(
                    organizationId = organizationId,
                    campaignId = request.campaignId,
                    amountMinor = request.amountMinor,
                    supporterName = request.supporterName,
                    isAnonymous = request.isAnonymous,
                    supporterEmail = request.supporterEmail,
                    paymentMethod = request.paymentMethod,
                    paymentReference = request.paymentReference,
                    receivedAt = request.receivedAt,
                    internalNotes = request.internalNotes,
                    idempotencyKey = request.idempotencyKey,
                    markVerified = request.markVerified,
                    sendAcknowledgement = request.sendAcknowledgement,
                    currentUser = currentUser,
                ).toResponse(),
        )

    @PostMapping("/sponsorships")
    fun createSponsorship(
        @PathVariable organizationId: UUID,
        @Valid @RequestBody request: CreateOfflineSponsorshipRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<OfflineFinancialRecordResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(
            service
                .createSponsorship(
                    organizationId = organizationId,
                    packageId = request.packageId,
                    sponsorName = request.sponsorName,
                    sponsorContactEmail = request.sponsorContactEmail,
                    sponsorPhone = request.sponsorPhone,
                    sponsorCompanyName = request.sponsorCompanyName,
                    paymentMethod = request.paymentMethod,
                    paymentReference = request.paymentReference,
                    receivedAt = request.receivedAt,
                    internalNotes = request.internalNotes,
                    idempotencyKey = request.idempotencyKey,
                    markVerified = request.markVerified,
                    sendAcknowledgement = request.sendAcknowledgement,
                    currentUser = currentUser,
                ).toResponse(),
        )

    @PostMapping("/orders")
    fun createOrder(
        @PathVariable organizationId: UUID,
        @Valid @RequestBody request: CreateOfflineOrderRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<OfflineFinancialRecordResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(
            service
                .createOrder(
                    organizationId = organizationId,
                    storeId = request.storeId,
                    items = request.items.map { it.toDomain() },
                    supporterName = request.supporterName,
                    supporterEmail = request.supporterEmail,
                    shippingAddress = request.shippingAddress?.toDomain(),
                    paymentMethod = request.paymentMethod,
                    paymentReference = request.paymentReference,
                    receivedAt = request.receivedAt,
                    internalNotes = request.internalNotes,
                    idempotencyKey = request.idempotencyKey,
                    markVerified = request.markVerified,
                    sendAcknowledgement = request.sendAcknowledgement,
                    currentUser = currentUser,
                ).toResponse(),
        )

    @PostMapping("/{recordId}/verify")
    fun verify(
        @PathVariable organizationId: UUID,
        @PathVariable recordId: UUID,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): OfflineFinancialRecordResponse = service.verify(organizationId, recordId, currentUser).toResponse()
}
