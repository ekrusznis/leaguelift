package com.rally26.fundraising.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.rally26.config.FrontendProperties
import com.rally26.fundraising.domain.Campaign
import com.rally26.fundraising.domain.CampaignStatus
import com.rally26.fundraising.persistence.CampaignRepository
import com.rally26.identity.domain.AppUser
import com.rally26.identity.persistence.AppUserRepository
import com.rally26.membership.domain.MembershipRole
import com.rally26.membership.persistence.MembershipRepository
import com.rally26.notification.EmailMessage
import com.rally26.notification.EmailProvider
import com.rally26.organization.persistence.OrganizationRepository
import com.rally26.outbox.application.OutboxEventHandler
import com.rally26.outbox.domain.OutboxEvent
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * Transactional email delivery for fundraiser lifecycle events.
 *
 * CampaignService/CampaignLifecycleScheduler only enqueue durable outbox rows. Recipient
 * lookup and email delivery happen here after commit, using current membership/user state.
 * Each event type has exactly one handler because OutboxWorker maps event_type -> handler.
 */
@Service
class FundraisingLifecycleEmailService(
    private val campaignRepository: CampaignRepository,
    private val membershipRepository: MembershipRepository,
    private val appUserRepository: AppUserRepository,
    private val organizationRepository: OrganizationRepository,
    private val emailProvider: EmailProvider,
    private val frontendProperties: FrontendProperties,
    private val objectMapper: ObjectMapper,
) {
    fun submitted(event: OutboxEvent) {
        val campaign = campaign(event) ?: return
        val creator = creator(campaign)
        ownerUsers(campaign.organizationId).forEach { owner ->
            send(
                event = event,
                recipient = owner,
                subject = "Fundraiser awaiting your approval",
                body =
                    buildString {
                        append("${creator?.displayName ?: "A Rally26 member"} submitted \"${campaign.name}\" for your approval.\n\n")
                        append("Review the fundraiser: ${managementUrl(campaign)}\n\n— Rally26")
                    },
            )
        }
    }

    fun approved(event: OutboxEvent) {
        val campaign = campaign(event) ?: return
        val recipient = creator(campaign) ?: return
        if (isOwner(campaign.organizationId, recipient.id)) return
        val targetStatus = targetStatus(event)
        val statusLine =
            if (targetStatus == CampaignStatus.SCHEDULED || campaign.status == CampaignStatus.SCHEDULED) {
                "It is approved and scheduled to begin${campaign.startDate?.let { " on $it" } ?: ""}."
            } else {
                "It is approved and active now."
            }
        send(
            event = event,
            recipient = recipient,
            subject = "Your fundraiser was approved",
            body =
                "Your fundraiser \"${campaign.name}\" was approved by the organization owner. $statusLine\n\n" +
                    "Open the fundraiser: ${managementUrl(campaign)}\n\n— Rally26",
        )
    }

    fun returnedToDraft(event: OutboxEvent) {
        val campaign = campaign(event) ?: return
        val recipient = creator(campaign) ?: return
        if (isOwner(campaign.organizationId, recipient.id)) return
        send(
            event = event,
            recipient = recipient,
            subject = "Your fundraiser needs changes",
            body =
                "The organization owner returned \"${campaign.name}\" to draft. You can edit it and submit it again when it is ready.\n\n" +
                    "Open the fundraiser: ${managementUrl(campaign)}\n\n— Rally26",
        )
    }

    fun activated(event: OutboxEvent) {
        val campaign = campaign(event) ?: return
        val recipient = creator(campaign) ?: return
        if (isOwner(campaign.organizationId, recipient.id)) return
        send(
            event = event,
            recipient = recipient,
            subject = "Your fundraiser is now active",
            body =
                "Your fundraiser \"${campaign.name}\" is now active and its public page is live.\n\n" +
                    "View the fundraiser: ${publicUrl(campaign)}\n\n— Rally26",
        )
    }

    fun ended(event: OutboxEvent) {
        val campaign = campaign(event) ?: return
        val recipients = linkedMapOf<UUID, AppUser>()
        ownerUsers(campaign.organizationId).forEach { recipients[it.id] = it }
        creator(campaign)?.let { recipients[it.id] = it }
        recipients.values.forEach { recipient ->
            val owner = isOwner(campaign.organizationId, recipient.id)
            send(
                event = event,
                recipient = recipient,
                subject = "Fundraiser ended: ${campaign.name}",
                body =
                    if (owner) {
                        "\"${campaign.name}\" reached its end date. Contributions are closed for this " +
                            "fundraising window and the fundraiser is ready for owner closeout.\n\n" +
                            "Review and close it: ${managementUrl(campaign)}\n\n— Rally26"
                    } else {
                        "\"${campaign.name}\" reached its end date. The organization owner can now review and close the fundraiser.\n\n" +
                            "View the fundraiser: ${managementUrl(campaign)}\n\n— Rally26"
                    },
            )
        }
    }

    private fun campaign(event: OutboxEvent): Campaign? {
        val organizationId = event.organizationId ?: return null
        val campaignId = payloadCampaignId(event) ?: event.aggregateId
        return campaignRepository.findById(campaignId, organizationId)
    }

    private fun payloadCampaignId(event: OutboxEvent): UUID? =
        runCatching {
            objectMapper
                .readTree(event.payload)
                .get("campaignId")
                ?.asText()
                ?.let(UUID::fromString)
        }.getOrNull()

    private fun targetStatus(event: OutboxEvent): CampaignStatus? =
        runCatching {
            objectMapper
                .readTree(event.payload)
                .get("targetStatus")
                ?.asText()
                ?.let(CampaignStatus::valueOf)
        }.getOrNull()

    private fun ownerUsers(organizationId: UUID): List<AppUser> =
        membershipRepository
            .listActiveManagers(organizationId)
            .filter { it.role == MembershipRole.OWNER }
            .mapNotNull { appUserRepository.findById(it.userId) }

    private fun creator(campaign: Campaign): AppUser? = campaign.createdByUserId?.let(appUserRepository::findById)

    private fun isOwner(
        organizationId: UUID,
        userId: UUID,
    ): Boolean = membershipRepository.findActiveMembership(organizationId, userId)?.role == MembershipRole.OWNER

    private fun managementUrl(campaign: Campaign): String =
        "${frontendProperties.baseUrl}/app/organizations/${campaign.organizationId}/fundraising"

    private fun publicUrl(campaign: Campaign): String = "${frontendProperties.baseUrl}/campaigns/${campaign.slug}"

    private fun send(
        event: OutboxEvent,
        recipient: AppUser,
        subject: String,
        body: String,
    ) {
        val organizationName = event.organizationId?.let(organizationRepository::findById)?.name
        val resolvedSubject = organizationName?.let { "$subject — $it" } ?: subject
        emailProvider.send(
            EmailMessage(
                to = recipient.email,
                subject = resolvedSubject,
                body = body,
                idempotencyKey = "fundraiser-${event.eventType}-${event.id}-${recipient.id}",
            ),
        )
    }
}

@Component
class FundraiserSubmittedEmailHandler(
    private val service: FundraisingLifecycleEmailService,
) : OutboxEventHandler {
    override val eventType = "fundraiser.submitted"

    override fun handle(event: OutboxEvent) = service.submitted(event)
}

@Component
class FundraiserApprovedEmailHandler(
    private val service: FundraisingLifecycleEmailService,
) : OutboxEventHandler {
    override val eventType = "fundraiser.approved"

    override fun handle(event: OutboxEvent) = service.approved(event)
}

@Component
class FundraiserReturnedToDraftEmailHandler(
    private val service: FundraisingLifecycleEmailService,
) : OutboxEventHandler {
    override val eventType = "fundraiser.returned_to_draft"

    override fun handle(event: OutboxEvent) = service.returnedToDraft(event)
}

@Component
class FundraiserActivatedEmailHandler(
    private val service: FundraisingLifecycleEmailService,
) : OutboxEventHandler {
    override val eventType = "fundraiser.activated"

    override fun handle(event: OutboxEvent) = service.activated(event)
}

@Component
class FundraiserEndedEmailHandler(
    private val service: FundraisingLifecycleEmailService,
) : OutboxEventHandler {
    override val eventType = "fundraiser.ended"

    override fun handle(event: OutboxEvent) = service.ended(event)
}
