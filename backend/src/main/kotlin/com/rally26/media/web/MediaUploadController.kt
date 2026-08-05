package com.rally26.media.web

import com.rally26.common.web.CurrentUser
import com.rally26.media.application.MediaUploadService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/media")
class MediaUploadController(
    private val mediaUploadService: MediaUploadService,
) {
    @PostMapping("/uploads")
    fun requestUpload(
        @PathVariable organizationId: UUID,
        @Valid @RequestBody request: RequestUploadRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<RequestUploadResponse> {
        val result =
            mediaUploadService.requestUpload(
                organizationId,
                parseUsageSlot(request.usageSlot),
                request.fileName,
                request.contentType,
                request.fileSizeBytes,
                currentUser,
                request.entityType?.let(::parseEntityType),
                request.entityId,
            )
        return ResponseEntity.status(HttpStatus.CREATED).body(result.toResponse())
    }

    @PostMapping("/uploads/{assetId}/confirm")
    fun confirmUpload(
        @PathVariable organizationId: UUID,
        @PathVariable assetId: UUID,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ConfirmUploadResponse = mediaUploadService.confirmUpload(organizationId, assetId, currentUser).asset.toConfirmResponse()
}
