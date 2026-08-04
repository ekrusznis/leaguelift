package com.rally26.invitation.web

import com.rally26.common.web.CurrentUser
import com.rally26.common.web.PageResponse
import com.rally26.invitation.application.InvitationService
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
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/invitations")
class InvitationController(private val invitationService: InvitationService) {

	@PostMapping
	fun create(
		@PathVariable organizationId: UUID,
		@Valid @RequestBody request: CreateInvitationRequest,
		@AuthenticationPrincipal currentUser: CurrentUser,
	): ResponseEntity<CreateInvitationResponse> {
		val created = invitationService.invite(organizationId, request.email, request.role, currentUser)
		val body = CreateInvitationResponse(created.invitation.toResponse(), created.rawToken)
		return ResponseEntity.status(HttpStatus.CREATED).body(body)
	}

	@PostMapping("/{invitationId}/resend")
	fun resend(
		@PathVariable organizationId: UUID,
		@PathVariable invitationId: UUID,
		@AuthenticationPrincipal currentUser: CurrentUser,
	): ResponseEntity<CreateInvitationResponse> {
		val created = invitationService.resend(organizationId, invitationId, currentUser)
		return ResponseEntity.ok(CreateInvitationResponse(created.invitation.toResponse(), created.rawToken))
	}

	@GetMapping
	fun listPending(
		@PathVariable organizationId: UUID,
		@RequestParam(defaultValue = "0") page: Int,
		@RequestParam(defaultValue = "20") size: Int,
		@AuthenticationPrincipal currentUser: CurrentUser,
	): PageResponse<InvitationResponse> {
		val offset = page * size
		val items = invitationService.listPending(organizationId, currentUser, offset, size).map { it.toResponse() }
		val total = invitationService.countPending(organizationId, currentUser)
		return PageResponse(items, page, size, total)
	}

	@DeleteMapping("/{invitationId}")
	fun revoke(
		@PathVariable organizationId: UUID,
		@PathVariable invitationId: UUID,
		@AuthenticationPrincipal currentUser: CurrentUser,
	): ResponseEntity<Void> {
		invitationService.revoke(organizationId, invitationId, currentUser)
		return ResponseEntity.noContent().build()
	}
}
