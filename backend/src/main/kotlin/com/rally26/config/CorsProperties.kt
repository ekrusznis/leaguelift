package com.rally26.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "rally26.cors")
data class CorsProperties(
	val allowedOrigins: List<String> = listOf("http://localhost:5173"),
)
