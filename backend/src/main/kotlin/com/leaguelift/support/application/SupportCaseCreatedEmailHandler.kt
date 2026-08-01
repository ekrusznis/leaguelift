package com.leaguelift.support.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.leaguelift.config.SupportProperties
import com.leaguelift.notification.EmailMessage
import com.leaguelift.notification.EmailProvider
import com.leaguelift.outbox.application.OutboxEventHandler
import com.leaguelift.outbox.domain.OutboxEvent
import com.leaguelift.support.persistence.SupportCaseRepository
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
                subject = "LeagueLift support case ${supportCase.id.toString().take(8)}: ${supportCase.subject}",
                body = """
                    Hi ${supportCase.requesterName},

                    We received your LeagueLift support request.

                    Case: ${supportCase.id}
                    Category: ${supportCase.category.name.replace('_', ' ')}
                    Subject: ${supportCase.subject}

                    Reply to this email if you need to add context. This first support release is ticket/email support, not live chat, and does not promise a specific response time.

                    — LeagueLift Support
                """.trimIndent(),
            ),
        )
    }
}
