package com.rally26.notification.infra

import com.rally26.common.error.ServiceUnavailableException
import com.rally26.config.ResendProperties
import com.rally26.notification.EmailMessage
import com.rally26.notification.EmailProvider
import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import java.util.UUID

private data class SendEmailRequestDto(
	val from: String,
	val to: List<String>,
	val subject: String,
	val text: String,
	val cc: List<String>? = null,
	@JsonProperty("reply_to") val replyTo: String? = null,
)
private data class SendEmailResponseDto(val id: String)

/**
 * Real send path for `EmailProvider` (Phase 8 slice 1, ADR-022) — Resend's HTTP API
 * (`POST /emails`), a plain-text body only (no HTML templating this slice). Active only
 * when `rally26.email.provider = resend`; [LoggingEmailProvider] remains the default
 * everywhere real Resend credentials aren't configured (which is everywhere today — see
 * the ADR). A blank [ResendProperties.apiKey] still produces a client that fails
 * cleanly with a 401, translated below into `ServiceUnavailableException` rather than a
 * raw provider error, same posture as `VendorSelectionService`'s Printify translation.
 */
@Component
@ConditionalOnProperty(prefix = "rally26.email", name = ["provider"], havingValue = "resend")
class ResendEmailProvider(
	private val resendRestClient: RestClient,
	private val resendProperties: ResendProperties,
) : EmailProvider {

	override fun send(message: EmailMessage) {
		try {
			resendRestClient.post()
				.uri("/emails")
				.header("Idempotency-Key", message.idempotencyKey ?: "rally26-${UUID.randomUUID()}")
				.body(
					SendEmailRequestDto(
						from = resendProperties.fromAddress,
						to = listOf(message.to),
						subject = message.subject,
						text = message.body,
						cc = message.cc.takeIf { it.isNotEmpty() },
						replyTo = message.replyTo,
					),
				)
				.retrieve()
				.body(SendEmailResponseDto::class.java)
		} catch (e: RestClientException) {
			throw ServiceUnavailableException(
				"EMAIL_PROVIDER_UNAVAILABLE",
				"Resend could not be reached or rejected the request: ${e.message}",
			)
		}
	}
}
