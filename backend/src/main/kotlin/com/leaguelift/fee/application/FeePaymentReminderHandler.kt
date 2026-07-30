package com.leaguelift.fee.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.leaguelift.notification.EmailMessage
import com.leaguelift.notification.EmailProvider
import com.leaguelift.notification.SmsMessage
import com.leaguelift.notification.SmsProvider
import com.leaguelift.outbox.application.OutboxEventHandler
import com.leaguelift.outbox.domain.OutboxEvent
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.text.NumberFormat
import java.util.Locale

private val log = LoggerFactory.getLogger(FeePaymentReminderHandler::class.java)

/**
 * Sends the fee-payment-reminder email and/or SMS (Phase 8 slice 2, ADR-023; SMS added
 * slice 3, ADR-024). Email and SMS are independent channels — a household may have
 * either, both, or neither available (the scanner already resolved opt-out/opt-in into
 * null-or-present fields, see `FeeRepository.findNeedingPaymentReminder`'s comment); a
 * household with neither is logged and treated as a no-op, not a failure.
 */
@Component
class FeePaymentReminderHandler(
	private val emailProvider: EmailProvider,
	private val smsProvider: SmsProvider,
	private val objectMapper: ObjectMapper,
) : OutboxEventHandler {

	override val eventType: String = "fee.payment_reminder_due"

	override fun handle(event: OutboxEvent) {
		val payload = objectMapper.readTree(event.payload)
		val contactEmail = payload.get("householdContactEmail")?.takeIf { !it.isNull }?.asText()
		val contactPhone = payload.get("householdContactPhone")?.takeIf { !it.isNull }?.asText()
		val description = payload.get("description").asText()
		val dueDate = payload.get("dueDate").asText()
		val balanceMinor = payload.get("balanceMinor").asLong()
		val participantName = payload.get("participantName")?.takeIf { !it.isNull }?.asText()

		if (contactEmail == null && contactPhone == null) {
			log.info(
				"Fee assignment {} (\"{}\") due {} has no available reminder channel (no email, not opted into SMS) — nothing to send.",
				event.aggregateId, description, dueDate,
			)
			return
		}
		val amount = NumberFormat.getCurrencyInstance(Locale.US).format(balanceMinor / 100.0)
		val who = if (participantName != null) " for $participantName" else ""

		if (contactEmail != null) {
			emailProvider.send(
				EmailMessage(
					to = contactEmail,
					subject = "Payment reminder: $description",
					body = "Hi there,\n\n" +
						"This is a reminder that a payment of $amount$who ($description) is due on $dueDate. " +
						"Sign in to your LeagueLift household to make a payment.\n\n— LeagueLift",
				),
			)
		}
		if (contactPhone != null) {
			smsProvider.send(
				SmsMessage(
					to = contactPhone,
					body = "LeagueLift: a payment of $amount$who ($description) is due on $dueDate.",
				),
			)
		}
	}
}
