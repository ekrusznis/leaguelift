package com.rally26.identity.application

import com.rally26.common.error.ConflictException
import com.rally26.common.error.UnauthorizedException
import com.rally26.common.web.CurrentUser
import com.rally26.identity.domain.AppUser
import com.rally26.identity.domain.AppUserStatus
import com.rally26.identity.persistence.AppUserRepository
import com.rally26.onboarding.owner.persistence.OwnerOnboardingRepository
import org.springframework.dao.DuplicateKeyException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Registration and sign-in against our own database (superseding the planned
 * external-IdP model — DESIGN-DOC.md section 11.5/18.1, ADR-002). Passwords are
 * never logged or returned; only their bcrypt hash is persisted.
 */
@Service
class PasswordAuthenticationService(
    private val appUserRepository: AppUserRepository,
    private val emailVerificationService: EmailVerificationService,
    private val passwordEncoder: PasswordEncoder,
    private val ownerOnboardingRepository: OwnerOnboardingRepository? = null,
) {
    data class RegistrationAccepted(
        val email: String,
    )

    /**
     * Internal helper still used by integration tests to create an already-active user
     * directly. Public endpoints should call [registerOwner].
     */
    @Transactional
    fun register(
        email: String,
        password: String,
        displayName: String,
    ): AppUser {
        val normalizedEmail = email.trim().lowercase()
        if (appUserRepository.findByEmail(normalizedEmail) != null) {
            throw emailAlreadyRegistered()
        }
        return try {
            appUserRepository.insert(
                email = normalizedEmail,
                displayName = displayName,
                passwordHash = passwordEncoder.encode(password),
                status = AppUserStatus.ACTIVE,
            )
        } catch (_: DuplicateKeyException) {
            throw emailAlreadyRegistered()
        }
    }

    @Transactional
    fun registerOwner(
        email: String,
        password: String,
        displayName: String,
        invitationToken: String? = null,
        acceptedTerms: Boolean = true,
        confirmedAdult: Boolean = true,
    ): RegistrationAccepted {
        val normalizedEmail = email.trim().lowercase()
        if (appUserRepository.findByEmail(normalizedEmail) != null) {
            throw emailAlreadyRegistered()
        }
        require(acceptedTerms) { "Terms must be accepted before owner registration." }
        require(confirmedAdult) { "Owner registration requires adult confirmation." }
        val created =
            try {
                appUserRepository.insert(
                    email = normalizedEmail,
                    displayName = displayName,
                    passwordHash = passwordEncoder.encode(password),
                    status = AppUserStatus.PENDING_EMAIL_VERIFICATION,
                )
            } catch (_: DuplicateKeyException) {
                throw emailAlreadyRegistered()
            }
        // Invitation registrations are staff/guardian/athlete account activation, not
        // organization-owner self-service. Only a normal owner registration gets a
        // resumable onboarding row; the unique owner_user_id constraint makes retries safe.
        if (invitationToken.isNullOrBlank()) {
            ownerOnboardingRepository?.createForOwner(created.id)
        }
        val issued = emailVerificationService.issueForUser(created.id, invitationToken)
        emailVerificationService.enqueueVerificationEmail(issued)
        return RegistrationAccepted(email = normalizedEmail)
    }

    /** Generic failure message regardless of whether the email exists — never confirm account existence. */
    fun authenticate(
        email: String,
        password: String,
    ): AppUser {
        val appUser = appUserRepository.findByEmail(email) ?: throw invalidCredentials()
        if (appUser.status == AppUserStatus.PENDING_EMAIL_VERIFICATION) {
            throw UnauthorizedException(
                code = "EMAIL_NOT_VERIFIED",
                message = "Verify your email before signing in.",
            )
        }
        val hash = appUser.passwordHash ?: throw invalidCredentials()
        if (!passwordEncoder.matches(password, hash)) {
            throw invalidCredentials()
        }
        return appUser
    }

    private fun emailAlreadyRegistered() = ConflictException("EMAIL_ALREADY_REGISTERED", "An account with that email already exists.")

    private fun invalidCredentials() = UnauthorizedException("INVALID_CREDENTIALS", "Invalid email or password.")

    fun toCurrentUser(
        appUser: AppUser,
        platformAdministrator: Boolean = false,
    ): CurrentUser =
        CurrentUser(
            userId = appUser.id,
            email = appUser.email,
            displayName = appUser.displayName,
            platformAdministrator = platformAdministrator,
        )
}
