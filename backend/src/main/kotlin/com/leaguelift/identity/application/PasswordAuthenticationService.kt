package com.leaguelift.identity.application

import com.leaguelift.common.error.ConflictException
import com.leaguelift.common.error.UnauthorizedException
import com.leaguelift.common.web.CurrentUser
import com.leaguelift.identity.domain.AppUser
import com.leaguelift.identity.persistence.AppUserRepository
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
	private val passwordEncoder: PasswordEncoder,
) {

	@Transactional
	fun register(email: String, password: String, displayName: String): AppUser {
		if (appUserRepository.findByEmail(email) != null) {
			throw emailAlreadyRegistered()
		}
		return try {
			appUserRepository.insert(email, displayName, passwordEncoder.encode(password))
		} catch (ex: DuplicateKeyException) {
			throw emailAlreadyRegistered()
		}
	}

	/** Generic failure message regardless of whether the email exists — never confirm account existence. */
	fun authenticate(email: String, password: String): AppUser {
		val appUser = appUserRepository.findByEmail(email) ?: throw invalidCredentials()
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
