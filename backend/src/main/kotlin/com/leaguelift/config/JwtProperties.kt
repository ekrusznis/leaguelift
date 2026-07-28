package com.leaguelift.config

import org.springframework.boot.context.properties.ConfigurationProperties
import javax.crypto.spec.SecretKeySpec

/**
 * Bound from `leaguelift.jwt.*`. Access tokens are self-issued and self-validated
 * (HS256) — there is no external identity provider (see docs/security.md and
 * `JwtConfig`). [secret] must never be committed; staging/prod read it from
 * `JWT_SECRET` with no default, so a missing value fails startup rather than
 * silently using a guessable key.
 */
@ConfigurationProperties(prefix = "leaguelift.jwt")
data class JwtProperties(
	val secret: String,
	val issuer: String = "leaguelift-api",
	val accessTokenTtlMinutes: Long = 60,
) {
	/** HS256 requires a key of at least 256 bits (32 bytes). */
	fun secretKey(): SecretKeySpec {
		val bytes = secret.toByteArray()
		require(bytes.size >= 32) { "leaguelift.jwt.secret must be at least 32 bytes for HS256." }
		return SecretKeySpec(bytes, "HmacSHA256")
	}
}
