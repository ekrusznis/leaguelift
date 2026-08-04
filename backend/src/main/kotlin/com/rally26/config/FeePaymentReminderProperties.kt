package com.rally26.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Bound from `rally26.fee.payment-reminder.*` (Phase 8 slice 2). Mirrors
 * `SponsorshipRenewalReminderProperties` — [daysBefore] is how far ahead of
 * `due_date` a reminder fires; [cron] controls how often the check itself runs
 * (the reminder is one-shot per fee assignment regardless, guarded by
 * `fee_assignment.payment_reminder_sent_at`).
 */
@ConfigurationProperties(prefix = "rally26.fee.payment-reminder")
data class FeePaymentReminderProperties(
	val enabled: Boolean = true,
	val daysBefore: Long = 3,
	val cron: String = "0 0 8 * * *",
)
