package com.rally26.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Bound from `rally26.stripe.*`. Test-mode only (ADR-005) — no live charge
 * routing. Neither [secretKey] nor [webhookSecret] has a default in staging/prod
 * config so a missing value fails startup rather than silently running
 * unauthenticated/unverified; locally both default to blank since no real Stripe
 * keys are provisioned yet (calls simply fail with a clear error until a founder
 * supplies test-mode keys). [webhookSecret] verifies the `Stripe-Signature` header
 * on `POST /api/v1/webhooks/stripe` (campaign-contribution confirmation only this
 * slice — Connect-account webhooks remain Phase 5).
 */
@ConfigurationProperties(prefix = "rally26.stripe")
data class StripeProperties(
	val secretKey: String,
	val webhookSecret: String,
)
