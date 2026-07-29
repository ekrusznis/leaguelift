package com.leaguelift.notification

/**
 * The seam DESIGN-DOC.md section 17 reserves for a real transactional-email adapter
 * (Resend, per section 5's infra design target — `RESEND_API_KEY` already exists as an
 * unwired placeholder in `.env.example`). No implementation of this interface has ever
 * existed in this codebase before Phase 6 remainder (ADR-019): [LoggingEmailProvider]
 * is a deliberate stopgap, not a real send path, built only because
 * `SponsorshipRenewalReminderService` needed *something* concrete to call. Building the
 * real Resend-backed implementation is Phase 8 work (the outbox worker + notification
 * infrastructure milestone) — do not treat this interface's existence as that phase
 * having started.
 */
interface EmailProvider {
	fun send(message: EmailMessage)
}

data class EmailMessage(
	val to: String,
	val subject: String,
	val body: String,
)
