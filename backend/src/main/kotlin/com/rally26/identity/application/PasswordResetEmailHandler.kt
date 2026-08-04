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
class PasswordResetEmailHandler(
	private val appUserRepository: AppUserRepository,
	private val emailProvider: EmailProvider,
	private val frontendProperties: FrontendProperties,
	private val resendTemplateProperties: ResendTemplateProperties,
	private val objectMapper: ObjectMapper,
) : OutboxEventHandler {

	override val eventType: String = "auth.password_reset_requested"

	override fun handle(event: OutboxEvent) {
		val payload = objectMapper.readTree(event.payload)
		val userId = UUID.fromString(payload.get("userId").asText())
		val resetToken = payload.get("resetToken")?.asText()?.trim().orEmpty()
		if (resetToken.isBlank()) return
		val user = appUserRepository.findById(userId) ?: return
		if (user.status != AppUserStatus.ACTIVE) return

		val resetUrl = "${frontendProperties.baseUrl}/auth/reset-password?token=$resetToken"
		emailProvider.send(
			EmailMessage(
				to = user.email,
				subject = "Reset your Rally26 password",
				body = "We received a request to reset your Rally26 password.\n\n" +
					"Reset password: $resetUrl\n\n" +
					"This link expires in 2 hours. If you did not request this, you can ignore this email.\n\n— Rally26",
				template = resendTemplateProperties.passwordResetId.takeIf { it.isNotBlank() }?.let { templateId ->
					EmailTemplateRef(id = templateId, variables = mapOf("RESET_URL" to resetUrl))
				},
			),
		)
	}
}

