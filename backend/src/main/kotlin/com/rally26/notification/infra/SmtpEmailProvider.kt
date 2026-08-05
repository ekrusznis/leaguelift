package com.rally26.notification.infra

import com.rally26.config.SmtpMailProperties
import com.rally26.notification.EmailMessage
import org.slf4j.LoggerFactory
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSenderImpl
import org.springframework.stereotype.Component

/**
 * Google Workspace SMTP send path for support-case correspondence (ADR-059) — used
 * only by [com.rally26.support.application.SupportCaseCreatedEmailHandler], never as
 * the ambient [com.rally26.notification.EmailProvider]. Deliberately does *not*
 * implement that interface: both [com.rally26.notification.infra.LoggingEmailProvider]
 * and [com.rally26.notification.infra.ResendEmailProvider] are conditional on
 * `rally26.email.provider` (exactly one of them is ever active), but this bean is
 * always present — implementing the shared interface would make it a second
 * `EmailProvider` candidate and break every other handler's unqualified
 * `EmailProvider` injection with an ambiguous-bean error. Callers that specifically
 * want the SMTP path inject this concrete class instead.
 *
 * Builds its own [JavaMailSenderImpl] from [SmtpMailProperties] rather than relying on
 * Spring Boot's `spring.mail.*` autoconfiguration, so there's exactly one place that
 * decides whether SMTP is "configured" (a blank [SmtpMailProperties.username], the
 * default in every environment without a real Google Workspace app password) — same
 * blank-means-inactive convention [com.rally26.config.ResendProperties.apiKey] uses.
 */
@Component
class SmtpEmailProvider(
    private val properties: SmtpMailProperties,
) {
    private val logger = LoggerFactory.getLogger(SmtpEmailProvider::class.java)

    private val sender: JavaMailSenderImpl by lazy {
        JavaMailSenderImpl().apply {
            host = properties.host
            port = properties.port
            username = properties.username
            password = properties.password
            javaMailProperties.apply {
                setProperty("mail.smtp.auth", "true")
                setProperty("mail.smtp.starttls.enable", "true")
            }
        }
    }

    fun send(message: EmailMessage) {
        if (properties.username.isBlank()) {
            logger.info(
                "SMTP not configured (rally26.email.smtp.username blank) — skipping real send. " +
                    "Would have sent to=${message.to} cc=${message.cc} subject=\"${message.subject}\"",
            )
            return
        }
        val mail = SimpleMailMessage()
        mail.setFrom(properties.fromAddress)
        mail.setTo(message.to)
        if (message.cc.isNotEmpty()) mail.setCc(*message.cc.toTypedArray())
        message.replyTo?.let { mail.setReplyTo(it) }
        mail.setSubject(message.subject)
        mail.setText(message.body)
        sender.send(mail)
    }
}
