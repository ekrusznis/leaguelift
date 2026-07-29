package com.leaguelift.payout.web

import com.fasterxml.jackson.annotation.JsonProperty
import com.leaguelift.payout.domain.OrganizationPayoutAccount
import jakarta.validation.constraints.NotBlank
import java.time.Instant

data class StartOnboardingRequest(
	@field:NotBlank val refreshUrl: String,
	@field:NotBlank val returnUrl: String,
)

data class OnboardingLinkResponse(val onboardingUrl: String)

data class PayoutAccountResponse(
	val stripeAccountId: String,
	val detailsSubmitted: Boolean,
	val chargesEnabled: Boolean,
	val payoutsEnabled: Boolean,
	// Kotlin's `val isFoo: Boolean` generates a getter literally named `isFoo()`; Jackson's
	// standard JavaBean introspection strips the "is" prefix unless pinned explicitly —
	// see AthleteDashboardDto.kt for the same fix applied to the dashboard DTOs.
	@get:JsonProperty("isFullyConnected") val isFullyConnected: Boolean,
	val updatedAt: Instant,
)

fun OrganizationPayoutAccount.toResponse() = PayoutAccountResponse(
	stripeAccountId = stripeAccountId,
	detailsSubmitted = detailsSubmitted,
	chargesEnabled = chargesEnabled,
	payoutsEnabled = payoutsEnabled,
	isFullyConnected = isFullyConnected,
	updatedAt = updatedAt,
)
