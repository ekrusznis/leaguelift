package com.leaguelift.config

import com.leaguelift.common.web.CurrentUser
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority

/**
 * The [org.springframework.security.core.Authentication] used throughout the app once
 * a request is authenticated, regardless of whether it came from a validated JWT or
 * the local-dev bypass. Controllers/services depend on [CurrentUser] (via
 * `@AuthenticationPrincipal`), never on the raw JWT or servlet request, keeping
 * business logic independent of the identity-provider implementation
 * (DESIGN-DOC.md section 11.5).
 */
class CurrentUserAuthenticationToken(private val currentUser: CurrentUser) :
	AbstractAuthenticationToken(
		if (currentUser.platformAdministrator) {
			listOf(SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN"))
		} else {
			emptyList()
		},
	) {

	init {
		isAuthenticated = true
	}

	override fun getCredentials(): Any = ""
	override fun getPrincipal(): CurrentUser = currentUser
}
