package com.rally26.identity.application

import com.rally26.common.error.ConflictException
import com.rally26.common.error.NotFoundException
import com.rally26.common.error.ValidationException
import com.rally26.identity.domain.AppUserStatus
import com.rally26.identity.persistence.AppUserRepository
import com.rally26.identity.persistence.EmailVerificationTokenRepository
import com.rally26.outbox.application.OutboxWriter
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.UUID

private const val EMAIL_VERIFICATION_VALIDITY_HOURS = 24L

@Service
class EmailVerificationService(
    private val emailVerificationTokenRepository: EmailVerificationTokenRepository,
    private val appUserRepository: AppUserRepository,
    private val outboxWriter: OutboxWriter,
) {
    data class IssuedVerification(
        val userId: UUID,
        val email: String,
        val rawToken: String,
    )

    @Transactional
    fun issueForUser(userId: UUID): IssuedVerification {
        val user =
            appUserRepository.findById(userId)
                ?: throw NotFoundException("USER_NOT_FOUND", "The user could not be found.")
        if (user.status != AppUserStatus.PENDING_EMAIL_VERIFICATION) {
            throw ValidationException("Only pending accounts can request email verification.")
        }
        val rawToken = generateToken()
        emailVerificationTokenRepository.replaceActiveToken(
            userId = user.id,
            tokenHash = sha256Hex(rawToken),
            expiresAt = Instant.now().plus(Duration.ofHours(EMAIL_VERIFICATION_VALIDITY_HOURS)),
        )
        return IssuedVerification(user.id, user.email, rawToken)
    }

    @Transactional
    fun resend(email: String) {
        val normalizedEmail = email.trim().lowercase()
        val user = appUserRepository.findByEmail(normalizedEmail) ?: return
        if (user.status != AppUserStatus.PENDING_EMAIL_VERIFICATION) return
        enqueueVerificationEmail(issueForUser(user.id))
    }

    fun enqueueVerificationEmail(issued: IssuedVerification) {
        outboxWriter.write(
            aggregateType = "app_user",
            aggregateId = issued.userId,
            organizationId = null,
            eventType = "auth.owner_verification_requested",
            payloadJson =
                """{"userId":"${issued.userId}","email":"${issued.email}","verificationToken":"${issued.rawToken}"}""",
        )
    }

    @Transactional
    fun verify(token: String) {
        val record =
            emailVerificationTokenRepository.findByTokenHash(sha256Hex(token))
                ?: throw NotFoundException("EMAIL_VERIFICATION_TOKEN_INVALID", "This verification link is invalid.")
        if (record.consumedAt != null) {
            // Distinct code (not VALIDATION_FAILED) so the frontend can tell "someone already
            // used this link" apart from "this link is genuinely bad/expired" — the former is
            // almost always the *same* user double-clicking Verify, or a resubmit after a slow
            // response whose first attempt actually succeeded, so the UI should treat it as a
            // success rather than a scary error (see VerifyEmailPage.tsx).
            throw ConflictException("EMAIL_VERIFICATION_ALREADY_USED", "This verification link has already been used.")
        }
        if (record.expiresAt.isBefore(Instant.now())) {
            throw ValidationException("This verification link has expired.")
        }
        appUserRepository.markActive(record.userId)
        // consume() reports how many rows it actually flipped from unconsumed -> consumed.
        // Guards a race where two concurrent requests both pass the consumedAt == null check
        // above before either commits — without checking this, the losing request would still
        // return success even though its UPDATE affected 0 rows.
        val consumedRows = emailVerificationTokenRepository.consume(record.id)
        if (consumedRows == 0) {
            throw ConflictException("EMAIL_VERIFICATION_ALREADY_USED", "This verification link has already been used.")
        }
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
