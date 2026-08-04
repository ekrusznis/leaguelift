package com.rally26.notification.infra

import com.rally26.common.error.ServiceUnavailableException
import com.rally26.config.ResendProperties
import com.rally26.notification.EmailMessage
import com.rally26.notification.EmailProvider
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import java.util.UUID

/**
 * [subject]/[text] are used for a plain-content send; [template] is used instead for a
 * Resend Template send (Phase 8 slice 4) — the two are mutually exclusive per call, and
 * [JsonInclude.Include.NON_NULL] keeps whichever half is unused out of the request body
 * entirely rather than sending it as an explicit null.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
private data class SendEmailRequestDto(
	val from: String,
	val to: List<String>,
	val subject: String? = null,
	val text: String? = null,
	val cc: List<String>? = null,
	@JsonProperty("reply_to") val replyTo: String? = null,
	val template: SendEmailTemplateDto? = null,
)
private data class SendEmailTemplateDto(val id: String, val variables: Map<String, Any>)
private data class SendEmailResponseDto(val id: String)

/**
 * Real send path for `EmailProvider` (Phase 8 slice 1, ADR-022) — Resend's HTTP API
 * (`POST /emails`). Originally plain-text only; Phase 8 slice 4 added support for
 * sending via a Resend Template (`template: {id, variables}`) instead, used whenever
 * the caller's [EmailMessage.template] has a non-blank ID (see
 * [com.rally26.config.ResendTemplateProperties] for where those IDs come from) — a
 * message without one still falls back to the original plain `subject`/`text` send.
 * Active only when `rally26.email.provider = resend`; [LoggingEmailProvider] remains
 * the default everywhere real Resend credentials aren't configured (which is
 * everywhere today — see the ADR). A blank [ResendProperties.apiKey] still produces a
 * client that fails cleanly with a 401, translated below into
 * `ServiceUnavailableException` rather than a raw provider error, same posture as
 * `VendorSelectionService`'s Printify translation.
 */
@Component
@ConditionalOnProperty(prefix = "rally26.email", name = ["provider"], havingValue = "resend")
class ResendEmailProvider(
	private val resendRestClient: RestClient,
	private val resendProperties: ResendProperties,
) : EmailProvider {

	override fun send(message: EmailMessage) {
		try {
			val template = message.template?.takeIf { it.id.isNotBlank() }
			resendRestClient.post()
				.uri("/emails")
				.header("Idempotency-Key", message.idempotencyKey ?: "rally26-${UUID.randomUUID()}")
				.body(
					SendEmailRequestDto(
						from = resendProperties.fromAddress,
						to = listOf(message.to),
						subject = if (template == null) message.subject else null,
						text = if (template == null) message.body else null,
						cc = message.cc.takeIf { it.isNotEmpty() },
						replyTo = message.replyTo,
						template = template?.let { SendEmailTemplateDto(it.id, it.variables) },
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
