package com.rally26.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Bound from `rally26.spaces.*`. S3-compatible object storage — MinIO locally/in
 * tests, DigitalOcean Spaces in staging/prod (ADR-012). [accessKey]/[secretKey] have no
 * defaults in staging/prod config so a missing value fails startup rather than silently
 * running unauthenticated.
 */
@ConfigurationProperties(prefix = "rally26.spaces")
data class SpacesProperties(
    val endpoint: String,
    val accessKey: String,
    val secretKey: String,
    val bucket: String,
    val region: String,
)
