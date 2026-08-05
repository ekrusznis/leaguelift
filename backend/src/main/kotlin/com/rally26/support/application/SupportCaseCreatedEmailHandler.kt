package com.rally26.support.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.rally26.config.SupportProperties
import com.rally26.notification.EmailMessage
import com.rally26.notification.MustacheTemplateRenderer
import com.rally26.notification.infra.SmtpEmailProvider
import com.rally26.outbox.application.OutboxEventHandler
import com.rally26.outbox.domain.OutboxEvent
import com.rally26.support.persistence.SupportCaseRepository
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Sends via [SmtpEmailProvider] (Google Workspace SMTP), not the primary
 * [com.rally26.notification.EmailProvider]/Resend (ADR-059) — this covers both the
 * anonymous landing-page Contact Us submission and an authenticated Help Center
 * ticket, since [com.rally26.support.application.SupportCaseService] persists both to
 * the same `support_case` row and this handler doesn't distinguish between them. The
 * requester is the primary recipient (a receipt of what they just sent); the support
 * inbox is CC'd so the team sees and can reply-all to it. Contrast with
 * [SupportCaseStatusChangedEmailHandler], which *does* go through Resend — that's an
 * automated status notice, not a person's own correspondence.
 */
@Component
class SupportCaseCreatedEmailHandler(
    private val repository: SupportCaseRepository,
    private val smtpEmailProvider: SmtpEmailProvider,
    private val templateRenderer: MustacheTemplateRenderer,
    private val supportProperties: SupportProperties,
    private val objectMapper: ObjectMapper,
) : OutboxEventHandler {
    override val eventType = "support.case.created"

    override fun handle(event: OutboxEvent) {
        val caseId = UUID.fromString(objectMapper.readTree(event.payload).get("caseId").asText())
        val supportCase = repository.findById(caseId) ?: return
        val body =
            templateRenderer.render(
                "mail-templates/support-case-created.mustache",
                mapOf(
                    "requesterName" to supportCase.requesterName,
                    "caseId" to supportCase.id.toString(),
                    "category" to supportCase.category.name.replace('_', ' '),
                    "subject" to supportCase.subject,
                    "description" to supportCase.description,
                ),
            )
        smtpEmailProvider.send(
            EmailMessage(
                to = supportCase.requesterEmail,
                cc = listOf(supportProperties.inboxEmail),
                replyTo = supportProperties.inboxEmail,
                idempotencyKey = "support-case-${supportCase.id}",
                subject = "Rally26 support case ${supportCase.id.toString().take(8)}: ${supportCase.subject}",
                body = body,
            ),
        )
    }
}
