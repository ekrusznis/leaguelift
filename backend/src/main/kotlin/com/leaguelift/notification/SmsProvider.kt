package com.leaguelift.notification

/**
 * The one-way SMS seam (Phase 8 slice 3, ADR-024) — mirrors [EmailProvider] exactly.
 * [LoggingSmsProvider] is the default everywhere real Twilio credentials aren't
 * configured (which is everywhere today); [notification.infra.TwilioSmsProvider] is the
 * real send path, active only when `leaguelift.sms.provider = twilio`.
 */
interface SmsProvider {
	fun send(message: SmsMessage)
}

data class SmsMessage(
	val to: String,
	val body: String,
)
