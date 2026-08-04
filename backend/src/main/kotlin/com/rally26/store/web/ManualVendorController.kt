package com.rally26.store.web

import com.rally26.common.web.CurrentUser
import com.rally26.store.application.ManualVendorService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/manual-vendors")
class ManualVendorController(private val service: ManualVendorService) {
	@GetMapping
	fun list(
		@PathVariable organizationId: UUID,
		@RequestParam(defaultValue = "false") includeArchived: Boolean,
		@AuthenticationPrincipal currentUser: CurrentUser,
	): List<ManualVendorResponse> = service.list(organizationId, includeArchived, currentUser).map { it.toResponse() }

	@GetMapping("/{vendorId}")
	fun get(
		@PathVariable organizationId: UUID,
		@PathVariable vendorId: UUID,
		@AuthenticationPrincipal currentUser: CurrentUser,
	): ManualVendorResponse = service.get(organizationId, vendorId, currentUser).toResponse()

	@PostMapping
	fun create(
		@PathVariable organizationId: UUID,
		@Valid @RequestBody request: ManualVendorMutationRequest,
		@AuthenticationPrincipal currentUser: CurrentUser,
	): ResponseEntity<ManualVendorResponse> = ResponseEntity.status(HttpStatus.CREATED).body(
		service.create(
			organizationId, request.name, request.contactName, request.contactEmail, request.phone,
			request.websiteUrl, request.notes, currentUser,
		).toResponse(),
	)

	@PutMapping("/{vendorId}")
	fun update(
		@PathVariable organizationId: UUID,
		@PathVariable vendorId: UUID,
		@Valid @RequestBody request: ManualVendorMutationRequest,
		@AuthenticationPrincipal currentUser: CurrentUser,
	): ManualVendorResponse = service.update(
		organizationId, vendorId, request.name, request.contactName, request.contactEmail,
		request.phone, request.websiteUrl, request.notes, currentUser,
	).toResponse()

	@PatchMapping("/{vendorId}/archive")
	fun archive(
		@PathVariable organizationId: UUID,
		@PathVariable vendorId: UUID,
		@AuthenticationPrincipal currentUser: CurrentUser,
	): ManualVendorResponse = service.archive(organizationId, vendorId, currentUser).toResponse()
}
