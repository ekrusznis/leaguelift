package com.rally26.membership.web

import com.rally26.common.web.CurrentUser
import com.rally26.common.web.PageResponse
import com.rally26.identity.persistence.AppUserRepository
import com.rally26.membership.application.MembershipService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/members")
class MembershipController(
    private val membershipService: MembershipService,
    private val appUserRepository: AppUserRepository,
) {
    @GetMapping
    fun list(
        @PathVariable organizationId: UUID,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): PageResponse<MembershipResponse> {
        membershipService.requireActiveMembership(organizationId, currentUser)
        val offset = page * size
        // N+1 user lookups are acceptable at this page size (default 20, admin-facing
        // list) — no batch-by-ids query exists on AppUserRepository yet and adding one
        // for a single admin-facing list isn't worth the extra surface today.
        val items =
            membershipService.listMembers(organizationId, offset, size).map { member ->
                val user = appUserRepository.findById(member.userId)
                member.toResponse(userEmail = user?.email, userDisplayName = user?.displayName)
            }
        val total = membershipService.countMembers(organizationId)
        return PageResponse(items, page, size, total)
    }

    @PatchMapping("/{memberId}")
    fun updateRole(
        @PathVariable organizationId: UUID,
        @PathVariable memberId: UUID,
        @Valid @RequestBody request: UpdateMembershipRoleRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Void> {
        membershipService.updateRole(organizationId, memberId, request.role, currentUser)
        return ResponseEntity.noContent().build()
    }

    @DeleteMapping("/{memberId}")
    fun revoke(
        @PathVariable organizationId: UUID,
        @PathVariable memberId: UUID,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Void> {
        membershipService.revoke(organizationId, memberId, currentUser)
        return ResponseEntity.noContent().build()
    }
}
