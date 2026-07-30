package com.leaguelift.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Bound from `leaguelift.frontend.base-url` (Phase 8 slice 1, ADR-022) — the deployed
 * frontend's own origin, needed the first time backend code (not a frontend page) has
 * to build a complete, clickable URL itself: `InvitationEmailHandler` links to
 * `{baseUrl}/auth/invitation?token=...`. Not a secret — defaults to the local Vite dev
 * server, same value [CorsProperties.allowedOrigins] already defaults to.
 */
@ConfigurationProperties(prefix = "leaguelift.frontend")
data class FrontendProperties(
	val baseUrl: String = "http://localhost:5173",
)
