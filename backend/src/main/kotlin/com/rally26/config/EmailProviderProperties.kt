package com.rally26.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Bound from `rally26.email.provider` (Phase 8 slice 1, ADR-022). Not a secret —
 * selects which `EmailProvider` bean is active: `"logging"` (default, the Phase 6
 * remainder stopgap, still the only mode with real credentials configured anywhere
 * today) or `"resend"` (real send, requires [ResendProperties] to be populated with a
 * real API key). Switching this without a real Resend key configured just means
 * `ResendConfig`'s client calls fail loudly — same "still fails cleanly, never
 * silently no-ops" posture as every other provider in this codebase.
 */
@ConfigurationProperties(prefix = "rally26.email")
data class EmailProviderProperties(
	val provider: String = "logging",
)
