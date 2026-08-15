package com.rally26.boxpool.web

import com.rally26.boxpool.application.BoxPoolService
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** Public, unauthenticated — same "slug-scoped, no auth, Stripe-webhook-is-authoritative" shape as `CampaignPublicController`/`ContributionPublicController`/`OrderPublicController`. */
@RestController
@RequestMapping("/api/v1/public/campaigns/{slug}/box-pool")
class BoxPoolPublicController(
    private val boxPoolService: BoxPoolService,
) {
    @GetMapping
    fun get(
        @PathVariable slug: String,
    ): BoxPoolResponse = boxPoolService.getPublic(slug).toResponse()

    @PostMapping("/boxes/{rowIndex}/{colIndex}/reserve")
    fun reserveBox(
        @PathVariable slug: String,
        @PathVariable rowIndex: Int,
        @PathVariable colIndex: Int,
        @Valid @RequestBody request: ReserveBoxRequest,
    ): ReserveBoxResponse {
        val checkout =
            boxPoolService.reserveBox(
                slug,
                rowIndex,
                colIndex,
                request.claimantName,
                request.claimantEmail,
                request.successUrl,
                request.cancelUrl,
            )
        return ReserveBoxResponse(checkout.contributionId, checkout.checkoutUrl)
    }
}
