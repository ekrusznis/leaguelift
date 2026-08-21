package com.rally26.organization.web

import com.rally26.common.web.CurrentUser
import com.rally26.organization.application.OrganizationDeletionService
import com.rally26.organization.domain.OrganizationDeletionRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.UUID

data class OrganizationDeletionRequestResponse(
    val id: UUID,
    val status: String,
    val requestedAt: Instant,
    val scheduledFor: Instant,
)

fun OrganizationDeletionRequest.toResponse() = OrganizationDeletionRequestResponse(id, status.name, requestedAt, scheduledFor)

/** Owner-only "close this organization" — see [OrganizationDeletionService] for the 7-day grace period. */
@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/deletion-request")
class OrganizationDeletionController(
    private val organizationDeletionService: OrganizationDeletionService,
) {
    @PostMapping
    fun request(
        @PathVariable organizationId: UUID,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<OrganizationDeletionRequestResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(organizationDeletionService.request(organizationId, currentUser).toResponse())

    @GetMapping
    fun pending(
        @PathVariable organizationId: UUID,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): OrganizationDeletionRequestResponse? = organizationDeletionService.findPending(organizationId, currentUser)?.toResponse()

    @DeleteMapping
    fun cancel(
        @PathVariable organizationId: UUID,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Void> {
        organizationDeletionService.cancel(organizationId, currentUser)
        return ResponseEntity.noContent().build()
    }
}
