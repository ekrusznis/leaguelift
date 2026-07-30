package com.leaguelift.event.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.leaguelift.notification.EmailMessage
import com.leaguelift.notification.EmailProvider
import com.leaguelift.notification.SmsMessage
import com.leaguelift.notification.SmsProvider
import com.leaguelift.outbox.application.OutboxEventHandler
import com.leaguelift.outbox.domain.OutboxEvent
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

private val log = LoggerFactory.getLogger(EventChangeNotificationHandler::class.java)

/**
 * Sends one event-change email/SMS per [EventChangeNotificationPayload] (Phase 10 slice
 * 4, ADR-029). A single class parameterized by [eventType], not nine near-identical
 * `@Component` classes: every one of the notification event types DESIGN-DOC.md section
 * 14.1A lists (`event.created`, `event.time_changed`, `event.location_changed`,
 * `event.area_assigned`, `event.arrival_time_changed`, `event.postponed`,
 * `event.cancelled`, `tournament.event_added`, `tournament.event_updated`) shares the
 * exact same "tell these households what changed" logic — only the wording embedded in
 * [EventChangeNotificationPayload.changeSummary] by the writer differs. [EventNotificationHandlerConfig]
 * registers nine distinct `@Bean`-produced instances below, one per event type, so
 * [com.leaguelift.outbox.application.OutboxWorker]'s `eventType -> handler` map still
 * gets nine distinct entries.
 */
class EventChangeNotificationHandler(
	override val eventType: String,
	private val emailProvider: EmailProvider,
	private val smsProvider: SmsProvider,
	private val objectMapper: ObjectMapper,
) : OutboxEventHandler {

	override fun handle(event: OutboxEvent) {
		val payload = objectMapper.readValue(event.payload, EventChangeNotificationPayload::class.java)
		if (payload.recipients.isEmpty()) {
			log.info("Event {} (\"{}\") has no notification-eligible household this change — nothing to send.", event.aggregateId, payload.displayTitle)
			return
		}
		for (recipient in payload.recipients) {
			recipient.email?.let {
				emailProvider.send(EmailMessage(to = it, subject = "Schedule update: ${payload.displayTitle}", body = "${payload.changeSummary}\n\n— LeagueLift"))
			}
			recipient.phone?.let {
				smsProvider.send(SmsMessage(to = it, body = "LeagueLift: ${payload.changeSummary}"))
			}
		}
	}
}

@Configuration
class EventNotificationHandlerConfig(
	private val emailProvider: EmailProvider,
	private val smsProvider: SmsProvider,
	private val objectMapper: ObjectMapper,
) {
	private fun handlerFor(eventType: String) = EventChangeNotificationHandler(eventType, emailProvider, smsProvider, objectMapper)

	@Bean fun eventCreatedNotificationHandler() = handlerFor("event.created")
	@Bean fun eventTimeChangedNotificationHandler() = handlerFor("event.time_changed")
	@Bean fun eventLocationChangedNotificationHandler() = handlerFor("event.location_changed")
	@Bean fun eventAreaAssignedNotificationHandler() = handlerFor("event.area_assigned")
	@Bean fun eventArrivalTimeChangedNotificationHandler() = handlerFor("event.arrival_time_changed")
	@Bean fun eventPostponedNotificationHandler() = handlerFor("event.postponed")
	@Bean fun eventCancelledNotificationHandler() = handlerFor("event.cancelled")
	@Bean fun tournamentEventAddedNotificationHandler() = handlerFor("tournament.event_added")
	@Bean fun tournamentEventUpdatedNotificationHandler() = handlerFor("tournament.event_updated")
}
