package com.leaguelift.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.web.client.RestClient

/**
 * Builds the plain REST client `notification/infra/ResendEmailProvider.kt` uses
 * (Phase 8 slice 1, ADR-022) — no official Kotlin/Java Resend SDK is assumed, mirroring
 * `PrintifyConfig`'s existing no-SDK precedent (a single-endpoint provider doesn't
 * justify a new dependency). A blank [ResendProperties.apiKey] still produces a working
 * client; Resend's own API rejects the request with a 401, which
 * `ResendEmailProvider` translates into a clean `ServiceUnavailableException` rather
 * than propagating a raw provider error.
 */
@Configuration
class ResendConfig(private val resendProperties: ResendProperties) {

	@Bean
	fun resendRestClient(): RestClient = RestClient.builder()
		.baseUrl("https://api.resend.com")
		.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer ${resendProperties.apiKey}")
		.defaultHeader(HttpHeaders.CONTENT_TYPE, "application/json")
		.build()
}
