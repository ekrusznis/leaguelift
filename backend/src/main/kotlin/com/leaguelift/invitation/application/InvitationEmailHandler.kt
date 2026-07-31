package com.leaguelift.invitation.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.leaguelift.config.FrontendProperties
import com.leaguelift.invitation.domain.InvitationStatus
import com.leaguelift.invitation.persistence.InvitationRepository
import com.leaguelift.notification.EmailMessage
import com.leaguelift.notification.EmailProvider
import com.leaguelift.outbox.application.OutboxEventHandler
import com.leaguelift.outbox.domain.OutboxEvent
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * The first real consumer of `membership.invited` (Phase 8 slice 1, ADR-022) — closes
 * the "admin invitations are token-link only, no email delivery" gap called out since
 * Phase 1. Keeps the recipient/role/expiry authoritative by looking up the invitation
 * row by ID, while taking the one-time raw accept token from the event payload (it is
 * no longer stored in plaintext on the invitation row). A revoked/expired/already-
 * accepted invitation by the time this runs just skips sending rather than failing the
 * outbox event, since there's nothing useful left to email.
 */
@Component
class InvitationEmailHandler(
	private val invitationRepository: InvitationRepository,
	private val emailProvider: EmailProvider,
	private val frontendProperties: FrontendProperties,
	private val objectMapper: ObjectMapper,
) : OutboxEventHandler {

	override val eventType: String = "membership.invited"

	override fun handle(event: OutboxEvent) {
		val payload = objectMapper.readTree(event.payload)
		val invitationId = UUID.fromString(payload.get("invitationId").asText())
		val acceptToken = payload.get("acceptToken")?.asText()?.trim().orEmpty()
		if (acceptToken.isBlank()) return
		val invitation = invitationRepository.findById(invitationId) ?: return
		if (invitation.status != InvitationStatus.PENDING) return

		val acceptUrl = "${frontendProperties.baseUrl}/auth/invitation?token=$acceptToken"
		emailProvider.send(
			EmailMessage(
				to = invitation.email,
				subject = "You've been invited to join a LeagueLift organization",
				body = "You've been invited to join an organization on LeagueLift as ${invitation.role.name}.\n\n" +
					"Accept your invitation: $acceptUrl\n\n" +
					"This link expires on ${invitation.expiresAt}.\n\n— LeagueLift",
			),
		)
	}
}
