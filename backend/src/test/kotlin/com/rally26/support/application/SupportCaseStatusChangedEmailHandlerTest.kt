package com.rally26.support.application

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.rally26.config.FrontendProperties
import com.rally26.config.ResendTemplateProperties
import com.rally26.notification.EmailMessage
import com.rally26.notification.EmailProvider
import com.rally26.outbox.domain.OutboxEvent
import com.rally26.outbox.domain.OutboxEventStatus
import com.rally26.support.domain.SupportCase
import com.rally26.support.domain.SupportCaseCategory
import com.rally26.support.domain.SupportCasePriority
import com.rally26.support.domain.SupportCaseStatus
import com.rally26.support.persistence.SupportCaseRepository
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

class SupportCaseStatusChangedEmailHandlerTest {
    private val repository = mockk<SupportCaseRepository>()
    private val emailProvider = mockk<EmailProvider>()
    private val objectMapper = jacksonObjectMapper()

    private fun handlerWith(resendTemplateProperties: ResendTemplateProperties) =
        SupportCaseStatusChangedEmailHandler(
            repository,
            emailProvider,
            resendTemplateProperties,
            FrontendProperties(baseUrl = "https://app.rally26.test"),
            objectMapper,
        )

    private val handler = handlerWith(ResendTemplateProperties())

    @Test
    fun `sends a plain-text status notice when no template is configured`() {
        val supportCase = supportCase(status = SupportCaseStatus.RESOLVED, resolution = "Reissued the invitation link.")
        every { repository.findById(supportCase.id) } returns supportCase
        val message = slot<EmailMessage>()
        every { emailProvider.send(capture(message)) } just runs

        handler.handle(event(supportCase.id))

        verify(exactly = 1) { emailProvider.send(any()) }
        assertEquals("adult@example.com", message.captured.to)
        assertTrue(message.captured.subject.contains("Resolved"))
        assertTrue(message.captured.body.contains("Reissued the invitation link."))
        assertNull(message.captured.template)
    }

    @Test
    fun `sends via the Resend template when a template id is configured`() {
        val supportCase = supportCase(status = SupportCaseStatus.CLOSED, resolution = "Duplicate of case #123.")
        every { repository.findById(supportCase.id) } returns supportCase
        val message = slot<EmailMessage>()
        every { emailProvider.send(capture(message)) } just runs

        handlerWith(ResendTemplateProperties(supportCaseUpdateId = "template-support-case-update")).handle(event(supportCase.id))

        val template = message.captured.template
        assertEquals("template-support-case-update", template?.id)
        assertEquals(supportCase.subject, template?.variables?.get("CASE_SUBJECT"))
        assertEquals("Closed", template?.variables?.get("STATUS_LABEL"))
        assertTrue((template?.variables?.get("RESOLUTION_HTML") as String).contains("Duplicate of case #123."))
    }

    @Test
    fun `missing case does not send email`() {
        val caseId = UUID.randomUUID()
        every { repository.findById(caseId) } returns null

        handler.handle(event(caseId))

        verify(exactly = 0) { emailProvider.send(any()) }
    }

    private fun event(caseId: UUID): OutboxEvent {
        val now = Instant.parse("2026-08-04T13:30:00Z")
        return OutboxEvent(
            id = UUID.randomUUID(),
            aggregateType = "SUPPORT_CASE",
            aggregateId = caseId,
            organizationId = null,
            eventType = "support.case.status_changed",
            schemaVersion = 1,
            payload = objectMapper.writeValueAsString(mapOf("caseId" to caseId.toString())),
            status = OutboxEventStatus.PROCESSING,
            attemptCount = 1,
            availableAt = now,
            processedAt = null,
            lastError = null,
            createdAt = now,
        )
    }

    private fun supportCase(
        status: SupportCaseStatus,
        resolution: String?,
    ): SupportCase {
        val now = Instant.parse("2026-08-04T13:30:00Z")
        return SupportCase(
            id = UUID.randomUUID(),
            idempotencyKey = "case-public-001",
            organizationId = null,
            organizationName = null,
            requesterUserId = null,
            requesterName = "Adult User",
            requesterEmail = "adult@example.com",
            category = SupportCaseCategory.TECHNICAL_PROBLEM,
            priority = SupportCasePriority.NORMAL,
            subject = "Page would not load",
            description = "The organization page remained blank after I signed in.",
            status = status,
            assignedPlatformUserId = null,
            assignedPlatformUserName = null,
            resolution = resolution,
            closedAt = if (status in setOf(SupportCaseStatus.RESOLVED, SupportCaseStatus.CLOSED)) now else null,
            createdAt = now,
            updatedAt = now,
        )
    }
}
