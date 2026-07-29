package com.leaguelift.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Bound from `leaguelift.platform.*`. A single global rate for this slice, not
 * per-org/plan-tier (DESIGN-DOC.md section 19.3 #12, subscription-plan tiers,
 * remains open) — ADR-017 explicitly treats this as a starting pilot value,
 * not final pricing, and DESIGN-DOC.md section 1 requires pricing to be
 * configurable, never hard-coded.
 */
@ConfigurationProperties(prefix = "leaguelift.platform")
data class PlatformFeeProperties(
	val feeBasisPoints: Int = 500,
) {
	/** e.g. 500 basis points -> 0.05. */
	fun feeMinorOf(grossAmountMinor: Long): Long = Math.floorDiv(grossAmountMinor * feeBasisPoints, 10_000L)
}
