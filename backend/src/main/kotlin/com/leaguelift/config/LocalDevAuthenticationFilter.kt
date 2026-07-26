package com.leaguelift.config

import com.leaguelift.identity.application.UserProvisioningService
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.annotation.Profile
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Development-only authentication bypass: sets a fixed internal user as the
 * authenticated principal for every request, so the API can be exercised without a
 * configured OIDC tenant.
 *
 * This bean only exists at all when the `local` or `test` Spring profile is active
 * (`@Profile`), AND `SecurityConfig` only wires it into the filter chain when
 * `leaguelift.auth.dev-bypass-enabled=true` (set in application-local.yml /
 * application-test.yml, and explicitly false in application-staging.yml /
 * application-prod.yml). Both conditions must hold, so this cannot be silently
 * enabled in a deployed environment through configuration drift alone. See
 * docs/security.md.
 */
@Component
@Profile("local", "test")
class LocalDevAuthenticationFilter(
	private val authProperties: AuthProperties,
	private val userProvisioningService: UserProvisioningService,
) : OncePerRequestFilter() {

	override fun doFilterInternal(
		request: HttpServletRequest,
		response: HttpServletResponse,
		filterChain: FilterChain,
	) {
		val devUser = authProperties.devBypassUser
		val appUser = userProvisioningService.resolveOrProvision(
			externalSubject = devUser.externalSubject,
			email = devUser.email,
			displayName = devUser.displayName,
		)
		val currentUser = userProvisioningService.toCurrentUser(appUser, platformAdministrator = true)
		SecurityContextHolder.getContext().authentication = CurrentUserAuthenticationToken(currentUser)
		filterChain.doFilter(request, response)
	}
}
