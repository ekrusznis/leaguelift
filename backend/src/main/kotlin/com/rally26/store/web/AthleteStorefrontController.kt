package com.rally26.store.web

import com.rally26.common.web.CurrentUser
import com.rally26.common.web.PageResponse
import com.rally26.store.application.AthleteStorefrontService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/** Phase 24 slice 24.3 (DESIGN-DOC.md section 14.1G): org-owner/coach management of public per-athlete storefronts. */
@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/athlete-storefronts")
class AthleteStorefrontController(
    private val service: AthleteStorefrontService,
) {
    @GetMapping
    fun list(
        @PathVariable organizationId: UUID,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): PageResponse<AthleteStorefrontResponse> {
        val offset = page * size
        val items = service.list(organizationId, currentUser, offset, size).map { it.toResponse() }
        val total = service.count(organizationId, currentUser)
        return PageResponse(items, page, size, total)
    }

    @GetMapping("/{storefrontId}")
    fun get(
        @PathVariable organizationId: UUID,
        @PathVariable storefrontId: UUID,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): AthleteStorefrontResponse = service.get(organizationId, storefrontId, currentUser).toResponse()

    @GetMapping("/{storefrontId}/products")
    fun listProductIds(
        @PathVariable organizationId: UUID,
        @PathVariable storefrontId: UUID,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): List<UUID> = service.listProductIds(organizationId, storefrontId, currentUser)

    @PostMapping
    fun create(
        @PathVariable organizationId: UUID,
        @Valid @RequestBody request: CreateAthleteStorefrontRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<AthleteStorefrontResponse> =
        ResponseEntity
            .status(HttpStatus.CREATED)
            .body(
                service
                    .create(
                        organizationId,
                        request.teamId,
                        request.participantId,
                        request.storeId,
                        request.productIds,
                        request.slug,
                        currentUser,
                    ).toResponse(),
            )

    @PutMapping("/{storefrontId}/products")
    fun updateProducts(
        @PathVariable organizationId: UUID,
        @PathVariable storefrontId: UUID,
        @Valid @RequestBody request: UpdateAthleteStorefrontProductsRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): AthleteStorefrontResponse = service.updateProducts(organizationId, storefrontId, request.productIds, currentUser).toResponse()

    @PatchMapping("/{storefrontId}/publish")
    fun publish(
        @PathVariable organizationId: UUID,
        @PathVariable storefrontId: UUID,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): AthleteStorefrontResponse = service.publish(organizationId, storefrontId, currentUser).toResponse()

    @PatchMapping("/{storefrontId}/unpublish")
    fun unpublish(
        @PathVariable organizationId: UUID,
        @PathVariable storefrontId: UUID,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): AthleteStorefrontResponse = service.unpublish(organizationId, storefrontId, currentUser).toResponse()

    @PatchMapping("/{storefrontId}/archive")
    fun archive(
        @PathVariable organizationId: UUID,
        @PathVariable storefrontId: UUID,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): AthleteStorefrontResponse = service.archive(organizationId, storefrontId, currentUser).toResponse()

    @PostMapping("/share-link-qr")
    fun buildShareLinkQr(
        @PathVariable organizationId: UUID,
        @Valid @RequestBody request: BuildAthleteStorefrontShareLinkRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ShareLinkQrResponse = ShareLinkQrResponse(service.buildShareLink(organizationId, request.url, currentUser))
}
