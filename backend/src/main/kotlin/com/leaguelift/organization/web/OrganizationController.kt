package com.leaguelift.organization.web

import com.leaguelift.common.web.CurrentUser
import com.leaguelift.common.web.PageResponse
import com.leaguelift.organization.application.OrganizationService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/organizations")
class OrganizationController(private val organizationService: OrganizationService) {

	@GetMapping
	fun list(
		@RequestParam(defaultValue = "0") page: Int,
		@RequestParam(defaultValue = "20") size: Int,
		@AuthenticationPrincipal currentUser: CurrentUser,
	): PageResponse<OrganizationResponse> {
		val offset = page * size
		val items = organizationService.listForUser(currentUser, offset, size).map { it.toResponse() }
		val total = organizationService.countForUser(currentUser)
		return PageResponse(items, page, size, total)
	}

	@PostMapping
	fun create(
		@Valid @RequestBody request: CreateOrganizationRequest,
		@AuthenticationPrincipal currentUser: CurrentUser,
	): ResponseEntity<OrganizationResponse> {
		val created = organizationService.create(request.name, request.slug, request.organizationType, currentUser)
		return ResponseEntity.status(HttpStatus.CREATED).body(created.toResponse())
	}

	@GetMapping("/{organizationId}")
	fun get(
		@PathVariable organizationId: UUID,
		@AuthenticationPrincipal currentUser: CurrentUser,
	): OrganizationResponse = organizationService.get(organizationId, currentUser).toResponse()

	@PatchMapping("/{organizationId}")
	fun update(
		@PathVariable organizationId: UUID,
		@Valid @RequestBody request: UpdateOrganizationRequest,
		@AuthenticationPrincipal currentUser: CurrentUser,
	): OrganizationResponse =
		organizationService.update(
			organizationId,
			request.name,
			request.organizationType,
			request.sports,
			request.contactEmail,
			request.contactPhone,
			currentUser,
		).toResponse()

	@GetMapping("/{organizationId}/onboarding")
	fun onboarding(
		@PathVariable organizationId: UUID,
		@AuthenticationPrincipal currentUser: CurrentUser,
	): OnboardingProgressResponse = organizationService.onboardingProgress(organizationId, currentUser).toResponse()
}
