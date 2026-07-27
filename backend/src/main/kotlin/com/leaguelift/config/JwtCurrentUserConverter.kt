package com.leaguelift.config

import com.leaguelift.identity.application.UserProvisioningService
import org.springframework.core.convert.converter.Converter
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Component

/**
 * Converts a validated [Jwt] (signature/issuer/audience/expiration already checked by
 * Spring's resource-server support before this runs) into a [CurrentUserAuthenticationToken],
 * provisioning the internal app_user on first sight. Platform-administrator status is
 * intentionally NOT derived from any JWT claim here — it must come from the internal
 * membership/role model once that exists, never be inferred from the token or email
 * (DESIGN-DOC.md section 18.2).
 */
@Component
class JwtCurrentUserConverter(
	private val userProvisioningService: UserProvisioningService,
) : Converter<Jwt, AbstractAuthenticationToken> {

	override fun convert(jwt: Jwt): AbstractAuthenticationToken {
		val externalSubject = jwt.subject
			?: throw IllegalArgumentException("JWT missing required 'sub' claim")
		val email = jwt.getClaimAsString("email") ?: "$externalSubject@unknown.leaguelift.local"
		val displayName = jwt.getClaimAsString("name") ?: email
		val appUser = userProvisioningService.resolveOrProvision(externalSubject, email, displayName)
		val currentUser = userProvisioningService.toCurrentUser(appUser)
		return CurrentUserAuthenticationToken(currentUser)
	}
}
