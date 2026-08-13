package com.rally26.media.web

import com.rally26.common.web.CurrentUser
import com.rally26.media.application.HouseholdMediaService
import com.rally26.media.application.MediaReadService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * Household media center (Track 5, 2026-08-13). Uploading the underlying file goes
 * through the existing generic `POST /organizations/{organizationId}/media/uploads`
 * (+ `/confirm`) with `usageSlot: "HOUSEHOLD_MEDIA"`, `entityType: "HOUSEHOLD"`,
 * `entityId: <householdId>` — this controller only covers listing, adding an
 * already-uploaded asset to the household's gallery, removing one, and the bulk
 * "release publicly" action.
 */
@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/households/{householdId}/media")
class HouseholdMediaController(
    private val householdMediaService: HouseholdMediaService,
    private val mediaReadService: MediaReadService,
) {
    @GetMapping
    fun list(
        @PathVariable organizationId: UUID,
        @PathVariable householdId: UUID,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): HouseholdMediaListResponse {
        val assignments = householdMediaService.list(organizationId, householdId, currentUser)
        return HouseholdMediaListResponse(mediaReadService.describeAll(assignments).map { it.toHouseholdMediaResponse() })
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun assign(
        @PathVariable organizationId: UUID,
        @PathVariable householdId: UUID,
        @Valid @RequestBody request: AssignHouseholdMediaRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): HouseholdMediaResponse {
        val assignment = householdMediaService.assign(organizationId, householdId, request.assetId, currentUser)
        val descriptor = mediaReadService.describe(assignment) ?: error("Newly assigned household media ${assignment.id} must exist.")
        return descriptor.toHouseholdMediaResponse()
    }

    @DeleteMapping("/{assignmentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun remove(
        @PathVariable organizationId: UUID,
        @PathVariable householdId: UUID,
        @PathVariable assignmentId: UUID,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ) = householdMediaService.remove(organizationId, householdId, assignmentId, currentUser)

    @PostMapping("/release-publicly")
    fun releasePublicly(
        @PathVariable organizationId: UUID,
        @PathVariable householdId: UUID,
        @Valid @RequestBody request: ReleaseHouseholdMediaPubliclyRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): HouseholdMediaListResponse {
        val assignments = householdMediaService.releasePublicly(organizationId, householdId, request.assignmentIds, currentUser)
        return HouseholdMediaListResponse(mediaReadService.describeAll(assignments).map { it.toHouseholdMediaResponse() })
    }
}
