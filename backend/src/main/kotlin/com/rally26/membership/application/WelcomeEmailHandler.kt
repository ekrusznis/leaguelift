package com.rally26.membership.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.rally26.config.FrontendProperties
import com.rally26.config.ResendTemplateProperties
import com.rally26.identity.persistence.AppUserRepository
import com.rally26.membership.domain.MembershipRole
import com.rally26.membership.domain.MembershipRoleLabels
import com.rally26.membership.persistence.MembershipRepository
import com.rally26.notification.EmailMessage
import com.rally26.notification.EmailProvider
import com.rally26.notification.EmailTemplateRef
import com.rally26.organization.persistence.OrganizationRepository
import com.rally26.outbox.application.OutboxEventHandler
import com.rally26.outbox.domain.OutboxEvent
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Sends the one-time "welcome to Rally26" email (Phase 8 slice 4) the first time a
 * user is granted any organization membership — see
 * [MembershipService.enqueueWelcomeEmailIfFirstMembership] for how "first" is
 * determined at write time. Re-reads current state rather than trusting the event
 * payload for anything beyond IDs, same "skip rather than fail on stale state" posture
 * as the other outbox email handlers: if the membership has since been revoked, or the
 * user/organization can no longer be found, this just returns without sending.
 */
@Component
class WelcomeEmailHandler(
    private val membershipRepository: MembershipRepository,
    private val appUserRepository: AppUserRepository,
    private val organizationRepository: OrganizationRepository,
    private val emailProvider: EmailProvider,
    private val frontendProperties: FrontendProperties,
    private val resendTemplateProperties: ResendTemplateProperties,
    private val objectMapper: ObjectMapper,
) : OutboxEventHandler {
    override val eventType: String = "membership.first_granted"

    override fun handle(event: OutboxEvent) {
        val payload = objectMapper.readTree(event.payload)
        val userId = UUID.fromString(payload.get("userId").asText())
        val organizationId = UUID.fromString(payload.get("organizationId").asText())
        val role = MembershipRole.valueOf(payload.get("role").asText())

        val membership = membershipRepository.findActiveMembership(organizationId, userId) ?: return
        val user = appUserRepository.findById(userId) ?: return
        val organization = organizationRepository.findById(organizationId) ?: return

        val roleLabel = MembershipRoleLabels.label(membership.role)
        val dashboardUrl = "${frontendProperties.baseUrl}/dashboard"
        emailProvider.send(
            EmailMessage(
                to = user.email,
                subject = "Welcome to Rally26",
                body =
                    "Welcome to Rally26. You're all set on ${organization.name} as a $roleLabel.\n\n" +
                        "Go to your dashboard: $dashboardUrl\n\n— Rally26",
                template =
                    resendTemplateProperties.welcomeId.takeIf { it.isNotBlank() }?.let { templateId ->
                        EmailTemplateRef(
                            id = templateId,
                            variables =
                                mapOf(
                                    "ORG_NAME" to organization.name,
                                    "ROLE_LABEL" to roleLabel,
                                    "FEATURES_HTML" to WelcomeEmailFeatures.featuresHtml(role),
                                    "DASHBOARD_URL" to dashboardUrl,
                                ),
                        )
                    },
            ),
        )
    }
}
