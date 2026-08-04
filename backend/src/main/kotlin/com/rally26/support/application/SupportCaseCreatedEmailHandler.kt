package com.rally26.support.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.rally26.config.SupportProperties
import com.rally26.notification.EmailMessage
import com.rally26.notification.EmailProvider
import com.rally26.outbox.application.OutboxEventHandler
import com.rally26.outbox.domain.OutboxEvent
import com.rally26.support.persistence.SupportCaseRepository
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class SupportCaseCreatedEmailHandler(
    private val repository: SupportCaseRepository,
    private val emailProvider: EmailProvider,
    private val supportProperties: SupportProperties,
    private val objectMapper: ObjectMapper,
) : OutboxEventHandler {
    override val eventType = "support.case.created"

    override fun handle(event: OutboxEvent) {
        val caseId = UUID.fromString(objectMapper.readTree(event.payload).get("caseId").asText())
        val supportCase = repository.findById(caseId) ?: return
        emailProvider.send(
            EmailMessage(
                to = supportCase.requesterEmail,
                cc = listOf(supportProperties.inboxEmail),
                replyTo = supportProperties.inboxEmail,
                idempotencyKey = "support-case-${supportCase.id}",
                subject = "Rally26 support case ${supportCase.id.toString().take(8)}: ${supportCase.subject}",
                body = """
                    Hi ${supportCase.requesterName},

                    We received your Rally26 support request.

                    Case: ${supportCase.id}
                    Category: ${supportCase.category.name.replace('_', ' ')}
                    Subject: ${supportCase.subject}

                    Reply to this email if you need to add context. This first support release is ticket/email support, not live chat, and does not promise a specific response time.

                    — Rally26 Support
                """.trimIndent(),
            ),
        )
    }
}
