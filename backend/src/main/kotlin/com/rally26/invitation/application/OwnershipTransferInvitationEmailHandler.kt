package com.rally26.invitation.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.rally26.config.FrontendProperties
import com.rally26.config.ResendTemplateProperties
import com.rally26.invitation.domain.OwnershipTransferInvitationStatus
import com.rally26.invitation.persistence.OwnershipTransferInvitationRepository
import com.rally26.notification.EmailMessage
import com.rally26.notification.EmailProvider
import com.rally26.notification.EmailTemplateRef
import com.rally26.organization.persistence.OrganizationRepository
import com.rally26.outbox.application.OutboxEventHandler
import com.rally26.outbox.domain.OutboxEvent
import org.springframework.stereotype.Component
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID

private val EXPIRY_DATE_FORMATTER = DateTimeFormatter.ofPattern("MMMM d, yyyy").withZone(ZoneOffset.UTC)

/** Consumes `ownership_transfer.invitation_created`, reusing the generic NOTIFICATION_* Resend template — same pattern as [com.rally26.invitation.application.HouseholdInvitationEmailHandler]. */
@Component
class OwnershipTransferInvitationEmailHandler(
    private val ownershipTransferInvitationRepository: OwnershipTransferInvitationRepository,
    private val organizationRepository: OrganizationRepository,
    private val emailProvider: EmailProvider,
    private val frontendProperties: FrontendProperties,
    private val resendTemplateProperties: ResendTemplateProperties,
    private val objectMapper: ObjectMapper,
) : OutboxEventHandler {
    override val eventType: String = "ownership_transfer.invitation_created"

    override fun handle(event: OutboxEvent) {
        val payload = objectMapper.readTree(event.payload)
        val invitationId = UUID.fromString(payload.get("invitationId").asText())
        val acceptToken =
            payload
                .get("acceptToken")
                ?.asText()
                ?.trim()
                .orEmpty()
        if (acceptToken.isBlank()) return
        val invitation = ownershipTransferInvitationRepository.findById(invitationId) ?: return
        if (invitation.status != OwnershipTransferInvitationStatus.PENDING) return

        val organizationName = organizationRepository.findById(invitation.organizationId)?.name ?: "your organization"
        val acceptUrl = "${frontendProperties.baseUrl}/auth/ownership-transfer-invitation?token=$acceptToken"
        val expiresAtFormatted = EXPIRY_DATE_FORMATTER.format(invitation.expiresAt)
        val subject = "You've been invited to become the owner of $organizationName on Rally26"
        val details =
            "The current owner of $organizationName on Rally26 has invited you to take over as the organization's owner. " +
                "This gives you full control of the organization, including billing, teams, and members."

        emailProvider.send(
            EmailMessage(
                to = invitation.email,
                subject = subject,
                body = "$details\n\nAccept your invitation: $acceptUrl\n\nThis link expires on $expiresAtFormatted.\n\n— Rally26",
                template =
                    resendTemplateProperties.notificationId.takeIf { it.isNotBlank() }?.let { templateId ->
                        EmailTemplateRef(
                            id = templateId,
                            variables =
                                mapOf(
                                    "NOTIFICATION_TITLE" to subject,
                                    "NOTIFICATION_DETAILS" to "$details Expires $expiresAtFormatted.",
                                    "ACTION_URL" to acceptUrl,
                                ),
                        )
                    },
            ),
        )
    }
}
