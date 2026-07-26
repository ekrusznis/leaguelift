package com.leaguelift.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Bound from `leaguelift.auth.*`. [devBypassEnabled] must only ever be true in the
 * `local` and `test` application profiles (application-local.yml, application-test.yml).
 * It defaults to false and application-staging.yml / application-prod.yml explicitly
 * set it to false so there is no ambiguity. See docs/security.md.
 */
@ConfigurationProperties(prefix = "leaguelift.auth")
data class AuthProperties(
	val devBypassEnabled: Boolean = false,
	val devBypassUser: DevBypassUser = DevBypassUser(),
) {
	data class DevBypassUser(
		val externalSubject: String = "local-dev-user",
		val email: String = "dev@leaguelift.local",
		val displayName: String = "Local Dev User",
	)
}
