package com.rally26.identity.application

import com.rally26.audit.application.AuditService
import com.rally26.common.error.ServiceUnavailableException
import com.rally26.common.error.UnauthorizedException
import com.rally26.config.AppleOAuthProperties
import com.rally26.config.GoogleOAuthProperties
import com.rally26.identity.domain.AppUser
import com.rally26.identity.domain.AppUserStatus
import com.rally26.identity.domain.OAuthProvider
import com.rally26.identity.persistence.AppUserRepository
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Real Google/Apple mobile sign-in (Phase 37) — extends ADR-014's traditional
 * password-authentication model rather than reversing it. The mobile app's native
 * Google/Apple SDK returns a provider-signed ID token directly to the client; this
 * service verifies that token's signature against the provider's own public JWKS via
 * the injected [JwtDecoder]s (see `OAuthJwtDecoderConfig`) — never trusts a
 * client-supplied claim without verification — then issues Rally26's own session via
 * [TokenService], the exact same encoder password sign-in uses. No external session or
 * redirect flow is involved; this is a native/mobile "verify a token" flow, not a
 * browser OAuth redirect.
 */
@Service
class OAuthSignInService(
    private val appUserRepository: AppUserRepository,
    private val googleOAuthProperties: GoogleOAuthProperties,
    private val appleOAuthProperties: AppleOAuthProperties,
    @Qualifier("googleJwtDecoder") private val googleJwtDecoder: JwtDecoder,
    @Qualifier("appleJwtDecoder") private val appleJwtDecoder: JwtDecoder,
    private val auditService: AuditService,
) {
    @Transactional
    fun signIn(
        provider: OAuthProvider,
        idToken: String,
    ): AppUser {
        val clientId = clientIdFor(provider)
        if (clientId.isBlank()) {
            throw ServiceUnavailableException(
                "${provider.name}_OAUTH_NOT_CONFIGURED",
                "${provider.displayName()} sign-in is not yet configured for Rally26.",
            )
        }
        val jwt =
            try {
                decoderFor(provider).decode(idToken)
            } catch (_: JwtException) {
                throw UnauthorizedException("OAUTH_TOKEN_INVALID", "That sign-in could not be verified.")
            }
        if (clientId !in jwt.audience.orEmpty()) {
            throw UnauthorizedException("OAUTH_TOKEN_INVALID", "That sign-in could not be verified.")
        }
        val providerSubject = jwt.subject ?: throw UnauthorizedException("OAUTH_TOKEN_INVALID", "That sign-in could not be verified.")
        val email =
            (jwt.claims["email"] as? String)?.trim()?.lowercase()
                ?: throw UnauthorizedException("OAUTH_EMAIL_REQUIRED", "This account has no email address to sign in with.")

        appUserRepository.findByProvider(provider, providerSubject)?.let { existing ->
            requireSignable(existing)
            return existing
        }

        val existingByEmail = appUserRepository.findByEmail(email)
        return when {
            existingByEmail == null -> {
                val displayName = (jwt.claims["name"] as? String)?.trim()?.takeIf { it.isNotBlank() } ?: email.substringBefore("@")
                val created = appUserRepository.insertOAuthUser(email, displayName, provider, providerSubject)
                auditService.record(created.id, null, "identity.oauth_account_created", "app_user", created.id)
                created
            }
            existingByEmail.status == AppUserStatus.PENDING_EMAIL_VERIFICATION -> {
                // See AppUserRepository.claimViaProvider's doc comment — the provider has
                // just proven real ownership of this email more strongly than an unverified
                // password ever did, so this claims the row and invalidates that password.
                appUserRepository.claimViaProvider(existingByEmail.id, provider, providerSubject)
                auditService.record(existingByEmail.id, null, "identity.oauth_account_claimed", "app_user", existingByEmail.id)
                existingByEmail.copy(
                    status = AppUserStatus.ACTIVE,
                    passwordHash = null,
                    provider = provider,
                    providerSubject = providerSubject,
                )
            }
            else -> {
                requireSignable(existingByEmail)
                appUserRepository.linkProvider(existingByEmail.id, provider, providerSubject)
                auditService.record(existingByEmail.id, null, "identity.oauth_provider_linked", "app_user", existingByEmail.id)
                existingByEmail.copy(provider = provider, providerSubject = providerSubject)
            }
        }
    }

    /** Suspended accounts (including Phase 27.4 merged source identities) never get a new sign-in path just because a provider was linked. */
    private fun requireSignable(appUser: AppUser) {
        if (appUser.status == AppUserStatus.SUSPENDED) {
            throw UnauthorizedException("INVALID_CREDENTIALS", "Invalid email or password.")
        }
    }

    private fun clientIdFor(provider: OAuthProvider): String =
        when (provider) {
            OAuthProvider.GOOGLE -> googleOAuthProperties.clientId
            OAuthProvider.APPLE -> appleOAuthProperties.clientId
        }

    private fun decoderFor(provider: OAuthProvider): JwtDecoder =
        when (provider) {
            OAuthProvider.GOOGLE -> googleJwtDecoder
            OAuthProvider.APPLE -> appleJwtDecoder
        }

    private fun OAuthProvider.displayName(): String =
        when (this) {
            OAuthProvider.GOOGLE -> "Google"
            OAuthProvider.APPLE -> "Apple"
        }
}
