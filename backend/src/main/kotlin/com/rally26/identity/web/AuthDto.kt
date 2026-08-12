package com.rally26.identity.web

import jakarta.validation.constraints.AssertTrue
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
    /**
     * Set when registration was reached from an invitation-accept link
     * (`/auth/invitation?token=...`) by someone with no existing account yet. Carried
     * through to the verification email so its link can redirect back to accepting the
     * invitation after verifying, instead of stranding a real invitation as permanently
     * unclaimed — see [com.rally26.identity.application.EmailVerificationService].
     *
     * Kept before the new legal-acceptance booleans so existing positional construction
     * in tests/callers remains source-compatible.
     */
    @field:Size(max = 500)
    val invitationToken: String? = null,
    @field:AssertTrue(message = "You must accept the Terms of Service and Privacy Policy.")
    val agreeToTerms: Boolean = false,
    @field:AssertTrue(message = "You must confirm that you are at least 18 years old.")
    val confirmAdult: Boolean = false,
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

/** Phase 37 — the mobile app's native Google/Apple SDK hands back this ID token; OAuthSignInService verifies it server-side, never trusting it unverified. */
data class OAuthSignInRequest(
    @field:NotBlank
    val idToken: String,
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
