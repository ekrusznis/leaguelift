package com.rally26.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "rally26.foundingorg.lifecycle")
data class FoundingPilotLifecycleProperties(
    val enabled: Boolean = true,
    val cron: String = "0 0 8 * * *",
)
