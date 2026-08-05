package com.rally26.support.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.rally26.notification.EmailMessage
import com.rally26.notification.EmailProvider
import com.rally26.outbox.application.OutboxEventHandler
import com.rally26.outbox.domain.OutboxEvent
import com.rally26.support.persistence.SupportCaseRepository
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Sends a platform admin's ad-hoc message (SupportCaseService.sendPlatformEmail) to the
 * requester via the primary EmailProvider. Distinct from
 * SupportCaseStatusChangedEmailHandler's automatic, templated status notice -- this is
 * free-form admin-authored content, so no Resend template ref is used, matching
 * support.case.created's own plain-body precedent for requester-facing content.
 */
@Component
class SupportCaseAdminEmailHandler(
    private val repository: SupportCaseRepository,
    private val emailProvider: EmailProvider,
    private val objectMapper: ObjectMapper,
) : OutboxEventHandler {
    override val eventType = "support.case.admin_email"

    override fun handle(event: OutboxEvent) {
        val payload = objectMapper.readTree(event.payload)
        val caseId = UUID.fromString(payload.get("caseId").asText())
        val subject = payload.get("subject").asText()
        val body = payload.get("body").asText()
        val supportCase = repository.findById(caseId) ?: return

        emailProvider.send(
            EmailMessage(
                to = supportCase.requesterEmail,
                idempotencyKey = "support-case-admin-email-${event.id}",
                subject = subject,
                body = "$body\n\n— Rally26 Support",
            ),
        )
    }
}
