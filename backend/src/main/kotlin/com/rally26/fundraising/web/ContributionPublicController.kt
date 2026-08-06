package com.rally26.fundraising.web

import com.rally26.fundraising.application.ContributionService
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * Public, unauthenticated — supporters contributing to a campaign are never
 * Rally26 users. Confirmation is authoritative only via the Stripe webhook
 * (`webhook/web/StripeWebhookController.kt`); this controller's GET is a
 * read-only poll for the browser's return page, nothing here flips status.
 */
@RestController
@RequestMapping("/api/v1/public/campaigns/{slug}/contributions")
class ContributionPublicController(
    private val contributionService: ContributionService,
) {
    @PostMapping
    fun createCheckoutSession(
        @PathVariable slug: String,
        @Valid @RequestBody request: CreateContributionCheckoutRequest,
    ): ContributionCheckoutResponse =
        contributionService
            .createCheckoutSession(
                slug,
                request.amountMinor,
                request.supporterName,
                request.isAnonymous,
                request.supporterEmail,
                request.successUrl,
                request.cancelUrl,
                request.attributionCode,
            ).toResponse()

    @GetMapping("/{contributionId}")
    fun getStatus(
        @PathVariable slug: String,
        @PathVariable contributionId: UUID,
    ): ContributionStatusResponse = contributionService.getStatus(slug, contributionId).toStatusResponse()
}
