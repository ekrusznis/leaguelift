package com.rally26.config

import org.springframework.boot.context.properties.ConfigurationProperties

/** Public, non-secret support routing configuration. Real mailbox activation is Phase 20. */
@ConfigurationProperties(prefix = "rally26.support")
data class SupportProperties(
    val inboxEmail: String = "support@rally26.com",
)
