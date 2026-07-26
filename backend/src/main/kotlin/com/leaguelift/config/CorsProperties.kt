package com.leaguelift.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "leaguelift.cors")
data class CorsProperties(
	val allowedOrigins: List<String> = listOf("http://localhost:5173"),
)
