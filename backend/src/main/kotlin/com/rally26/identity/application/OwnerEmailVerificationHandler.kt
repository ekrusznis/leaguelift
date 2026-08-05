package com.rally26.identity.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.rally26.config.FrontendProperties
import com.rally26.config.ResendTemplateProperties
import com.rally26.identity.domain.AppUserStatus
import com.rally26.identity.persistence.AppUserRepository
import com.rally26.notification.EmailMessage
import com.rally26.notification.EmailProvider
import com.rally26.notification.EmailTemplateRef
import com.rally26.outbox.application.OutboxEventHandler
import com.rally26.outbox.domain.OutboxEvent
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class OwnerEmailVerificationHandler(
    private val appUserRepository: AppUserRepository,
    private val emailProvider: EmailProvider,
    private val frontendProperties: FrontendProperties,
    private val resendTemplateProperties: ResendTemplateProperties,
    private val objectMapper: ObjectMapper,
) : OutboxEventHandler {
    override val eventType: String = "auth.owner_verification_requested"

    override fun handle(event: OutboxEvent) {
        val payload = objectMapper.readTree(event.payload)
        val userId = UUID.fromString(payload.get("userId").asText())
        val verificationToken =
            payload
                .get("verificationToken")
                ?.asText()
                ?.trim()
                .orEmpty()
        if (verificationToken.isBlank()) return
        val user = appUserRepository.findById(userId) ?: return
        if (user.status != AppUserStatus.PENDING_EMAIL_VERIFICATION) return

        val verifyUrl = "${frontendProperties.baseUrl}/auth/verify-email?token=$verificationToken"
        emailProvider.send(
            EmailMessage(
                to = user.email,
                subject = "Verify your Rally26 account",
                body =
                    "Welcome to Rally26. Verify your email to finish account setup.\n\n" +
                        "Verify email: $verifyUrl\n\n" +
                        "This link expires in 24 hours.\n\n— Rally26",
                template =
                    resendTemplateProperties.verifyEmailId.takeIf { it.isNotBlank() }?.let { templateId ->
                        EmailTemplateRef(id = templateId, variables = mapOf("VERIFY_URL" to verifyUrl))
                    },
            ),
        )
    }
}
