package com.rally26.identity.application

import com.rally26.common.error.NotFoundException
import com.rally26.common.error.ValidationException
import com.rally26.identity.domain.AppUserStatus
import com.rally26.identity.persistence.AppUserRepository
import com.rally26.identity.persistence.PasswordResetTokenRepository
import com.rally26.outbox.application.OutboxWriter
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Duration
import java.time.Instant
import java.util.Base64

private const val PASSWORD_RESET_TOKEN_VALIDITY_HOURS = 2L

@Service
class PasswordResetService(
    private val appUserRepository: AppUserRepository,
    private val passwordResetTokenRepository: PasswordResetTokenRepository,
    private val outboxWriter: OutboxWriter,
    private val passwordEncoder: PasswordEncoder,
) {
    @Transactional
    fun request(email: String) {
        val normalizedEmail = email.trim().lowercase()
        val user = appUserRepository.findByEmail(normalizedEmail) ?: return
        if (user.status != AppUserStatus.ACTIVE) return
        val rawToken = generateToken()
        passwordResetTokenRepository.replaceActiveToken(
            userId = user.id,
            tokenHash = sha256Hex(rawToken),
            expiresAt = Instant.now().plus(Duration.ofHours(PASSWORD_RESET_TOKEN_VALIDITY_HOURS)),
        )
        outboxWriter.write(
            aggregateType = "app_user",
            aggregateId = user.id,
            organizationId = null,
            eventType = "auth.password_reset_requested",
            payloadJson =
                """{"userId":"${user.id}","email":"${user.email}","resetToken":"$rawToken"}""",
        )
    }

    @Transactional
    fun complete(
        token: String,
        password: String,
    ) {
        val record =
            passwordResetTokenRepository.findByTokenHash(sha256Hex(token))
                ?: throw NotFoundException("PASSWORD_RESET_TOKEN_INVALID", "This password reset link is invalid.")
        if (record.consumedAt != null) {
            throw ValidationException("This password reset link has already been used.")
        }
        if (record.expiresAt.isBefore(Instant.now())) {
            throw ValidationException("This password reset link has expired.")
        }
        val user =
            appUserRepository.findById(record.userId)
                ?: throw NotFoundException("USER_NOT_FOUND", "The user could not be found.")
        if (user.status != AppUserStatus.ACTIVE) {
            throw ValidationException("This account is not eligible for password reset.")
        }
        val encodedPassword =
            passwordEncoder.encode(password)
                ?: throw ValidationException("Could not reset password at this time.")
        appUserRepository.updatePasswordHash(user.id, encodedPassword)
        passwordResetTokenRepository.consume(record.id)
    }

    private fun generateToken(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun sha256Hex(value: String): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
