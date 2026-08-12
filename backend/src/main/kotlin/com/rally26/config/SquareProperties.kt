package com.rally26.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Bound from `rally26.square.*`. Phase 32 scaffold (Cash App Pay, via approved
 * Square payments) — same blank-means-unconfigured convention as [PayPalProperties].
 */
@ConfigurationProperties(prefix = "rally26.square")
data class SquareProperties(
    val accessToken: String = "",
    val locationId: String = "",
) {
    val configured: Boolean get() = accessToken.isNotBlank() && locationId.isNotBlank()
}
