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
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtException
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class OAuthSignInServiceTest {
    private val appUserRepository = mockk<AppUserRepository>()
    private val googleProperties = GoogleOAuthProperties(clientId = "google-client-id")
    private val appleProperties = AppleOAuthProperties(clientId = "com.rally26.mobile")
    private val googleDecoder = mockk<JwtDecoder>()
    private val appleDecoder = mockk<JwtDecoder>()
    private val auditService = mockk<AuditService>(relaxed = true)
    private val service =
        OAuthSignInService(appUserRepository, googleProperties, appleProperties, googleDecoder, appleDecoder, auditService)

    private fun jwt(
        subject: String = "provider-subject-1",
        email: String? = "athlete@example.com",
        name: String? = "Jordan Smith",
        audience: List<String> = listOf("google-client-id"),
    ): Jwt =
        Jwt
            .withTokenValue("token-value")
            .header("alg", "RS256")
            .subject(subject)
            .audience(audience)
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .apply {
                if (email != null) claim("email", email)
                if (name != null) claim("name", name)
            }.build()

    private fun appUser(
        id: UUID = UUID.randomUUID(),
        email: String = "athlete@example.com",
        status: AppUserStatus = AppUserStatus.ACTIVE,
        passwordHash: String? = "hash",
        provider: OAuthProvider? = null,
        providerSubject: String? = null,
    ) = AppUser(id, email, "Jordan Smith", status, passwordHash, Instant.now(), Instant.now(), provider, providerSubject)

    @Test
    fun `an unconfigured provider fails closed with a 503`() {
        val service =
            OAuthSignInService(appUserRepository, GoogleOAuthProperties(), appleProperties, googleDecoder, appleDecoder, auditService)

        assertFailsWith<ServiceUnavailableException> {
            service.signIn(OAuthProvider.GOOGLE, "token-value")
        }
    }

    @Test
    fun `an invalid token is rejected`() {
        every { googleDecoder.decode("bad-token") } throws JwtException("bad signature")

        assertFailsWith<UnauthorizedException> {
            service.signIn(OAuthProvider.GOOGLE, "bad-token")
        }
    }

    @Test
    fun `a token minted for a different audience is rejected`() {
        every { googleDecoder.decode("token-value") } returns jwt(audience = listOf("someone-elses-client-id"))

        assertFailsWith<UnauthorizedException> {
            service.signIn(OAuthProvider.GOOGLE, "token-value")
        }
    }

    @Test
    fun `a returning provider identity signs in directly`() {
        val existing = appUser(provider = OAuthProvider.GOOGLE, providerSubject = "provider-subject-1")
        every { googleDecoder.decode("token-value") } returns jwt()
        every { appUserRepository.findByProvider(OAuthProvider.GOOGLE, "provider-subject-1") } returns existing

        val result = service.signIn(OAuthProvider.GOOGLE, "token-value")

        assertEquals(existing.id, result.id)
        verify(exactly = 0) { appUserRepository.findByEmail(any()) }
    }

    @Test
    fun `a first-time sign-in with no matching account creates a new active account`() {
        val created = appUser(provider = OAuthProvider.GOOGLE, providerSubject = "provider-subject-1", passwordHash = null)
        every { googleDecoder.decode("token-value") } returns jwt()
        every { appUserRepository.findByProvider(OAuthProvider.GOOGLE, "provider-subject-1") } returns null
        every { appUserRepository.findByEmail("athlete@example.com") } returns null
        every {
            appUserRepository.insertOAuthUser("athlete@example.com", "Jordan Smith", OAuthProvider.GOOGLE, "provider-subject-1")
        } returns created

        val result = service.signIn(OAuthProvider.GOOGLE, "token-value")

        assertEquals(AppUserStatus.ACTIVE, result.status)
        assertNull(result.passwordHash)
    }

    @Test
    fun `a provider sign-in claims and invalidates an unverified password registration for the same email`() {
        val pending = appUser(status = AppUserStatus.PENDING_EMAIL_VERIFICATION, passwordHash = "attacker-set-hash")
        every { googleDecoder.decode("token-value") } returns jwt()
        every { appUserRepository.findByProvider(OAuthProvider.GOOGLE, "provider-subject-1") } returns null
        every { appUserRepository.findByEmail("athlete@example.com") } returns pending
        every { appUserRepository.claimViaProvider(pending.id, OAuthProvider.GOOGLE, "provider-subject-1") } returns 1

        val result = service.signIn(OAuthProvider.GOOGLE, "token-value")

        assertEquals(AppUserStatus.ACTIVE, result.status)
        assertNull(result.passwordHash)
        verify(exactly = 1) { appUserRepository.claimViaProvider(pending.id, OAuthProvider.GOOGLE, "provider-subject-1") }
    }

    @Test
    fun `a provider sign-in links onto an already-active password account with the same email`() {
        val active = appUser(status = AppUserStatus.ACTIVE, passwordHash = "real-hash")
        every { googleDecoder.decode("token-value") } returns jwt()
        every { appUserRepository.findByProvider(OAuthProvider.GOOGLE, "provider-subject-1") } returns null
        every { appUserRepository.findByEmail("athlete@example.com") } returns active
        every { appUserRepository.linkProvider(active.id, OAuthProvider.GOOGLE, "provider-subject-1") } returns 1

        val result = service.signIn(OAuthProvider.GOOGLE, "token-value")

        assertEquals("real-hash", result.passwordHash)
        assertEquals(OAuthProvider.GOOGLE, result.provider)
    }

    @Test
    fun `a suspended account cannot sign in via a newly linked provider`() {
        val suspended = appUser(status = AppUserStatus.SUSPENDED)
        every { googleDecoder.decode("token-value") } returns jwt()
        every { appUserRepository.findByProvider(OAuthProvider.GOOGLE, "provider-subject-1") } returns null
        every { appUserRepository.findByEmail("athlete@example.com") } returns suspended

        assertFailsWith<UnauthorizedException> {
            service.signIn(OAuthProvider.GOOGLE, "token-value")
        }
    }

    @Test
    fun `a token with no email claim is rejected`() {
        every { googleDecoder.decode("token-value") } returns jwt(email = null)

        assertFailsWith<UnauthorizedException> {
            service.signIn(OAuthProvider.GOOGLE, "token-value")
        }
    }
}
