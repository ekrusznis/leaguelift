package com.leaguelift.membership.web

import com.leaguelift.common.web.CurrentUser
import com.leaguelift.common.web.PageResponse
import com.leaguelift.membership.application.MembershipService
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/members")
class MembershipController(private val membershipService: MembershipService) {

	@GetMapping
	fun list(
		@PathVariable organizationId: UUID,
		@RequestParam(defaultValue = "0") page: Int,
		@RequestParam(defaultValue = "20") size: Int,
		@AuthenticationPrincipal currentUser: CurrentUser,
	): PageResponse<MembershipResponse> {
		membershipService.requireActiveMembership(organizationId, currentUser)
		val offset = page * size
		val items = membershipService.listMembers(organizationId, offset, size).map { it.toResponse() }
		val total = membershipService.countMembers(organizationId)
		return PageResponse(items, page, size, total)
	}
}
