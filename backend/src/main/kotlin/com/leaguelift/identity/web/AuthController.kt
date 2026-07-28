package com.leaguelift.identity.web

import com.leaguelift.identity.application.PasswordAuthenticationService
import com.leaguelift.identity.application.TokenService
import com.leaguelift.identity.domain.AppUser
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** Public — see the permitAll matcher for auth paths in `SecurityConfig`. */
@RestController
@RequestMapping("/api/v1/auth")
class AuthController(
	private val passwordAuthenticationService: PasswordAuthenticationService,
	private val tokenService: TokenService,
) {

	@PostMapping("/register")
	fun register(@Valid @RequestBody request: RegisterRequest): ResponseEntity<AuthResponse> {
		val displayName = "${request.firstName} ${request.lastName}".trim()
		val appUser = passwordAuthenticationService.register(request.email, request.password, displayName)
		return ResponseEntity.status(HttpStatus.CREATED).body(issueAuthResponse(appUser))
	}

	@PostMapping("/login")
	fun login(@Valid @RequestBody request: LoginRequest): AuthResponse {
		val appUser = passwordAuthenticationService.authenticate(request.email, request.password)
		return issueAuthResponse(appUser)
	}

	private fun issueAuthResponse(appUser: AppUser): AuthResponse {
		val token = tokenService.issueAccessToken(appUser.id, appUser.email, appUser.displayName)
		return AuthResponse(
			accessToken = token.accessToken,
			tokenType = "Bearer",
			expiresIn = token.expiresInSeconds,
			user = appUser.toResponse(),
		)
	}
}
