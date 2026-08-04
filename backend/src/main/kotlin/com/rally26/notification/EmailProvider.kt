package com.rally26.notification

/**
 * The seam DESIGN-DOC.md section 17 reserves for a real transactional-email adapter.
 * [LoggingEmailProvider] (Phase 6 remainder, ADR-019) was the only implementation until
 * Phase 8 slice 1 (ADR-022) added `notification/infra/ResendEmailProvider.kt` — a real
 * send path via Resend's HTTP API, active when `rally26.email.provider = resend`.
 * `LoggingEmailProvider` remains the default in every environment without a real
 * `RESEND_API_KEY` configured, which is every environment today.
 */
interface EmailProvider {
	fun send(message: EmailMessage)
}

data class EmailMessage(
	val to: String,
	val subject: String,
	val body: String,
	val cc: List<String> = emptyList(),
	val replyTo: String? = null,
	val idempotencyKey: String? = null,
)
