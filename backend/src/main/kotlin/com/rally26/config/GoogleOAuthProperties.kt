package com.rally26.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Bound from `rally26.oauth.google.*` (Phase 37). Same posture as [TwilioProperties]/
 * [ResendProperties]: a blank Kotlin default in every profile, since Rally26 has no
 * registered Google Cloud OAuth client yet. `OAuthSignInService` fails closed with a
 * `ServiceUnavailableException` when this is blank rather than attempting to verify a
 * token against an empty audience.
 */
@ConfigurationProperties(prefix = "rally26.oauth.google")
data class GoogleOAuthProperties(
    val clientId: String = "",
)
