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
class OwnerEmailVerificationHandler(
	private val appUserRepository: AppUserRepository,
	private val emailProvider: EmailProvider,
	private val frontendProperties: FrontendProperties,
	private val objectMapper: ObjectMapper,
) : OutboxEventHandler {

	override val eventType: String = "auth.owner_verification_requested"

	override fun handle(event: OutboxEvent) {
		val payload = objectMapper.readTree(event.payload)
		val userId = UUID.fromString(payload.get("userId").asText())
		val verificationToken = payload.get("verificationToken")?.asText()?.trim().orEmpty()
		if (verificationToken.isBlank()) return
		val user = appUserRepository.findById(userId) ?: return
		if (user.status != AppUserStatus.PENDING_EMAIL_VERIFICATION) return

		val verifyUrl = "${frontendProperties.baseUrl}/auth/verify-email?token=$verificationToken"
		emailProvider.send(
			EmailMessage(
				to = user.email,
				subject = "Verify your LeagueLift account",
				body = "Welcome to LeagueLift. Verify your email to finish account setup.\n\n" +
					"Verify email: $verifyUrl\n\n" +
					"This link expires in 24 hours.\n\n— LeagueLift",
			),
		)
	}
}

