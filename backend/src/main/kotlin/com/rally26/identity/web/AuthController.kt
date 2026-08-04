package com.rally26.identity.web

import com.rally26.identity.application.PasswordAuthenticationService
import com.rally26.identity.application.TokenService
import com.rally26.identity.application.EmailVerificationService
import com.rally26.identity.application.PasswordResetService
import com.rally26.identity.domain.AppUser
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
	private val emailVerificationService: EmailVerificationService,
	private val passwordResetService: PasswordResetService,
	private val tokenService: TokenService,
) {

	@PostMapping("/register")
	fun register(@Valid @RequestBody request: RegisterRequest): ResponseEntity<RegistrationAcceptedResponse> {
		return registerOwner(request)
	}

	@PostMapping("/register-owner")
	fun registerOwner(@Valid @RequestBody request: RegisterRequest): ResponseEntity<RegistrationAcceptedResponse> {
		val displayName = "${request.firstName} ${request.lastName}".trim()
		val accepted = passwordAuthenticationService.registerOwner(request.email, request.password, displayName)
		return ResponseEntity.status(HttpStatus.ACCEPTED).body(RegistrationAcceptedResponse(email = accepted.email))
	}

	@PostMapping("/verify-email")
	fun verifyEmail(@Valid @RequestBody request: VerifyEmailRequest): ResponseEntity<Void> {
		emailVerificationService.verify(request.token)
		return ResponseEntity.noContent().build()
	}

	@PostMapping("/verify-email/resend")
	fun resendVerificationEmail(@Valid @RequestBody request: ResendVerificationRequest): ResponseEntity<Void> {
		emailVerificationService.resend(request.email)
		return ResponseEntity.noContent().build()
	}

	@PostMapping("/password-reset/request")
	fun requestPasswordReset(@Valid @RequestBody request: PasswordResetRequest): ResponseEntity<Void> {
		passwordResetService.request(request.email)
		return ResponseEntity.noContent().build()
	}

	@PostMapping("/password-reset/complete")
	fun completePasswordReset(@Valid @RequestBody request: CompletePasswordResetRequest): ResponseEntity<Void> {
		passwordResetService.complete(request.token, request.password)
		return ResponseEntity.noContent().build()
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
