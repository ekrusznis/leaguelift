package com.rally26.messaging.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.rally26.communication.domain.DeliveryStatus
import com.rally26.messaging.persistence.MessageRepository
import com.rally26.notification.EmailMessage
import com.rally26.notification.EmailProvider
import com.rally26.notification.SmsMessage
import com.rally26.notification.SmsProvider
import com.rally26.outbox.application.OutboxEventHandler
import com.rally26.outbox.domain.OutboxEvent
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Instant
import java.util.UUID

@Component
class ConversationMessageDeliveryHandler(
    private val repository: MessageRepository,
    private val emailProvider: EmailProvider,
    private val smsProvider: SmsProvider,
    private val objectMapper: ObjectMapper,
    private val clock: Clock,
) : OutboxEventHandler {
    override val eventType: String = "message.conversation_sent"

    override fun handle(event: OutboxEvent) {
        val payload = objectMapper.readTree(event.payload)
        val messageId = payload.get("messageId")?.asText()?.let(UUID::fromString) ?: event.aggregateId
        val organizationId = event.organizationId ?: error("Conversation message event is missing organizationId.")
        val message = repository.findMessageById(messageId, organizationId) ?: error("Message $messageId no longer exists.")
        val thread =
            repository.findThreadById(message.threadId, organizationId) ?: error("Message thread ${message.threadId} no longer exists.")
        var failures = 0
        for (recipient in repository.listDeliveries(messageId)) {
            if (recipient.emailStatus in setOf(DeliveryStatus.PENDING, DeliveryStatus.FAILED) && recipient.email != null) {
                try {
                    emailProvider.send(
                        EmailMessage(
                            to = recipient.email,
                            subject = "New reply: ${thread.title}",
                            body =
                                "Hi ${recipient.displayName},\n\n${message.body}\n\nOpen Rally26 Messages to view and reply." +
                                    "\n\n— Rally26",
                            idempotencyKey = "conversation-${message.id}-${recipient.id}-email",
                        ),
                    )
                    repository.markEmailSent(recipient.id, Instant.now(clock))
                } catch (ex: Exception) {
                    repository.markEmailFailed(recipient.id, ex.message ?: ex.javaClass.simpleName)
                    failures++
                }
            }
            if (recipient.smsStatus in setOf(DeliveryStatus.PENDING, DeliveryStatus.FAILED) && recipient.phone != null) {
                try {
                    smsProvider.send(SmsMessage(recipient.phone, "Rally26 message: ${thread.title}. ${message.body}".take(1200)))
                    repository.markSmsSent(recipient.id, Instant.now(clock))
                } catch (ex: Exception) {
                    repository.markSmsFailed(recipient.id, ex.message ?: ex.javaClass.simpleName)
                    failures++
                }
            }
        }
        if (failures > 0) error("$failures conversation message delivery attempt(s) failed.")
    }
}
