package com.leaguelift.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Bound from `leaguelift.payout.*`. The holding period gates when an
 * ORGANIZATION_EARNING credit becomes eligible for transfer — ADR-017
 * (2026-07-29): a default 7-day holding period, with actual transfer firing
 * requiring an explicit manager-triggered action this phase, never an
 * automatic scheduler.
 */
@ConfigurationProperties(prefix = "leaguelift.payout")
data class PayoutProperties(
	val holdingPeriodDays: Long = 7,
)
