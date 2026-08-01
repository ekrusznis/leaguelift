package com.leaguelift.config

import org.springframework.boot.context.properties.ConfigurationProperties

/** Public, non-secret support routing configuration. Real mailbox activation is Phase 20. */
@ConfigurationProperties(prefix = "leaguelift.support")
data class SupportProperties(
    val inboxEmail: String = "support@leaguelift.io",
)
