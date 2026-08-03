package com.leaguelift.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Bound from `leaguelift.email.resend.*` (Phase 8 slice 1, ADR-022). Only consulted
 * when `leaguelift.email.provider = resend` ([EmailProviderProperties]) — unlike
 * [PrintifyProperties]/[StripeProperties], both fields keep a blank Kotlin default in
 * every profile (including staging/prod) because "logging" (the default provider) is a
 * legitimate, fully-supported mode where these values are simply irrelevant; there's no
 * single "is this integration active" signal a property-placeholder default could key
 * off. The safety net for a real deployment that flips to `resend` without supplying a
 * real key is the same one Printify already uses: [ResendConfig] still builds a working
 * client with a blank key, and the resulting 401 is translated into a clean
 * `ServiceUnavailableException` at call time, not a build/startup failure.
 */
@ConfigurationProperties(prefix = "leaguelift.email.resend")
data class ResendProperties(
	val apiKey: String = "",
	val fromAddress: String = "",
	val webhookSecret: String = "",
)
