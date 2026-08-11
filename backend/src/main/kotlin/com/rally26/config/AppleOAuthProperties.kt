package com.rally26.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Bound from `rally26.oauth.apple.*` (Phase 37). `clientId` is Apple's audience claim
 * for a native mobile Sign in with Apple flow, which is the app's own bundle
 * identifier (e.g. `com.rally26.mobile`), not a separate registered secret the way
 * Google's client id is — but it still defaults to blank here so `OAuthSignInService`
 * fails closed the same way until Sign in with Apple is actually enabled in Rally26's
 * Apple Developer account/provisioning profile.
 */
@ConfigurationProperties(prefix = "rally26.oauth.apple")
data class AppleOAuthProperties(
    val clientId: String = "",
)
