package com.leaguelift.profilecorrection.web

import com.leaguelift.common.web.CurrentUser
import com.leaguelift.common.web.PageResponse
import com.leaguelift.profilecorrection.application.ProfileCorrectionService
import com.leaguelift.profilecorrection.domain.ProfileCorrectionStatus
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
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/organizations/{organizationId}")
class ProfileCorrectionController(private val service: ProfileCorrectionService) {

    @PostMapping("/profile-correction-requests")
    fun create(
        @PathVariable organizationId: UUID,
        @Valid @RequestBody request: CreateProfileCorrectionRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<ProfileCorrectionResponse> = ResponseEntity.status(HttpStatus.CREATED).body(
        service.create(
            organizationId = organizationId,
            targetType = request.targetType,
            targetId = request.targetId,
            field = request.field,
            proposedValue = request.proposedValue,
            reason = request.reason,
            currentUser = currentUser,
        ).toResponse(),
    )

    @GetMapping("/profile-correction-requests")
    fun listForOrganization(
        @PathVariable organizationId: UUID,
        @RequestParam(required = false) status: ProfileCorrectionStatus?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") size: Int,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): PageResponse<ProfileCorrectionResponse> {
        val safePage = page.coerceAtLeast(0)
        val safeSize = size.coerceIn(1, 100)
        val (items, total) = service.listForOrganization(
            organizationId, status, currentUser, safePage * safeSize, safeSize,
        )
        return PageResponse(items.map { it.toResponse() }, safePage, safeSize, total)
    }

    @GetMapping("/households/{householdId}/profile-correction-requests")
    fun listForHousehold(
        @PathVariable organizationId: UUID,
        @PathVariable householdId: UUID,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): List<ProfileCorrectionResponse> =
        service.listForHousehold(organizationId, householdId, currentUser).map { it.toResponse() }

    @PostMapping("/profile-correction-requests/{requestId}/approve")
    fun approve(
        @PathVariable organizationId: UUID,
        @PathVariable requestId: UUID,
        @Valid @RequestBody request: ReviewProfileCorrectionRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ProfileCorrectionResponse =
        service.approve(organizationId, requestId, request.reviewNote, currentUser).toResponse()

    @PostMapping("/profile-correction-requests/{requestId}/reject")
    fun reject(
        @PathVariable organizationId: UUID,
        @PathVariable requestId: UUID,
        @Valid @RequestBody request: RejectProfileCorrectionRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ProfileCorrectionResponse =
        service.reject(organizationId, requestId, request.reviewNote, currentUser).toResponse()

    @PostMapping("/profile-correction-requests/{requestId}/withdraw")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun withdraw(
        @PathVariable organizationId: UUID,
        @PathVariable requestId: UUID,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ) = service.withdraw(organizationId, requestId, currentUser)
}
