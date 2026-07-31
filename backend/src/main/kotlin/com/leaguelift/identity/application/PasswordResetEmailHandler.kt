package com.leaguelift.identity.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.leaguelift.config.FrontendProperties
import com.leaguelift.identity.domain.AppUserStatus
import com.leaguelift.identity.persistence.AppUserRepository
import com.leaguelift.notification.EmailMessage
import com.leaguelift.notification.EmailProvider
import com.leaguelift.outbox.application.OutboxEventHandler
import com.leaguelift.outbox.domain.OutboxEvent
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class PasswordResetEmailHandler(
	private val appUserRepository: AppUserRepository,
	private val emailProvider: EmailProvider,
	private val frontendProperties: FrontendProperties,
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
				subject = "Reset your LeagueLift password",
				body = "We received a request to reset your LeagueLift password.\n\n" +
					"Reset password: $resetUrl\n\n" +
					"This link expires in 2 hours. If you did not request this, you can ignore this email.\n\n— LeagueLift",
			),
		)
	}
}

