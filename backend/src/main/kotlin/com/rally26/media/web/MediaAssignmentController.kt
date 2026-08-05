package com.rally26.media.web

import com.rally26.common.web.CurrentUser
import com.rally26.media.application.MediaAssignmentService
import com.rally26.media.application.MediaReadService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/media/assignments")
class MediaAssignmentController(
    private val mediaAssignmentService: MediaAssignmentService,
    private val mediaReadService: MediaReadService,
) {
    @GetMapping
    fun listActive(
        @PathVariable organizationId: UUID,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): MediaAssignmentListResponse {
        val assignments = mediaAssignmentService.listActiveOrganizationMedia(organizationId, currentUser)
        return MediaAssignmentListResponse(mediaReadService.describeAll(assignments).map { it.toResponse() })
    }

    @PutMapping("/{usageSlot}")
    fun assign(
        @PathVariable organizationId: UUID,
        @PathVariable usageSlot: String,
        @Valid @RequestBody request: AssignMediaRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): MediaAssignmentResponse {
        val assignment =
            mediaAssignmentService.assignOrganizationMedia(
                organizationId,
                parseUsageSlot(usageSlot),
                request.assetId,
                request.altText,
                currentUser,
            )
        val descriptor =
            mediaReadService.describe(assignment)
                ?: error("Newly assigned media asset ${assignment.assetId} must exist.")
        return descriptor.toResponse()
    }

    @DeleteMapping("/{usageSlot}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun remove(
        @PathVariable organizationId: UUID,
        @PathVariable usageSlot: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ) = mediaAssignmentService.removeOrganizationMedia(organizationId, parseUsageSlot(usageSlot), currentUser)

    @GetMapping("/entities/{entityType}/{entityId}")
    fun listEntityMedia(
        @PathVariable organizationId: UUID,
        @PathVariable entityType: String,
        @PathVariable entityId: UUID,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): MediaAssignmentListResponse {
        val assignments =
            mediaAssignmentService.listEntityMedia(
                organizationId,
                parseEntityType(entityType),
                entityId,
                currentUser,
            )
        return MediaAssignmentListResponse(mediaReadService.describeAll(assignments).map { it.toResponse() })
    }

    @PutMapping("/entities/{entityType}/{entityId}/{usageSlot}")
    fun assignEntityMedia(
        @PathVariable organizationId: UUID,
        @PathVariable entityType: String,
        @PathVariable entityId: UUID,
        @PathVariable usageSlot: String,
        @Valid @RequestBody request: AssignMediaRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): MediaAssignmentResponse {
        val assignment =
            mediaAssignmentService.assignEntityMedia(
                organizationId,
                parseEntityType(entityType),
                entityId,
                parseUsageSlot(usageSlot),
                request.assetId,
                request.altText,
                currentUser,
            )
        val descriptor =
            mediaReadService.describe(assignment)
                ?: error("Newly assigned media asset ${assignment.assetId} must exist.")
        return descriptor.toResponse()
    }

    @DeleteMapping("/entities/{entityType}/{entityId}/{usageSlot}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun removeEntityMedia(
        @PathVariable organizationId: UUID,
        @PathVariable entityType: String,
        @PathVariable entityId: UUID,
        @PathVariable usageSlot: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ) = mediaAssignmentService.removeEntityMedia(
        organizationId,
        parseEntityType(entityType),
        entityId,
        parseUsageSlot(usageSlot),
        currentUser,
    )
}
