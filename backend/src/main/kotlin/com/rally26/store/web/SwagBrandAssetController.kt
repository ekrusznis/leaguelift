package com.rally26.store.web

import com.rally26.common.web.CurrentUser
import com.rally26.store.application.SwagBrandAssetService
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
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/swag-brand-assets")
class SwagBrandAssetController(
    private val service: SwagBrandAssetService,
) {
    @GetMapping
    fun list(
        @PathVariable organizationId: UUID,
        @RequestParam(required = false) teamId: UUID?,
        @RequestParam(defaultValue = "false") includeArchived: Boolean,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): List<SwagBrandAssetResponse> = service.list(organizationId, teamId, includeArchived, currentUser).map { it.toResponse() }

    @PostMapping
    fun create(
        @PathVariable organizationId: UUID,
        @Valid @RequestBody request: CreateSwagBrandAssetRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<SwagBrandAssetResponse> =
        ResponseEntity
            .status(HttpStatus.CREATED)
            .body(
                service
                    .create(
                        organizationId,
                        request.teamId,
                        request.mediaAssetId,
                        request.name,
                        request.category,
                        currentUser,
                    ).toResponse(),
            )

    @PatchMapping("/{assetId}/archive")
    fun archive(
        @PathVariable organizationId: UUID,
        @PathVariable assetId: UUID,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): SwagBrandAssetResponse = service.archive(organizationId, assetId, currentUser).toResponse()

    @PatchMapping("/{assetId}/restore")
    fun restore(
        @PathVariable organizationId: UUID,
        @PathVariable assetId: UUID,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): SwagBrandAssetResponse = service.restore(organizationId, assetId, currentUser).toResponse()
}
