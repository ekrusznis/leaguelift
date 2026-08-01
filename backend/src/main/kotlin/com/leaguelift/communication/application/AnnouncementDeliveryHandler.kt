package com.leaguelift.communication.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.leaguelift.communication.domain.DeliveryStatus
import com.leaguelift.communication.persistence.AnnouncementRepository
import com.leaguelift.notification.EmailMessage
import com.leaguelift.notification.EmailProvider
import com.leaguelift.notification.SmsMessage
import com.leaguelift.notification.SmsProvider
import com.leaguelift.outbox.application.OutboxEventHandler
import com.leaguelift.outbox.domain.OutboxEvent
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
                            body = "Hi ${recipient.displayName},\n\n${announcement.body}\n\n— LeagueLift",
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
                    smsProvider.send(SmsMessage(recipient.phone, "LeagueLift: ${announcement.title}. ${announcement.body}".take(1200)))
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
