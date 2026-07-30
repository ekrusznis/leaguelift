package com.leaguelift.notification

import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

private val log = LoggerFactory.getLogger(LoggingEmailProvider::class.java)

/**
 * The default [EmailProvider] implementation (Phase 6 remainder, ADR-019) — logs what
 * would be sent instead of actually sending it, the same "real interface, stub
 * implementation" pattern this codebase already uses for providers without configured
 * credentials. Active whenever `leaguelift.email.provider` is unset or anything other
 * than `resend` (`matchIfMissing = true`) — as of Phase 8 slice 1 (ADR-022) a real
 * `ResendEmailProvider` exists, but this stays the default everywhere real Resend
 * credentials aren't actually configured (which is everywhere today — see
 * `.env.example`'s unwired `RESEND_API_KEY`). Never logs the full message body if it
 * might ever carry sensitive data beyond a renewal reminder's own non-sensitive content
 * (DESIGN-DOC.md section 18.2's "never log sensitive personal data" rule) — reminder
 * emails only reference a sponsor's own name/package/date, already visible to the org
 * admin who approved that sponsorship.
 */
@Component
@ConditionalOnProperty(prefix = "leaguelift.email", name = ["provider"], havingValue = "logging", matchIfMissing = true)
class LoggingEmailProvider : EmailProvider {
	override fun send(message: EmailMessage) {
		log.info("Email would be sent to {} — subject: \"{}\"", message.to, message.subject)
	}
}
