package com.leaguelift.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Bound from `leaguelift.sms.twilio.*` (Phase 8 slice 3, ADR-024). Same posture as
 * [ResendProperties]: blank Kotlin defaults in every profile (including staging/prod)
 * since `"logging"` is a fully legitimate default mode where these are simply unused —
 * see [SmsProviderProperties]. A blank [accountSid]/[authToken] still produces a
 * working Twilio client; a resulting 401 is translated into a clean
 * `ServiceUnavailableException` at call time, same as a blank Resend/Printify key.
 */
@ConfigurationProperties(prefix = "leaguelift.sms.twilio")
data class TwilioProperties(
	val accountSid: String = "",
	val authToken: String = "",
	val fromNumber: String = "",
)
