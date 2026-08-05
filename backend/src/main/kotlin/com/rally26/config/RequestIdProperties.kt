package com.rally26.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "rally26.request-id")
data class RequestIdProperties(
    val headerName: String = "X-Request-Id",
)
