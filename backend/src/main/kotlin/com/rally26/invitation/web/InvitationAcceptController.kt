package com.rally26.invitation.web

import com.rally26.common.web.CurrentUser
import com.rally26.invitation.application.InvitationService
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Not nested under /organizations/{organizationId} — the caller doesn't have
 * membership in the target organization yet, that's the whole point of accepting.
 * The token itself resolves which organization/role this grants.
 */
@RestController
@RequestMapping("/api/v1/invitations")
class InvitationAcceptController(
    private val invitationService: InvitationService,
) {
    @PostMapping("/{token}/accept")
    fun accept(
        @PathVariable token: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): InvitationResponse = invitationService.accept(token, currentUser).toResponse()
}
