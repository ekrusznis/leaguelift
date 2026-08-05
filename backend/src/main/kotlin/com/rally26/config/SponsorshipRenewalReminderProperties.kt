package com.rally26.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Bound from `rally26.sponsorship.renewal-reminder.*`. Phase 6 remainder (ADR-019):
 * a minimal, deliberately-not-outbox-worker-based scheduled reminder — see
 * `sponsorship/application/SponsorshipRenewalReminderService.kt` for why. [daysBefore]
 * is the founder-requested configurable "N days before placement_end_date" window;
 * [cron] controls how often the check itself runs (daily by default — the reminder is
 * one-shot per sponsorship regardless of how often the job runs, guarded by
 * `sponsorship.renewal_reminder_sent_at`).
 */
@ConfigurationProperties(prefix = "rally26.sponsorship.renewal-reminder")
data class SponsorshipRenewalReminderProperties(
    val enabled: Boolean = true,
    val daysBefore: Long = 14,
    val cron: String = "0 0 8 * * *",
)
