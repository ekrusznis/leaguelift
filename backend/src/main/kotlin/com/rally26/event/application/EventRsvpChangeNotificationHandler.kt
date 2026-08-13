package com.rally26.event.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.rally26.config.FrontendProperties
import com.rally26.config.ResendTemplateProperties
import com.rally26.notification.EmailMessage
import com.rally26.notification.EmailProvider
import com.rally26.notification.EmailTemplateRef
import com.rally26.outbox.application.OutboxEventHandler
import com.rally26.outbox.domain.OutboxEvent
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

private val log = LoggerFactory.getLogger(EventRsvpChangeNotificationHandler::class.java)

/**
 * Notifies team staff (not the submitting family — they already know what they just
 * did) that an RSVP changed (Phase 10 slice 4, ADR-029, founder decision). Email only —
 * an RSVP change isn't judged SMS-worthy the way a schedule change is.
 */
@Component
class EventRsvpChangeNotificationHandler(
    private val emailProvider: EmailProvider,
    private val resendTemplateProperties: ResendTemplateProperties,
    private val frontendProperties: FrontendProperties,
    private val objectMapper: ObjectMapper,
) : OutboxEventHandler {
    override val eventType: String = "event.rsvp_changed"

    override fun handle(event: OutboxEvent) {
        val payload = objectMapper.readValue(event.payload, RsvpChangeNotificationPayload::class.java)
        if (payload.staffEmails.isEmpty()) {
            log.info(
                "RSVP change on event {} (\"{}\") has no team staff to notify — nothing to send.",
                event.aggregateId,
                payload.displayTitle,
            )
            return
        }
        val previous = payload.previousResponse ?: "no response"
        val actionUrl =
            event.organizationId?.let { "${frontendProperties.baseUrl}/app/organizations/$it/events/${payload.eventId}" } ?: frontendProperties.baseUrl
        for (email in payload.staffEmails) {
            emailProvider.send(
                EmailMessage(
                    to = email,
                    subject = "RSVP update: ${payload.displayTitle}",
                    body =
                        "${payload.participantName}'s RSVP for \"${payload.displayTitle}\" changed from $previous " +
                            "to ${payload.newResponse}.\n\n— Rally26",
                    template =
                        resendTemplateProperties.notificationId.takeIf { it.isNotBlank() }?.let { templateId ->
                            EmailTemplateRef(
                                id = templateId,
                                variables =
                                    mapOf(
                                        "NOTIFICATION_TITLE" to "RSVP update: ${payload.displayTitle}",
                                        "NOTIFICATION_DETAILS" to "${payload.participantName}'s RSVP changed from $previous to ${payload.newResponse}.",
                                        "ACTION_URL" to actionUrl,
                                    ),
                            )
                        },
                ),
            )
        }
    }
}
