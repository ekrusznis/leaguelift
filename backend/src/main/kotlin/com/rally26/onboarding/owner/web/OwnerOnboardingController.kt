package com.rally26.onboarding.owner.web

import com.rally26.common.web.CurrentUser
import com.rally26.onboarding.owner.application.OwnerOnboardingService
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/owner-onboarding")
class OwnerOnboardingController(
    private val ownerOnboardingService: OwnerOnboardingService,
) {
    @GetMapping
    fun get(
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): OwnerOnboardingResponse = ownerOnboardingService.get(currentUser).toResponse()

    @GetMapping("/plans")
    fun plans(
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): List<SubscriptionPlanResponse> {
        ownerOnboardingService.get(currentUser)
        return ownerOnboardingService.listPlans().map { it.toResponse() }
    }

    @GetMapping("/timezone-suggestion")
    fun timezoneSuggestion(
        @RequestParam country: String?,
        @RequestParam state: String?,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): TimezoneSuggestionResponse {
        ownerOnboardingService.get(currentUser)
        return TimezoneSuggestionResponse(ownerOnboardingService.suggestTimezone(country, state))
    }

    @PutMapping("/organization")
    fun saveOrganization(
        @Valid @RequestBody request: SaveOwnerOrganizationRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): OwnerOnboardingResponse =
        ownerOnboardingService
            .saveOrganization(
                name = request.name,
                slug = request.slug,
                organizationType = request.organizationType,
                sports = request.sports,
                contactEmail = request.contactEmail,
                contactPhone = request.contactPhone,
                addressLine1 = request.addressLine1,
                addressLine2 = request.addressLine2,
                addressCity = request.addressCity,
                addressState = request.addressState,
                addressPostalCode = request.addressPostalCode,
                addressCountry = request.addressCountry,
                timezone = request.timezone,
                currentUser = currentUser,
            ).toResponse()

    @PutMapping("/plan")
    fun selectPlan(
        @Valid @RequestBody request: SelectOwnerPlanRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): OwnerOnboardingResponse = ownerOnboardingService.selectPlan(request.planCode, request.billingFrequency, currentUser).toResponse()

    @PostMapping("/checkout")
    fun checkout(
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): SubscriptionCheckoutResponse {
        val checkout = ownerOnboardingService.startCheckout(currentUser)
        return SubscriptionCheckoutResponse(checkout.sessionId, checkout.checkoutUrl)
    }

    @PostMapping("/free-activate")
    fun activateFreePlan(
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): OwnerOnboardingResponse = ownerOnboardingService.activateFreePlan(currentUser).toResponse()

    @GetMapping("/founding-promo-reserved")
    fun foundingPromoReserved(
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): FoundingPromoReservationResponse = FoundingPromoReservationResponse(ownerOnboardingService.hasReservedFoundingPromoCode(currentUser))

    @PostMapping("/founding-activate")
    fun activateFoundingPromoPlan(
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): OwnerOnboardingResponse = ownerOnboardingService.activateFoundingPromoPlan(currentUser).toResponse()
}
