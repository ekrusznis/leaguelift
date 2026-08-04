package com.rally26.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Bound from `rally26.email.resend.templates.*` (Phase 8 slice 4) — the Resend-assigned
 * IDs of the four transactional Templates built in the Resend dashboard (verify-email,
 * password-reset, rally-invitation, welcome-email). Only consulted when
 * `rally26.email.provider = resend` ([EmailProviderProperties]), same as
 * [ResendProperties]. Every field defaults to blank, same rationale as
 * [ResendProperties.apiKey]/[ResendProperties.fromAddress] — "logging" mode never reads
 * these, and each individual email handler falls back to its original plain-text send
 * whenever its own template ID is blank, so templates can be published and wired up one
 * at a time rather than all-or-nothing.
 */
@ConfigurationProperties(prefix = "rally26.email.resend.templates")
data class ResendTemplateProperties(
	val verifyEmailId: String = "",
	val passwordResetId: String = "",
	val invitationId: String = "",
	val welcomeId: String = "",
)
