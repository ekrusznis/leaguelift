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
    /**
     * Endpoint embedded in presigned URLs handed to the browser. Defaults to [endpoint].
     * Only needs to differ when [endpoint] is a server-internal address the browser can't
     * resolve (e.g. Docker Compose's `http://minio:9000` locally, vs `http://localhost:9000`
     * for the browser) — production's real Spaces endpoint is already publicly reachable,
     * so both stay equal there.
     */
    val publicEndpoint: String? = null,
)
