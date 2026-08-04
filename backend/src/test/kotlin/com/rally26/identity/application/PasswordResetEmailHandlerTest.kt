package com.rally26.identity.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.rally26.config.FrontendProperties
import com.rally26.identity.domain.AppUser
import com.rally26.identity.domain.AppUserStatus
import com.rally26.identity.persistence.AppUserRepository
import com.rally26.notification.EmailMessage
import com.rally26.notification.EmailProvider
import com.rally26.outbox.domain.OutboxEvent
import com.rally26.outbox.domain.OutboxEventStatus
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertTrue

class PasswordResetEmailHandlerTest {

	private val appUserRepository = mockk<AppUserRepository>()
	private val emailProvider = mockk<EmailProvider>()
	private val handler = PasswordResetEmailHandler(
		appUserRepository,
		emailProvider,
		FrontendProperties(baseUrl = "https://app.rally26.test"),
		ObjectMapper(),
	)

	@Test
	fun `sends password reset email for active user`() {
		val userId = UUID.randomUUID()
		every { appUserRepository.findById(userId) } returns activeUser(userId)
		val messageSlot = slot<EmailMessage>()
		every { emailProvider.send(capture(messageSlot)) } just runs

		handler.handle(eventFor(userId, "reset-token"))

		verify(exactly = 1) { emailProvider.send(any()) }
		assertTrue(messageSlot.captured.body.contains("/auth/reset-password?token=reset-token"))
	}

	@Test
	fun `does nothing for non-active user`() {
		val userId = UUID.randomUUID()
		every { appUserRepository.findById(userId) } returns activeUser(userId).copy(status = AppUserStatus.SUSPENDED)

		handler.handle(eventFor(userId, "reset-token"))

		verify(exactly = 0) { emailProvider.send(any()) }
	}

	private fun eventFor(userId: UUID, token: String): OutboxEvent {
		val now = Instant.now()
		return OutboxEvent(
			id = UUID.randomUUID(),
			aggregateType = "app_user",
			aggregateId = userId,
			organizationId = null,
			eventType = "auth.password_reset_requested",
			schemaVersion = 1,
			payload = """{"userId":"$userId","email":"owner@example.com","resetToken":"$token"}""",
			status = OutboxEventStatus.PROCESSING,
			attemptCount = 1,
			availableAt = now,
			processedAt = null,
			lastError = null,
			createdAt = now,
		)
	}

	private fun activeUser(id: UUID) = AppUser(
		id = id,
		email = "owner@example.com",
		displayName = "Owner",
		status = AppUserStatus.ACTIVE,
		passwordHash = "hash",
		createdAt = Instant.now(),
		updatedAt = Instant.now(),
	)
}

