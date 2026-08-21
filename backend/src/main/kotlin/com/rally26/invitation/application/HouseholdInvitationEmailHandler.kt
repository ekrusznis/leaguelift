package com.rally26.invitation.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.rally26.config.FrontendProperties
import com.rally26.config.ResendTemplateProperties
import com.rally26.invitation.domain.HouseholdInvitationKind
import com.rally26.invitation.domain.HouseholdInvitationStatus
import com.rally26.invitation.persistence.HouseholdInvitationRepository
import com.rally26.notification.EmailMessage
import com.rally26.notification.EmailProvider
import com.rally26.notification.EmailTemplateRef
import com.rally26.organization.persistence.OrganizationRepository
import com.rally26.outbox.application.OutboxEventHandler
import com.rally26.outbox.domain.OutboxEvent
import com.rally26.participant.persistence.ParticipantRepository
import org.springframework.stereotype.Component
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID

private val EXPIRY_DATE_FORMATTER = DateTimeFormatter.ofPattern("MMMM d, yyyy").withZone(ZoneOffset.UTC)

/** Consumes `household.invitation_created`, reusing the generic NOTIFICATION_* Resend template (see ResendTemplateProperties.notificationId) rather than a dedicated one — same pattern as every other non-receipt transactional email in this codebase. */
@Component
class HouseholdInvitationEmailHandler(
    private val householdInvitationRepository: HouseholdInvitationRepository,
    private val organizationRepository: OrganizationRepository,
    private val participantRepository: ParticipantRepository,
    private val emailProvider: EmailProvider,
    private val frontendProperties: FrontendProperties,
    private val resendTemplateProperties: ResendTemplateProperties,
    private val objectMapper: ObjectMapper,
) : OutboxEventHandler {
    override val eventType: String = "household.invitation_created"

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
        val invitation = householdInvitationRepository.findById(invitationId) ?: return
        if (invitation.status != HouseholdInvitationStatus.PENDING) return

        val organizationName = organizationRepository.findById(invitation.organizationId)?.name ?: "your organization"
        val participant = participantRepository.findById(invitation.participantId, invitation.organizationId)
        val athleteName = participant?.let { "${it.firstName} ${it.lastName}" } ?: "an athlete"
        val acceptUrl = "${frontendProperties.baseUrl}/auth/household-invitation?token=$acceptToken"
        val expiresAtFormatted = EXPIRY_DATE_FORMATTER.format(invitation.expiresAt)

        val subject: String
        val details: String
        when (invitation.kind) {
            HouseholdInvitationKind.GUARDIAN -> {
                subject = "You've been invited as a guardian on Rally26"
                details = "You've been invited to Rally26 as a guardian for $athleteName at $organizationName."
            }
            HouseholdInvitationKind.ATHLETE -> {
                subject = "You've been invited to your own Rally26 account"
                details = "You've been invited to set up your own Rally26 account for $athleteName at $organizationName."
            }
        }

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
