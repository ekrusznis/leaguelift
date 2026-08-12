package com.rally26.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Bound from `rally26.affirm.*`. Phase 32 scaffold (household pay-over-time) —
 * same blank-means-unconfigured convention as [PayPalProperties]. Provider/legal/
 * accounting/disclosure review (DESIGN-DOC.md section 19.3 item 42) is required
 * before this is ever configured with real credentials, not just a technical gate.
 */
@ConfigurationProperties(prefix = "rally26.affirm")
data class AffirmProperties(
    val publicApiKey: String = "",
    val privateApiKey: String = "",
) {
    val configured: Boolean get() = publicApiKey.isNotBlank() && privateApiKey.isNotBlank()
}
