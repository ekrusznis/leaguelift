package com.rally26.identity.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.rally26.config.FrontendProperties
import com.rally26.config.ResendTemplateProperties
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
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OwnerEmailVerificationHandlerTest {
    private val appUserRepository = mockk<AppUserRepository>()
    private val emailProvider = mockk<EmailProvider>()

    private fun handlerWith(resendTemplateProperties: ResendTemplateProperties) =
        OwnerEmailVerificationHandler(
            appUserRepository,
            emailProvider,
            FrontendProperties(baseUrl = "https://app.rally26.test"),
            resendTemplateProperties,
            ObjectMapper(),
        )

    private val handler = handlerWith(ResendTemplateProperties())

    @Test
    fun `sends verification email for pending owner with onboarding return`() {
        val userId = UUID.randomUUID()
        every { appUserRepository.findById(userId) } returns pendingUser(userId)
        val messageSlot = slot<EmailMessage>()
        every { emailProvider.send(capture(messageSlot)) } just runs

        handler.handle(eventFor(userId, "abc-token"))

        verify(exactly = 1) { emailProvider.send(any()) }
        assertTrue(messageSlot.captured.body.contains("/auth/verify-email?token=abc-token"))
        assertTrue(messageSlot.captured.body.contains("next=%2Fapp%2Fonboarding%2Forganization"))
        assertNull(messageSlot.captured.template)
    }

    @Test
    fun `sends via the Resend template with onboarding return`() {
        val userId = UUID.randomUUID()
        every { appUserRepository.findById(userId) } returns pendingUser(userId)
        val messageSlot = slot<EmailMessage>()
        every { emailProvider.send(capture(messageSlot)) } just runs

        handlerWith(ResendTemplateProperties(verifyEmailId = "template-verify-email")).handle(eventFor(userId, "abc-token"))

        val template = messageSlot.captured.template
        assertEquals("template-verify-email", template?.id)
        assertEquals(
            "https://app.rally26.test/auth/verify-email?token=abc-token&next=%2Fapp%2Fonboarding%2Forganization",
            template?.variables?.get("VERIFY_URL"),
        )
    }

    @Test
    fun `invitation verification still returns to invitation`() {
        val userId = UUID.randomUUID()
        every { appUserRepository.findById(userId) } returns pendingUser(userId)
        val messageSlot = slot<EmailMessage>()
        every { emailProvider.send(capture(messageSlot)) } just runs

        handler.handle(eventFor(userId, "abc-token", invitationToken = "invite-token"))

        assertTrue(messageSlot.captured.body.contains("next=%2Fauth%2Finvitation%3Ftoken%3Dinvite-token"))
    }

    @Test
    fun `does nothing for non-pending user`() {
        val userId = UUID.randomUUID()
        every { appUserRepository.findById(userId) } returns pendingUser(userId).copy(status = AppUserStatus.ACTIVE)

        handler.handle(eventFor(userId, "abc-token"))

        verify(exactly = 0) { emailProvider.send(any()) }
    }

    private fun eventFor(
        userId: UUID,
        token: String,
        invitationToken: String? = null,
    ): OutboxEvent {
        val now = Instant.now()
        val invitationJson = invitationToken?.let { ",\"invitationToken\":\"$it\"" }.orEmpty()
        return OutboxEvent(
            id = UUID.randomUUID(),
            aggregateType = "app_user",
            aggregateId = userId,
            organizationId = null,
            eventType = "auth.owner_verification_requested",
            schemaVersion = 1,
            payload = """{"userId":"$userId","email":"owner@example.com","verificationToken":"$token"$invitationJson}""",
            status = OutboxEventStatus.PROCESSING,
            attemptCount = 1,
            availableAt = now,
            processedAt = null,
            lastError = null,
            createdAt = now,
        )
    }

    private fun pendingUser(id: UUID) =
        AppUser(
            id = id,
            email = "owner@example.com",
            displayName = "Owner",
            status = AppUserStatus.PENDING_EMAIL_VERIFICATION,
            passwordHash = "hash",
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
        )
}
