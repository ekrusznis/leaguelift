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

class OwnerEmailVerificationHandlerTest {

	private val appUserRepository = mockk<AppUserRepository>()
	private val emailProvider = mockk<EmailProvider>()
	private val handler = OwnerEmailVerificationHandler(
		appUserRepository,
		emailProvider,
		FrontendProperties(baseUrl = "https://app.rally26.test"),
		ObjectMapper(),
	)

	@Test
	fun `sends verification email for pending user`() {
		val userId = UUID.randomUUID()
		every { appUserRepository.findById(userId) } returns pendingUser(userId)
		val messageSlot = slot<EmailMessage>()
		every { emailProvider.send(capture(messageSlot)) } just runs

		handler.handle(eventFor(userId, "abc-token"))

		verify(exactly = 1) { emailProvider.send(any()) }
		assertTrue(messageSlot.captured.body.contains("/auth/verify-email?token=abc-token"))
	}

	@Test
	fun `does nothing for non-pending user`() {
		val userId = UUID.randomUUID()
		every { appUserRepository.findById(userId) } returns pendingUser(userId).copy(status = AppUserStatus.ACTIVE)

		handler.handle(eventFor(userId, "abc-token"))

		verify(exactly = 0) { emailProvider.send(any()) }
	}

	private fun eventFor(userId: UUID, token: String): OutboxEvent {
		val now = Instant.now()
		return OutboxEvent(
			id = UUID.randomUUID(),
			aggregateType = "app_user",
			aggregateId = userId,
			organizationId = null,
			eventType = "auth.owner_verification_requested",
			schemaVersion = 1,
			payload = """{"userId":"$userId","email":"owner@example.com","verificationToken":"$token"}""",
			status = OutboxEventStatus.PROCESSING,
			attemptCount = 1,
			availableAt = now,
			processedAt = null,
			lastError = null,
			createdAt = now,
		)
	}

	private fun pendingUser(id: UUID) = AppUser(
		id = id,
		email = "owner@example.com",
		displayName = "Owner",
		status = AppUserStatus.PENDING_EMAIL_VERIFICATION,
		passwordHash = "hash",
		createdAt = Instant.now(),
		updatedAt = Instant.now(),
	)
}

