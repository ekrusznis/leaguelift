package com.rally26.identity.application

import com.rally26.common.error.NotFoundException
import com.rally26.common.error.ValidationException
import com.rally26.identity.domain.AppUser
import com.rally26.identity.domain.AppUserStatus
import com.rally26.identity.persistence.AppUserRepository
import com.rally26.identity.persistence.EmailVerificationTokenRecord
import com.rally26.identity.persistence.EmailVerificationTokenRepository
import com.rally26.outbox.application.OutboxWriter
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import java.time.Instant
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class EmailVerificationServiceTest {

	private val tokenRepository = mockk<EmailVerificationTokenRepository>()
	private val appUserRepository = mockk<AppUserRepository>()
	private val outboxWriter = mockk<OutboxWriter>()
	private val service = EmailVerificationService(tokenRepository, appUserRepository, outboxWriter)

	@Test
	fun `issueForUser creates a new token for pending user`() {
		val user = pendingUser("owner@example.com")
		every { appUserRepository.findById(user.id) } returns user
		every { tokenRepository.replaceActiveToken(user.id, any(), any()) } returns EmailVerificationTokenRecord(
			id = UUID.randomUUID(),
			userId = user.id,
			tokenHash = "hash",
			expiresAt = Instant.now().plusSeconds(3600),
			consumedAt = null,
		)

		val issued = service.issueForUser(user.id)

		assertEquals(user.id, issued.userId)
		assertEquals("owner@example.com", issued.email)
		assertEquals(43, issued.rawToken.length)
	}

	@Test
	fun `enqueueVerificationEmail writes outbox event`() {
		val issued = EmailVerificationService.IssuedVerification(UUID.randomUUID(), "owner@example.com", "token")
		every { outboxWriter.write(any(), any(), any(), any(), any()) } just runs

		service.enqueueVerificationEmail(issued)

		verify(exactly = 1) {
			outboxWriter.write("app_user", issued.userId, null, "auth.owner_verification_requested", any())
		}
	}

	@Test
	fun `resend is ignored when account is missing`() {
		every { appUserRepository.findByEmail("missing@example.com") } returns null

		service.resend("missing@example.com")

		verify(exactly = 0) { tokenRepository.replaceActiveToken(any(), any(), any()) }
		verify(exactly = 0) { outboxWriter.write(any(), any(), any(), any(), any()) }
	}

	@Test
	fun `resend issues fresh token and queues email for pending user`() {
		val user = pendingUser("owner@example.com")
		every { appUserRepository.findByEmail("owner@example.com") } returns user
		every { appUserRepository.findById(user.id) } returns user
		every { tokenRepository.replaceActiveToken(user.id, any(), any()) } returns EmailVerificationTokenRecord(
			id = UUID.randomUUID(),
			userId = user.id,
			tokenHash = "hash",
			expiresAt = Instant.now().plusSeconds(3600),
			consumedAt = null,
		)
		every { outboxWriter.write(any(), any(), any(), any(), any()) } just runs

		service.resend("owner@example.com")

		verify(exactly = 1) { tokenRepository.replaceActiveToken(user.id, any(), any()) }
		verify(exactly = 1) { outboxWriter.write(any(), any(), any(), any(), any()) }
	}

	@Test
	fun `verify rejects invalid token`() {
		every { tokenRepository.findByTokenHash(any()) } returns null
		assertFailsWith<NotFoundException> {
			service.verify("missing")
		}
	}

	@Test
	fun `verify rejects expired token`() {
		val record = EmailVerificationTokenRecord(
			id = UUID.randomUUID(),
			userId = UUID.randomUUID(),
			tokenHash = "hash",
			expiresAt = Instant.now().minusSeconds(60),
			consumedAt = null,
		)
		every { tokenRepository.findByTokenHash(any()) } returns record

		assertFailsWith<ValidationException> {
			service.verify("expired")
		}
	}

	@Test
	fun `verify marks user active and consumes token`() {
		val record = EmailVerificationTokenRecord(
			id = UUID.randomUUID(),
			userId = UUID.randomUUID(),
			tokenHash = "hash",
			expiresAt = Instant.now().plusSeconds(3600),
			consumedAt = null,
		)
		every { tokenRepository.findByTokenHash(any()) } returns record
		every { appUserRepository.markActive(record.userId) } returns 1
		every { tokenRepository.consume(record.id, any()) } returns 1

		service.verify("valid")

		verify(exactly = 1) { appUserRepository.markActive(record.userId) }
		verify(exactly = 1) { tokenRepository.consume(record.id, any()) }
	}

	private fun pendingUser(email: String) = AppUser(
		id = UUID.randomUUID(),
		email = email,
		displayName = "Owner",
		status = AppUserStatus.PENDING_EMAIL_VERIFICATION,
		passwordHash = "hash",
		createdAt = Instant.now(),
		updatedAt = Instant.now(),
	)
}

