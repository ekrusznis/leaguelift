package com.rally26.config

import com.rally26.authorization.persistence.RoleAssignmentRepository
import com.rally26.common.web.CurrentUser
import com.rally26.identity.persistence.AppUserRepository
import org.springframework.core.convert.converter.Converter
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Converts a validated [Jwt] (signature/issuer/expiration already checked by
 * `NimbusJwtDecoder` — see `JwtConfig`) into a [CurrentUserAuthenticationToken].
 * Unlike the external-IdP version this replaced, this never provisions a user: we
 * issue our own tokens (see `TokenService`), so a valid token can only exist for an
 * app_user that already exists. A missing row means the account was removed after
 * the token was issued. Platform-administrator status is never derived from the
 * token itself (DESIGN-DOC.md section 18.2/7.2 — "never inferred from email or
 * frontend state") — as of Phase 7/ADR-020 it comes from a real, explicit
 * `role_assignment(context_type = PLATFORM, role = PLATFORM_ADMIN)` grant,
 * looked up fresh on every request rather than embedded in the token.
 */
@Component
class JwtCurrentUserConverter(
    private val appUserRepository: AppUserRepository,
    private val roleAssignmentRepository: RoleAssignmentRepository,
) : Converter<Jwt, AbstractAuthenticationToken> {
    override fun convert(jwt: Jwt): AbstractAuthenticationToken {
        val userId =
            jwt.subject?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                ?: throw IllegalArgumentException("JWT missing or invalid 'sub' claim")
        val appUser =
            appUserRepository.findById(userId)
                ?: throw IllegalArgumentException("Token subject $userId has no app_user record")
        val currentUser =
            CurrentUser(
                userId = appUser.id,
                email = appUser.email,
                displayName = appUser.displayName,
                platformAdministrator = roleAssignmentRepository.findActivePlatformGrant(appUser.id) != null,
            )
        return CurrentUserAuthenticationToken(currentUser)
    }
}
