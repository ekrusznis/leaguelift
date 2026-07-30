package com.leaguelift.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Bound from `leaguelift.outbox.worker.*` (Phase 8 slice 1, ADR-022). Not secrets —
 * safe defaults, overridable without a redeploy. [backoffBaseSeconds] doubles per
 * attempt (attempt 1 -> base, attempt 2 -> base*2, attempt 3 -> base*4, ...), capped at
 * [backoffCapSeconds], until [maxAttempts] is reached and the row moves to DEAD_LETTER
 * instead of retrying again.
 */
@ConfigurationProperties(prefix = "leaguelift.outbox.worker")
data class OutboxWorkerProperties(
	val enabled: Boolean = true,
	val pollIntervalMs: Long = 5000,
	val batchSize: Int = 20,
	val maxAttempts: Int = 5,
	val backoffBaseSeconds: Long = 30,
	val backoffCapSeconds: Long = 3600,
)
