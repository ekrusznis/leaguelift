package com.leaguelift.identity.application

import com.leaguelift.common.error.ConflictException
import com.leaguelift.common.error.UnauthorizedException
import com.leaguelift.common.web.CurrentUser
import com.leaguelift.identity.domain.AppUser
import com.leaguelift.identity.domain.AppUserStatus
import com.leaguelift.identity.persistence.AppUserRepository
import com.leaguelift.outbox.application.OutboxWriter
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
	private val outboxWriter: OutboxWriter,
	private val passwordEncoder: PasswordEncoder,
) {

	data class RegistrationAccepted(
		val email: String,
	)

	/**
	 * Internal helper still used by integration tests to create an already-active user
	 * directly. Public endpoints should call [registerOwner].
	 */
	@Transactional
	fun register(email: String, password: String, displayName: String): AppUser {
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
	fun registerOwner(email: String, password: String, displayName: String): RegistrationAccepted {
		val normalizedEmail = email.trim().lowercase()
		if (appUserRepository.findByEmail(normalizedEmail) != null) {
			throw emailAlreadyRegistered()
		}
		val created = try {
			appUserRepository.insert(
				email = normalizedEmail,
				displayName = displayName,
				passwordHash = passwordEncoder.encode(password),
				status = AppUserStatus.PENDING_EMAIL_VERIFICATION,
			)
		} catch (_: DuplicateKeyException) {
			throw emailAlreadyRegistered()
		}
		val issued = emailVerificationService.issueForUser(created.id)
		outboxWriter.write(
			aggregateType = "app_user",
			aggregateId = created.id,
			organizationId = null,
			eventType = "auth.owner_verification_requested",
			payloadJson =
				"""{"userId":"${issued.userId}","email":"${issued.email}","verificationToken":"${issued.rawToken}"}""",
		)
		return RegistrationAccepted(email = normalizedEmail)
	}

	/** Generic failure message regardless of whether the email exists — never confirm account existence. */
	fun authenticate(email: String, password: String): AppUser {
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

	private fun emailAlreadyRegistered() =
		ConflictException("EMAIL_ALREADY_REGISTERED", "An account with that email already exists.")

	private fun invalidCredentials() =
		UnauthorizedException("INVALID_CREDENTIALS", "Invalid email or password.")

	fun toCurrentUser(appUser: AppUser, platformAdministrator: Boolean = false): CurrentUser =
		CurrentUser(
			userId = appUser.id,
			email = appUser.email,
			displayName = appUser.displayName,
			platformAdministrator = platformAdministrator,
		)
}
