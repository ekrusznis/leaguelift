package com.leaguelift.identity.web

import com.leaguelift.common.web.CurrentUser
import com.leaguelift.identity.application.UserProvisioningService
import com.leaguelift.identity.persistence.AppUserRepository
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1")
class CurrentUserController(
	private val appUserRepository: AppUserRepository,
	private val userProvisioningService: UserProvisioningService,
) {

	// By the time a request reaches here, the security filter chain has already
	// provisioned the app_user (see JwtCurrentUserConverter / LocalDevAuthenticationFilter),
	// so /me is a read of already-resolved identity, and /bootstrap is idempotent by
	// construction rather than by special-casing here.

	@GetMapping("/me")
	fun me(@AuthenticationPrincipal currentUser: CurrentUser): UserResponse {
		val appUser = appUserRepository.findById(currentUser.userId)
			?: error("Authenticated user ${currentUser.userId} has no app_user record")
		return appUser.toResponse()
	}

	@PostMapping("/bootstrap")
	fun bootstrap(@AuthenticationPrincipal currentUser: CurrentUser): UserResponse {
		val appUser = appUserRepository.findById(currentUser.userId)
			?: error("Authenticated user ${currentUser.userId} has no app_user record")
		return appUser.toResponse()
	}
}
