package com.rally26.invitation.web

import com.rally26.common.web.CurrentUser
import com.rally26.invitation.application.HouseholdInvitationService
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

@RestController
@RequestMapping("/api/v1/organizations/{organizationId}")
class HouseholdInvitationController(
    private val householdInvitationService: HouseholdInvitationService,
) {
    @PostMapping("/participants/{participantId}/guardian-invitations")
    fun inviteGuardian(
        @PathVariable organizationId: UUID,
        @PathVariable participantId: UUID,
        @Valid @RequestBody request: InviteGuardianRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<CreateHouseholdInvitationResponse> {
        val created =
            householdInvitationService.inviteGuardian(
                organizationId,
                participantId,
                request.firstName,
                request.lastName,
                request.email,
                request.relationship,
                currentUser,
            )
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(CreateHouseholdInvitationResponse(created.invitation.toResponse(), created.rawToken))
    }

    @PostMapping("/participants/{participantId}/athlete-invitations")
    fun inviteAthlete(
        @PathVariable organizationId: UUID,
        @PathVariable participantId: UUID,
        @Valid @RequestBody request: InviteAthleteRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<CreateHouseholdInvitationResponse> {
        val created = householdInvitationService.inviteAthlete(organizationId, participantId, request.email, currentUser)
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(CreateHouseholdInvitationResponse(created.invitation.toResponse(), created.rawToken))
    }

    @GetMapping("/households/{householdId}/invitations")
    fun listPending(
        @PathVariable organizationId: UUID,
        @PathVariable householdId: UUID,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): List<HouseholdInvitationResponse> =
        householdInvitationService.listPendingForHousehold(organizationId, householdId, currentUser).map { it.toResponse() }

    @DeleteMapping("/household-invitations/{invitationId}")
    fun revoke(
        @PathVariable organizationId: UUID,
        @PathVariable invitationId: UUID,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Void> {
        householdInvitationService.revoke(organizationId, invitationId, currentUser)
        return ResponseEntity.noContent().build()
    }
}
