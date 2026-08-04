package com.rally26.communication.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.rally26.communication.domain.DeliveryStatus
import com.rally26.communication.persistence.AnnouncementRepository
import com.rally26.notification.EmailMessage
import com.rally26.notification.EmailProvider
import com.rally26.notification.SmsMessage
import com.rally26.notification.SmsProvider
import com.rally26.outbox.application.OutboxEventHandler
import com.rally26.outbox.domain.OutboxEvent
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Instant

@Component
class AnnouncementDeliveryHandler(
    private val repository: AnnouncementRepository,
    private val emailProvider: EmailProvider,
    private val smsProvider: SmsProvider,
    private val objectMapper: ObjectMapper,
    private val clock: Clock,
) : OutboxEventHandler {
    override val eventType: String = "announcement.published"

    override fun handle(event: OutboxEvent) {
        val announcementId = objectMapper.readTree(event.payload).get("announcementId")?.asText()?.let(java.util.UUID::fromString)
            ?: event.aggregateId
        val announcement = repository.findById(announcementId, event.organizationId ?: error("Announcement event is missing organizationId."))
            ?: error("Announcement $announcementId no longer exists.")
        var failures = 0
        for (recipient in repository.listDeliveries(announcementId)) {
            if (recipient.emailStatus in setOf(DeliveryStatus.PENDING, DeliveryStatus.FAILED) && recipient.email != null) {
                try {
                    emailProvider.send(
                        EmailMessage(
                            to = recipient.email,
                            subject = announcement.title,
                            body = "Hi ${recipient.displayName},\n\n${announcement.body}\n\n— Rally26",
                            idempotencyKey = "announcement-${announcement.id}-${recipient.id}-email",
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
                    smsProvider.send(SmsMessage(recipient.phone, "Rally26: ${announcement.title}. ${announcement.body}".take(1200)))
                    repository.markSmsSent(recipient.id, Instant.now(clock))
                } catch (ex: Exception) {
                    repository.markSmsFailed(recipient.id, ex.message ?: ex.javaClass.simpleName)
                    failures++
                }
            }
        }
        if (failures > 0) error("$failures announcement delivery attempt(s) failed.")
    }
}
