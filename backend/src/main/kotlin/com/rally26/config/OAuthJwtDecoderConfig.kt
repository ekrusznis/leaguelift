package com.rally26.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtValidators
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder

/**
 * Real remote-JWKS verifiers for Phase 37's Google/Apple mobile sign-in, kept as
 * injectable beans (rather than built inline in `OAuthSignInService`) specifically so
 * a unit test can substitute a mock `JwtDecoder` instead of hitting a real provider's
 * JWKS endpoint over the network. `NimbusJwtDecoder` caches the fetched key set
 * internally, so these must stay singletons, not be constructed per request.
 */
@Configuration
class OAuthJwtDecoderConfig {
    @Bean
    fun googleJwtDecoder(): JwtDecoder =
        NimbusJwtDecoder.withJwkSetUri("https://www.googleapis.com/oauth2/v3/certs").build().apply {
            setJwtValidator(JwtValidators.createDefaultWithIssuer("https://accounts.google.com"))
        }

    @Bean
    fun appleJwtDecoder(): JwtDecoder =
        NimbusJwtDecoder.withJwkSetUri("https://appleid.apple.com/auth/keys").build().apply {
            setJwtValidator(JwtValidators.createDefaultWithIssuer("https://appleid.apple.com"))
        }
}
