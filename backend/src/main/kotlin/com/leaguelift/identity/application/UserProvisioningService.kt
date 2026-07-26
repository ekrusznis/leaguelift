package com.leaguelift.identity.application

import com.leaguelift.common.web.CurrentUser
import com.leaguelift.identity.domain.AppUser
import com.leaguelift.identity.persistence.AppUserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Resolves an internal [AppUser] from an external identity-provider subject,
 * provisioning it on first sight. Keyed by external_subject, never by email
 * (DESIGN-DOC.md section 11.5 — email is not the permanent identity key).
 */
@Service
class UserProvisioningService(private val appUserRepository: AppUserRepository) {

	@Transactional
	fun resolveOrProvision(externalSubject: String, email: String, displayName: String): AppUser {
		appUserRepository.findByExternalSubject(externalSubject)?.let { return it }
		return appUserRepository.insert(externalSubject, email, displayName)
	}

	fun toCurrentUser(appUser: AppUser, platformAdministrator: Boolean = false): CurrentUser =
		CurrentUser(
			userId = appUser.id,
			externalSubject = appUser.externalSubject,
			email = appUser.email,
			displayName = appUser.displayName,
			platformAdministrator = platformAdministrator,
		)
}
