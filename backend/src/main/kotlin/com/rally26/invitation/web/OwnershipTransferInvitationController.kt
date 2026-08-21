package com.rally26.invitation.web

import com.rally26.common.web.CurrentUser
import com.rally26.invitation.application.OwnershipTransferInvitationService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * Deliberately does not return the raw invitation token in any response body — unlike
 * [HouseholdInvitationController]'s `rawToken` (mitigated there by the email-match check
 * on accept, and low-stakes), handing over organization ownership is high-stakes enough
 * that the token should only ever leave the server via the invitee's own email.
 */
@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/ownership-transfer-invitations")
class OwnershipTransferInvitationController(
    private val ownershipTransferInvitationService: OwnershipTransferInvitationService,
) {
    @PostMapping
    fun invite(
        @PathVariable organizationId: UUID,
        @Valid @RequestBody request: InviteOwnershipTransferRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<OwnershipTransferInvitationResponse> {
        val created = ownershipTransferInvitationService.invite(organizationId, request.email, currentUser)
        return ResponseEntity.status(HttpStatus.CREATED).body(created.invitation.toResponse())
    }

    @GetMapping("/pending")
    fun pending(
        @PathVariable organizationId: UUID,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): OwnershipTransferInvitationResponse? =
        ownershipTransferInvitationService.findPendingForOrganization(organizationId, currentUser)?.toResponse()

    @DeleteMapping("/{invitationId}")
    fun revoke(
        @PathVariable organizationId: UUID,
        @PathVariable invitationId: UUID,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Void> {
        ownershipTransferInvitationService.revoke(organizationId, invitationId, currentUser)
        return ResponseEntity.noContent().build()
    }
}
