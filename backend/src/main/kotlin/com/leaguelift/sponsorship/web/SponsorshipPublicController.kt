package com.leaguelift.sponsorship.web

import com.leaguelift.sponsorship.application.SponsorshipPackageService
import com.leaguelift.sponsorship.application.SponsorshipService
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * Public, unauthenticated — a visitor browsing/purchasing sponsorships is never a
 * LeagueLift user (mirrors `CampaignPublicController`/`OrderPublicController`).
 * Confirmation is authoritative only via the Stripe webhook
 * (`webhook/web/StripeWebhookController.kt`); [getStatus] is a read-only poll for the
 * browser's return page, nothing here flips status. Package listing and the sponsor
 * directory are scoped by the organization's own public slug; checkout/status-poll are
 * scoped by `packageId` directly (a visitor arrives at checkout already holding the
 * package id from the listing call).
 */
@RestController
@RequestMapping("/api/v1/public")
class SponsorshipPublicController(
	private val sponsorshipPackageService: SponsorshipPackageService,
	private val sponsorshipService: SponsorshipService,
) {

	@GetMapping("/organizations/{slug}/sponsorship-packages")
	fun listPackages(@PathVariable slug: String): List<PublicSponsorshipPackageResponse> =
		sponsorshipPackageService.listPublic(slug).map { it.toPublicResponse(sponsorshipService.getConfirmedCount(it.id)) }

	/** The public sponsor directory (DESIGN-DOC.md section 14.1 scope) — confirmed sponsors + their logo, for a simple "our sponsors" section on the org's public page. */
	@GetMapping("/organizations/{slug}/sponsors")
	fun listSponsorDirectory(@PathVariable slug: String): List<SponsorDirectoryEntryResponse> =
		sponsorshipService.listPublicDirectory(slug).map { it.toResponse() }

	@PostMapping("/sponsorship-packages/{packageId}/sponsorships")
	fun createCheckoutSession(
		@PathVariable packageId: UUID,
		@Valid @RequestBody request: CreateSponsorshipCheckoutRequest,
	): SponsorshipCheckoutResponse =
		sponsorshipService.createCheckoutSession(
			packageId, request.sponsorName, request.sponsorContactEmail, request.successUrl, request.cancelUrl,
		).toResponse()

	@GetMapping("/sponsorship-packages/{packageId}/sponsorships/{sponsorshipId}")
	fun getStatus(
		@PathVariable packageId: UUID,
		@PathVariable sponsorshipId: UUID,
	): SponsorshipStatusResponse = sponsorshipService.getStatus(packageId, sponsorshipId).toStatusResponse()
}
