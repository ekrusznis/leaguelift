package com.rally26.config

import com.nimbusds.jose.jwk.source.ImmutableSecret
import com.nimbusds.jose.proc.SecurityContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtValidators
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder

/**
 * Issues and validates our own JWTs (HS256, shared secret) instead of delegating to
 * an external OIDC provider — see `JwtProperties` and `InternalJwtCurrentUserConverter`.
 * Reuses `spring-boot-starter-oauth2-resource-server`'s Nimbus-backed encoder/decoder
 * and expiration/issuer validation rather than hand-rolling JWT handling.
 */
@Configuration
class JwtConfig(private val jwtProperties: JwtProperties) {

	@Bean
	fun jwtEncoder(): JwtEncoder {
		val jwkSource = ImmutableSecret<SecurityContext>(jwtProperties.secretKey())
		return NimbusJwtEncoder(jwkSource)
	}

	@Bean
	fun jwtDecoder(): JwtDecoder {
		val decoder = NimbusJwtDecoder.withSecretKey(jwtProperties.secretKey())
			.macAlgorithm(MacAlgorithm.HS256)
			.build()
		decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(jwtProperties.issuer))
		return decoder
	}
}
