package com.rally26.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.web.client.RestClient

/**
 * Builds the plain REST client used by `integration/printify` (no official
 * Kotlin/Java Printify SDK exists, unlike `stripe-java` for the payout/fundraising
 * modules — see `integration/printify/infra/PrintifyCatalogClient.kt` and
 * `PrintifyOrderClient.kt`). A blank [PrintifyProperties.apiToken] still produces a
 * working client; Printify's own API rejects the request with a 401, which the
 * calling services translate into a clean `ServiceUnavailableException` rather
 * than propagating a raw provider error.
 */
@Configuration
class PrintifyConfig(
    private val printifyProperties: PrintifyProperties,
) {
    @Bean
    fun printifyRestClient(): RestClient =
        RestClient
            .builder()
            .baseUrl("https://api.printify.com/v1")
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer ${printifyProperties.apiToken}")
            .defaultHeader(HttpHeaders.CONTENT_TYPE, "application/json")
            .build()
}
