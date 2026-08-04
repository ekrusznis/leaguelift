package com.rally26.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Bound from `rally26.email.smtp.*` (ADR-059) — Google Workspace SMTP credentials used
 * exclusively for support-case correspondence (`SupportCaseCreatedEmailHandler`), kept
 * deliberately separate from [ResendProperties]/[ResendTemplateProperties], which stay
 * reserved for branded automated lifecycle emails (verify/reset/invite/welcome, and the
 * support-case status-change notice). Blank `username`/`password` (the default in every
 * environment without real credentials) means "SMTP not configured" — same convention as
 * [ResendProperties.apiKey] — and [com.rally26.notification.infra.SmtpEmailProvider]
 * logs and no-ops instead of attempting a real send.
 */
@ConfigurationProperties(prefix = "rally26.email.smtp")
data class SmtpMailProperties(
	val host: String = "smtp.gmail.com",
	val port: Int = 587,
	val username: String = "",
	val password: String = "",
	val fromAddress: String = "support@rally26.com",
)
