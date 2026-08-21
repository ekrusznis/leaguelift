package com.rally26.invitation.web

import com.rally26.common.web.CurrentUser
import com.rally26.invitation.application.OwnershipTransferInvitationService
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** Not nested under /organizations/{organizationId} — the token itself resolves which organization this transfers. */
@RestController
@RequestMapping("/api/v1/ownership-transfer-invitations")
class OwnershipTransferInvitationAcceptController(
    private val ownershipTransferInvitationService: OwnershipTransferInvitationService,
) {
    @PostMapping("/{token}/accept")
    fun accept(
        @PathVariable token: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): OwnershipTransferInvitationResponse = ownershipTransferInvitationService.accept(token, currentUser).toResponse()
}
