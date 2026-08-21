package com.rally26.invitation.web

import com.rally26.common.web.CurrentUser
import com.rally26.invitation.application.HouseholdInvitationService
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** Not nested under /organizations/{organizationId} — see InvitationAcceptController's identical note; the token itself resolves which household/participant this grants. */
@RestController
@RequestMapping("/api/v1/household-invitations")
class HouseholdInvitationAcceptController(
    private val householdInvitationService: HouseholdInvitationService,
) {
    @PostMapping("/{token}/accept")
    fun accept(
        @PathVariable token: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): HouseholdInvitationResponse = householdInvitationService.accept(token, currentUser).toResponse()
}
