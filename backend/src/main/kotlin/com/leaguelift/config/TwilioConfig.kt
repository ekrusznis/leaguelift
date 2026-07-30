package com.leaguelift.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestClient

/**
 * Builds the plain REST client `notification/infra/TwilioSmsProvider.kt` uses (Phase 8
 * slice 3, ADR-024) — Twilio's Messages API directly, not the official Twilio Java SDK.
 * Mirrors `ResendConfig`/`PrintifyConfig`'s existing no-SDK precedent: this codebase
 * only ever needs one endpoint (`POST /Messages.json`), and Twilio's REST API has been
 * stable since 2010 — a plain authenticated client avoids pulling in a full SDK
 * dependency (with its own transitive dependencies and version-pinning risk) for one
 * call.
 */
@Configuration
class TwilioConfig(private val twilioProperties: TwilioProperties) {

	@Bean
	fun twilioRestClient(): RestClient = RestClient.builder()
		.baseUrl("https://api.twilio.com/2010-04-01/Accounts/${twilioProperties.accountSid}")
		.defaultHeaders { it.setBasicAuth(twilioProperties.accountSid, twilioProperties.authToken) }
		.build()
}
