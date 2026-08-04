package com.rally26.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Bound from `rally26.ics-feed.sync.*` (Phase 12 slice 3, ADR-033). [cron]
 * controls how often every ACTIVE ICS_FEED connection is re-fetched and synced —
 * mirrors `FeePaymentReminderProperties`'s shape.
 */
@ConfigurationProperties(prefix = "rally26.ics-feed.sync")
data class IcsFeedSyncProperties(
	val enabled: Boolean = true,
	val cron: String = "0 */30 * * * *",
)
