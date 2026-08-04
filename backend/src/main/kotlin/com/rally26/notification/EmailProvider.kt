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

/**
 * A Resend Template reference (Phase 8 slice 4) — [id] is the Resend-assigned template
 * ID (configured per-template via [com.rally26.config.ResendTemplateProperties], not
 * hardcoded), [variables] are the template's `{{{VARIABLE}}}` substitutions. When set
 * on an [EmailMessage], [com.rally26.notification.infra.ResendEmailProvider] sends via
 * `template: {id, variables}` instead of the plain `subject`/`body` fields — [subject]/
 * [body] are still required on [EmailMessage] itself so [LoggingEmailProvider] always
 * has something human-readable to log, but Resend ignores them once a template is set.
 * A blank [id] (the property's Kotlin default, meaning "no template configured for this
 * email yet") is treated as "no template" by callers, not sent as-is.
 */
data class EmailTemplateRef(
	val id: String,
	val variables: Map<String, Any> = emptyMap(),
)

data class EmailMessage(
	val to: String,
	val subject: String,
	val body: String,
	val cc: List<String> = emptyList(),
	val replyTo: String? = null,
	val idempotencyKey: String? = null,
	val template: EmailTemplateRef? = null,
)
