package com.leaguelift.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Bound from `leaguelift.sms.provider` (Phase 8 slice 3, ADR-024). Not a secret —
 * selects which `SmsProvider` bean is active: `"logging"` (default, no real Twilio
 * credentials configured anywhere in this environment today) or `"twilio"` (real send,
 * requires [TwilioProperties] to be populated with a real account SID/auth token).
 */
@ConfigurationProperties(prefix = "leaguelift.sms")
data class SmsProviderProperties(
	val provider: String = "logging",
)
