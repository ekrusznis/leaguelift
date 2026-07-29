package com.leaguelift.sponsorship.web

import com.leaguelift.common.web.CurrentUser
import com.leaguelift.common.web.PageResponse
import com.leaguelift.media.application.MediaReadService
import com.leaguelift.media.web.MediaAssignmentResponse
import com.leaguelift.media.web.toResponse
import com.leaguelift.sponsorship.application.SponsorshipPackageService
import com.leaguelift.sponsorship.application.SponsorshipService
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

/**
 * Org-admin sponsorship-package CRUD/publish + confirmed-sponsorship listing + sponsor
 * logo assignment. Grouped under one base path (`/organizations/{organizationId}`)
 * spanning two resource families (`sponsorship-packages`, `sponsors`) the same way
 * `store/web/ProductController.kt` groups product + design-assignment endpoints.
 */
@RestController
@RequestMapping("/api/v1/organizations/{organizationId}")
class SponsorshipPackageController(
	private val sponsorshipPackageService: SponsorshipPackageService,
	private val sponsorshipService: SponsorshipService,
	private val mediaReadService: MediaReadService,
) {

	@GetMapping("/sponsorship-packages")
	fun list(
		@PathVariable organizationId: UUID,
		@RequestParam(defaultValue = "0") page: Int,
		@RequestParam(defaultValue = "20") size: Int,
		@AuthenticationPrincipal currentUser: CurrentUser,
	): PageResponse<SponsorshipPackageResponse> {
		val offset = page * size
		val items = sponsorshipPackageService.list(organizationId, currentUser, offset, size)
			.map { it.toResponse(sponsorshipService.getConfirmedCount(it.id)) }
		val total = sponsorshipPackageService.count(organizationId, currentUser)
		return PageResponse(items, page, size, total)
	}

	@PostMapping("/sponsorship-packages")
	fun create(
		@PathVariable organizationId: UUID,
		@Valid @RequestBody request: CreateSponsorshipPackageRequest,
		@AuthenticationPrincipal currentUser: CurrentUser,
	): ResponseEntity<SponsorshipPackageResponse> {
		val sponsorshipPackage = sponsorshipPackageService.create(
			organizationId, request.name, request.description, request.priceMinor, request.currency,
			request.maxQuantity, request.exclusive, request.placementStartDate, request.placementEndDate, currentUser,
		)
		return ResponseEntity.status(HttpStatus.CREATED).body(sponsorshipPackage.toResponse(confirmedCount = 0))
	}

	@GetMapping("/sponsorship-packages/{packageId}")
	fun get(
		@PathVariable organizationId: UUID,
		@PathVariable packageId: UUID,
		@AuthenticationPrincipal currentUser: CurrentUser,
	): SponsorshipPackageResponse {
		val sponsorshipPackage = sponsorshipPackageService.get(organizationId, packageId, currentUser)
		return sponsorshipPackage.toResponse(sponsorshipService.getConfirmedCount(sponsorshipPackage.id))
	}

	@PatchMapping("/sponsorship-packages/{packageId}")
	fun update(
		@PathVariable organizationId: UUID,
		@PathVariable packageId: UUID,
		@Valid @RequestBody request: UpdateSponsorshipPackageRequest,
		@AuthenticationPrincipal currentUser: CurrentUser,
	): SponsorshipPackageResponse {
		val sponsorshipPackage = sponsorshipPackageService.update(
			organizationId, packageId, request.name, request.description, request.priceMinor,
			request.maxQuantity, request.exclusive, request.placementStartDate, request.placementEndDate, currentUser,
		)
		return sponsorshipPackage.toResponse(sponsorshipService.getConfirmedCount(sponsorshipPackage.id))
	}

	@PostMapping("/sponsorship-packages/{packageId}/publish")
	fun publish(
		@PathVariable organizationId: UUID,
		@PathVariable packageId: UUID,
		@AuthenticationPrincipal currentUser: CurrentUser,
	): SponsorshipPackageResponse {
		val sponsorshipPackage = sponsorshipPackageService.publish(organizationId, packageId, currentUser)
		return sponsorshipPackage.toResponse(sponsorshipService.getConfirmedCount(sponsorshipPackage.id))
	}

	@PatchMapping("/sponsorship-packages/{packageId}/status")
	fun updateStatus(
		@PathVariable organizationId: UUID,
		@PathVariable packageId: UUID,
		@Valid @RequestBody request: UpdateSponsorshipPackageStatusRequest,
		@AuthenticationPrincipal currentUser: CurrentUser,
	): SponsorshipPackageResponse {
		val sponsorshipPackage = sponsorshipPackageService.updateStatus(organizationId, packageId, request.status, currentUser)
		return sponsorshipPackage.toResponse(sponsorshipService.getConfirmedCount(sponsorshipPackage.id))
	}

	@GetMapping("/sponsorship-packages/{packageId}/sponsorships")
	fun listSponsorships(
		@PathVariable organizationId: UUID,
		@PathVariable packageId: UUID,
		@RequestParam(defaultValue = "0") page: Int,
		@RequestParam(defaultValue = "20") size: Int,
		@AuthenticationPrincipal currentUser: CurrentUser,
	): PageResponse<SponsorshipResponse> {
		val offset = page * size
		val items = sponsorshipService.listConfirmed(organizationId, packageId, currentUser, offset, size).map { it.toResponse() }
		val total = sponsorshipService.getConfirmedCount(packageId)
		return PageResponse(items, page, size, total)
	}

	@PutMapping("/sponsors/{sponsorId}/logo")
	fun assignSponsorLogo(
		@PathVariable organizationId: UUID,
		@PathVariable sponsorId: UUID,
		@Valid @RequestBody request: AssignSponsorLogoRequest,
		@AuthenticationPrincipal currentUser: CurrentUser,
	): MediaAssignmentResponse {
		val assignment = sponsorshipPackageService.assignSponsorLogo(organizationId, sponsorId, request.assetId, request.altText, currentUser)
		val descriptor = mediaReadService.describe(assignment)
			?: error("Newly assigned sponsor logo asset ${assignment.assetId} must exist.")
		return descriptor.toResponse()
	}
}
