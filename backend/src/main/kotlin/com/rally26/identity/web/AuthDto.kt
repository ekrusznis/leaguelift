package com.rally26.identity.web

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class RegisterRequest(
    @field:NotBlank
    @field:Email
    val email: String,
    @field:NotBlank
    @field:Size(min = 8, max = 200)
    val password: String,
    @field:NotBlank
    @field:Size(max = 60)
    val firstName: String,
    @field:NotBlank
    @field:Size(max = 60)
    val lastName: String,
)

data class RegistrationAcceptedResponse(
    val email: String,
)

data class LoginRequest(
    @field:NotBlank
    @field:Email
    val email: String,
    @field:NotBlank
    val password: String,
)

data class VerifyEmailRequest(
    @field:NotBlank
    val token: String,
)

data class ResendVerificationRequest(
    @field:NotBlank
    @field:Email
    val email: String,
)

data class PasswordResetRequest(
    @field:NotBlank
    @field:Email
    val email: String,
)

data class CompletePasswordResetRequest(
    @field:NotBlank
    val token: String,
    @field:NotBlank
    @field:Size(min = 8, max = 200)
    val password: String,
)

data class AuthResponse(
    val accessToken: String,
    val tokenType: String,
    val expiresIn: Long,
    val user: UserResponse,
)
