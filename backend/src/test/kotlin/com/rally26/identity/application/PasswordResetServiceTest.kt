package com.rally26.identity.application

import com.rally26.common.error.NotFoundException
import com.rally26.common.error.ValidationException
import com.rally26.identity.domain.AppUser
import com.rally26.identity.domain.AppUserStatus
import com.rally26.identity.persistence.AppUserRepository
import com.rally26.identity.persistence.PasswordResetTokenRecord
import com.rally26.identity.persistence.PasswordResetTokenRepository
import com.rally26.outbox.application.OutboxWriter
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.springframework.security.crypto.password.PasswordEncoder
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertFailsWith

class PasswordResetServiceTest {

	private val appUserRepository = mockk<AppUserRepository>()
	private val passwordResetTokenRepository = mockk<PasswordResetTokenRepository>()
	private val outboxWriter = mockk<OutboxWriter>()
	private val passwordEncoder = mockk<PasswordEncoder>()
	private val service =
		PasswordResetService(appUserRepository, passwordResetTokenRepository, outboxWriter, passwordEncoder)

	@Test
	fun `request is ignored for missing account`() {
		every { appUserRepository.findByEmail("missing@example.com") } returns null

		service.request("missing@example.com")

		verify(exactly = 0) { passwordResetTokenRepository.replaceActiveToken(any(), any(), any()) }
		verify(exactly = 0) { outboxWriter.write(any(), any(), any(), any(), any()) }
	}

	@Test
	fun `request stores token and writes outbox event for active user`() {
		val user = activeUser("owner@example.com")
		every { appUserRepository.findByEmail(user.email) } returns user
		every { passwordResetTokenRepository.replaceActiveToken(user.id, any(), any()) } returns PasswordResetTokenRecord(
			id = UUID.randomUUID(),
			userId = user.id,
			tokenHash = "hash",
			expiresAt = Instant.now().plusSeconds(3600),
			consumedAt = null,
		)
		every { outboxWriter.write(any(), any(), any(), any(), any()) } just runs

		service.request(user.email)

		verify(exactly = 1) { passwordResetTokenRepository.replaceActiveToken(user.id, any(), any()) }
		verify(exactly = 1) { outboxWriter.write(any(), any(), any(), any(), any()) }
	}

	@Test
	fun `complete rejects invalid token`() {
		every { passwordResetTokenRepository.findByTokenHash(any()) } returns null

		assertFailsWith<NotFoundException> {
			service.complete("missing", "NewPassword123!")
		}
	}

	@Test
	fun `complete rejects expired token`() {
		every { passwordResetTokenRepository.findByTokenHash(any()) } returns PasswordResetTokenRecord(
			id = UUID.randomUUID(),
			userId = UUID.randomUUID(),
			tokenHash = "hash",
			expiresAt = Instant.now().minusSeconds(30),
			consumedAt = null,
		)

		assertFailsWith<ValidationException> {
			service.complete("expired", "NewPassword123!")
		}
	}

	@Test
	fun `complete updates password and consumes token`() {
		val user = activeUser("owner@example.com")
		val token = PasswordResetTokenRecord(
			id = UUID.randomUUID(),
			userId = user.id,
			tokenHash = "hash",
			expiresAt = Instant.now().plusSeconds(3600),
			consumedAt = null,
		)
		every { passwordResetTokenRepository.findByTokenHash(any()) } returns token
		every { appUserRepository.findById(user.id) } returns user
		every { passwordEncoder.encode("NewPassword123!") } returns "encoded"
		every { appUserRepository.updatePasswordHash(user.id, "encoded") } returns 1
		every { passwordResetTokenRepository.consume(token.id, any()) } returns 1

		service.complete("valid", "NewPassword123!")

		verify(exactly = 1) { appUserRepository.updatePasswordHash(user.id, "encoded") }
		verify(exactly = 1) { passwordResetTokenRepository.consume(token.id, any()) }
	}

	private fun activeUser(email: String) = AppUser(
		id = UUID.randomUUID(),
		email = email,
		displayName = "Owner",
		status = AppUserStatus.ACTIVE,
		passwordHash = "hash",
		createdAt = Instant.now(),
		updatedAt = Instant.now(),
	)
}

