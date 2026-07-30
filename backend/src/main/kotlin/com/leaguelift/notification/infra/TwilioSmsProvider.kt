package com.leaguelift.notification.infra

import com.leaguelift.common.error.ServiceUnavailableException
import com.leaguelift.config.TwilioProperties
import com.leaguelift.notification.SmsMessage
import com.leaguelift.notification.SmsProvider
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException

/**
 * Real send path for `SmsProvider` (Phase 8 slice 3, ADR-024) — Twilio's Messages API
 * (`POST /Messages.json`, form-urlencoded `To`/`From`/`Body`). Active only when
 * `leaguelift.sms.provider = twilio`; [com.leaguelift.notification.LoggingSmsProvider]
 * remains the default everywhere real Twilio credentials aren't configured (which is
 * everywhere today). A blank [TwilioProperties.accountSid]/[TwilioProperties.authToken]
 * still produces a client that fails cleanly with a 401, translated into
 * `ServiceUnavailableException` rather than a raw provider error — same posture as
 * `ResendEmailProvider`.
 */
@Component
@ConditionalOnProperty(prefix = "leaguelift.sms", name = ["provider"], havingValue = "twilio")
class TwilioSmsProvider(
	private val twilioRestClient: RestClient,
	private val twilioProperties: TwilioProperties,
) : SmsProvider {

	override fun send(message: SmsMessage) {
		val form = LinkedMultiValueMap<String, String>().apply {
			add("To", message.to)
			add("From", twilioProperties.fromNumber)
			add("Body", message.body)
		}
		try {
			twilioRestClient.post()
				.uri("/Messages.json")
				.body(form)
				.retrieve()
				.toBodilessEntity()
		} catch (e: RestClientException) {
			throw ServiceUnavailableException(
				"SMS_PROVIDER_UNAVAILABLE",
				"Twilio could not be reached or rejected the request: ${e.message}",
			)
		}
	}
}
