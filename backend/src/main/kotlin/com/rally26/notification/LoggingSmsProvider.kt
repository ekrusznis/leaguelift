package com.rally26.notification

import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

private val log = LoggerFactory.getLogger(LoggingSmsProvider::class.java)

/**
 * The default [SmsProvider] (Phase 8 slice 3, ADR-024) — logs what would be sent
 * instead of actually sending it, the same stopgap pattern [LoggingEmailProvider]
 * already established. Active whenever `rally26.sms.provider` is unset or anything
 * other than `twilio` (`matchIfMissing = true`). Never logs the message body — an SMS
 * reminder body is short but still carries a household's payment/participant details,
 * more than the destination number alone needs to reveal in logs.
 */
@Component
@ConditionalOnProperty(prefix = "rally26.sms", name = ["provider"], havingValue = "logging", matchIfMissing = true)
class LoggingSmsProvider : SmsProvider {
	override fun send(message: SmsMessage) {
		log.info("SMS would be sent to {}", message.to)
	}
}
