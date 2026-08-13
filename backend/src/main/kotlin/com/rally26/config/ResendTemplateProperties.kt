package com.rally26.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Bound from `rally26.email.resend.templates.*` (Phase 8 slice 4; extended ADR-059) —
 * the Resend-assigned IDs of the transactional Templates built in the Resend dashboard
 * (verify-email, password-reset, rally-invitation, welcome-email, and a support-case
 * status-change notice). Only consulted when `rally26.email.provider = resend`
 * ([EmailProviderProperties]), same as [ResendProperties]. Every field defaults to
 * blank, same rationale as [ResendProperties.apiKey]/[ResendProperties.fromAddress] —
 * "logging" mode never reads these, and each individual email handler falls back to
 * its original plain-text send whenever its own template ID is blank, so templates can
 * be published and wired up one at a time rather than all-or-nothing.
 */
@ConfigurationProperties(prefix = "rally26.email.resend.templates")
data class ResendTemplateProperties(
    val verifyEmailId: String = "",
    val passwordResetId: String = "",
    val invitationId: String = "",
    val welcomeId: String = "",
    /** ADR-059 — sent when a Platform Admin changes a support case's status (not on creation, which goes through SMTP instead — see SupportCaseCreatedEmailHandler). */
    val supportCaseUpdateId: String = "",
    /** Generic payment/transaction receipt — RECEIPT_TITLE, RECEIPT_DATA (multi-line summary string), ORDER_URL. Used by every handler confirming a specific payment/transaction occurred (fee payments, swag shop orders, contributions, sponsorships, refunds, offline acknowledgements). */
    val receiptId: String = "",
    /** Generic status/reminder notification — NOTIFICATION_TITLE, NOTIFICATION_DETAILS, ACTION_URL. Used by every other transactional email that isn't a payment receipt (reminders, status changes, messaging alerts, subscription lifecycle, disputes, event/RSVP changes). */
    val notificationId: String = "",
)
