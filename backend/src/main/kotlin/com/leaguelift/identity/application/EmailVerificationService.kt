package com.leaguelift.identity.application

import com.leaguelift.common.error.NotFoundException
import com.leaguelift.common.error.ValidationException
import com.leaguelift.identity.domain.AppUserStatus
import com.leaguelift.identity.persistence.AppUserRepository
import com.leaguelift.identity.persistence.EmailVerificationTokenRepository
import com.leaguelift.outbox.application.OutboxWriter
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
		val user = appUserRepository.findById(userId)
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
		val record = emailVerificationTokenRepository.findByTokenHash(sha256Hex(token))
			?: throw NotFoundException("EMAIL_VERIFICATION_TOKEN_INVALID", "This verification link is invalid.")
		if (record.consumedAt != null) {
			throw ValidationException("This verification link has already been used.")
		}
		if (record.expiresAt.isBefore(Instant.now())) {
			throw ValidationException("This verification link has expired.")
		}
		appUserRepository.markActive(record.userId)
		emailVerificationTokenRepository.consume(record.id)
	}

	private fun generateToken(): String {
		val bytes = ByteArray(32)
		SecureRandom().nextBytes(bytes)
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
	}

	private fun sha256Hex(value: String): String =
		MessageDigest.getInstance("SHA-256")
			.digest(value.toByteArray(Charsets.UTF_8))
			.joinToString("") { "%02x".format(it) }
}

