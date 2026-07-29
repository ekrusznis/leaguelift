package com.leaguelift.notification

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

private val log = LoggerFactory.getLogger(LoggingEmailProvider::class.java)

/**
 * The only [EmailProvider] implementation this codebase has (Phase 6 remainder,
 * ADR-019) — logs what would be sent instead of actually sending it, the same
 * "real interface, stub implementation" pattern this codebase already uses for
 * providers without configured credentials (e.g. Stripe/Printify calls fail with a
 * clear `ServiceUnavailableException` rather than silently no-op, but no email
 * provider credentials exist anywhere in this environment at all yet — see
 * `.env.example`'s unwired `RESEND_API_KEY`). Never logs the full message body if it
 * might ever carry sensitive data beyond a renewal reminder's own non-sensitive content
 * (DESIGN-DOC.md section 18.2's "never log sensitive personal data" rule) — reminder
 * emails only reference a sponsor's own name/package/date, already visible to the org
 * admin who approved that sponsorship.
 */
@Component
class LoggingEmailProvider : EmailProvider {
	override fun send(message: EmailMessage) {
		log.info("Email would be sent to {} — subject: \"{}\"", message.to, message.subject)
	}
}
