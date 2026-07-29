package com.leaguelift.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Bound from `leaguelift.stripe.*`. Test-mode Connect Express onboarding only this
 * slice (ADR-005) — no live charge routing. [secretKey] has no default in
 * staging/prod config so a missing value fails startup rather than silently running
 * unauthenticated; locally it defaults to blank since no real Stripe keys are
 * provisioned yet (onboarding calls simply fail with a clear error until a founder
 * supplies test-mode keys).
 */
@ConfigurationProperties(prefix = "leaguelift.stripe")
data class StripeProperties(
	val secretKey: String,
)
