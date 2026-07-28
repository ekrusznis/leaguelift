package com.leaguelift.identity.application

import com.leaguelift.config.JwtProperties
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

data class IssuedToken(val accessToken: String, val expiresInSeconds: Long)

@Service
class TokenService(
	private val jwtEncoder: JwtEncoder,
	private val jwtProperties: JwtProperties,
) {

	fun issueAccessToken(userId: UUID, email: String, displayName: String): IssuedToken {
		val now = Instant.now()
		val ttlSeconds = jwtProperties.accessTokenTtlMinutes * 60
		val claims = JwtClaimsSet.builder()
			.issuer(jwtProperties.issuer)
			.issuedAt(now)
			.expiresAt(now.plusSeconds(ttlSeconds))
			.subject(userId.toString())
			.claim("email", email)
			.claim("name", displayName)
			.build()
		val header = JwsHeader.with(MacAlgorithm.HS256).build()
		val token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).tokenValue
		return IssuedToken(token, ttlSeconds)
	}
}
