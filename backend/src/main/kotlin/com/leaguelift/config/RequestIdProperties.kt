package com.leaguelift.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "leaguelift.request-id")
data class RequestIdProperties(
	val headerName: String = "X-Request-Id",
)
