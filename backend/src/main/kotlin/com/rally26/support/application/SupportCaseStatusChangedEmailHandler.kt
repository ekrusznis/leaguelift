package com.rally26.support.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.rally26.config.FrontendProperties
import com.rally26.config.ResendTemplateProperties
import com.rally26.notification.EmailMessage
import com.rally26.notification.EmailProvider
import com.rally26.notification.EmailTemplateRef
import com.rally26.outbox.application.OutboxEventHandler
import com.rally26.outbox.domain.OutboxEvent
import com.rally26.support.domain.SupportCaseStatus
import com.rally26.support.persistence.SupportCaseRepository
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Sends via the primary [EmailProvider] (Resend, ADR-022/059) — an automated status
 * notice, unlike [SupportCaseCreatedEmailHandler]'s SMTP-based receipt of the
 * requester's own message. Fires only on a real status transition (see
 * [SupportCaseService.updatePlatform]'s `existing.status != updated.status` guard),
 * never on a priority/assignment-only edit.
 */
@Component
class SupportCaseStatusChangedEmailHandler(
    private val repository: SupportCaseRepository,
    private val emailProvider: EmailProvider,
    private val resendTemplateProperties: ResendTemplateProperties,
    private val frontendProperties: FrontendProperties,
    private val objectMapper: ObjectMapper,
) : OutboxEventHandler {
    override val eventType = "support.case.status_changed"

    override fun handle(event: OutboxEvent) {
        val caseId = UUID.fromString(objectMapper.readTree(event.payload).get("caseId").asText())
        val supportCase = repository.findById(caseId) ?: return
        val statusLabel = statusLabel(supportCase.status)
        val caseUrl = "${frontendProperties.baseUrl}/app/help/support"
        val resolutionLine = supportCase.resolution?.takeIf { it.isNotBlank() }?.let { "\n\n$it" } ?: ""
        val resolutionHtml =
            supportCase.resolution?.takeIf { it.isNotBlank() }?.let { resolution ->
                val escaped = resolution.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                "<p style=\"margin-top:16px; color:#0B1F33;\">$escaped</p>"
            } ?: ""

        emailProvider.send(
            EmailMessage(
                to = supportCase.requesterEmail,
                idempotencyKey = "support-case-status-${supportCase.id}-${supportCase.status.name}",
                subject = "Rally26 support case ${supportCase.id.toString().take(8)} is now $statusLabel",
                body =
                    "Your Rally26 support case \"${supportCase.subject}\" is now $statusLabel.$resolutionLine\n\n" +
                        "View your cases: $caseUrl\n\n— Rally26 Support",
                template =
                    resendTemplateProperties.supportCaseUpdateId.takeIf { it.isNotBlank() }?.let { templateId ->
                        EmailTemplateRef(
                            id = templateId,
                            variables =
                                mapOf(
                                    "CASE_SUBJECT" to supportCase.subject,
                                    "STATUS_LABEL" to statusLabel,
                                    "RESOLUTION_HTML" to resolutionHtml,
                                    "CASE_URL" to caseUrl,
                                ),
                        )
                    },
            ),
        )
    }

    private fun statusLabel(status: SupportCaseStatus): String =
        when (status) {
            SupportCaseStatus.OPEN -> "Open"
            SupportCaseStatus.IN_PROGRESS -> "In Progress"
            SupportCaseStatus.WAITING_ON_CUSTOMER -> "Waiting on You"
            SupportCaseStatus.RESOLVED -> "Resolved"
            SupportCaseStatus.CLOSED -> "Closed"
        }
}
