package com.rally26.communication.application

import com.rally26.authorization.application.AuthorizationService
import com.rally26.authorization.domain.Capabilities
import com.rally26.common.error.ConflictException
import com.rally26.common.error.NotFoundException
import com.rally26.common.error.ValidationException
import com.rally26.common.web.CurrentUser
import com.rally26.communication.domain.Announcement
import com.rally26.communication.domain.AnnouncementAudience
import com.rally26.communication.domain.AnnouncementKind
import com.rally26.communication.domain.AnnouncementScopeType
import com.rally26.communication.persistence.ReminderSourceRepository
import com.rally26.membership.application.MembershipService
import org.springframework.stereotype.Service
import java.text.NumberFormat
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

enum class ReminderResourceType { CAMPAIGN, EVENT, FEE_ASSIGNMENT, DOCUMENT, ELIGIBILITY }

@Service
class ReminderService(
    private val sources: ReminderSourceRepository,
    private val announcements: AnnouncementService,
    private val membershipService: MembershipService,
    private val authorizationService: AuthorizationService,
    private val clock: Clock,
) {
    fun send(
        organizationId: UUID,
        resourceType: ReminderResourceType,
        resourceId: UUID,
        idempotencyKey: String,
        emailEnabled: Boolean,
        smsEnabled: Boolean,
        currentUser: CurrentUser,
    ): Announcement =
        when (resourceType) {
            ReminderResourceType.CAMPAIGN -> campaign(organizationId, resourceId, idempotencyKey, emailEnabled, smsEnabled, currentUser)
            ReminderResourceType.EVENT -> event(organizationId, resourceId, idempotencyKey, emailEnabled, smsEnabled, currentUser)
            ReminderResourceType.FEE_ASSIGNMENT -> fee(organizationId, resourceId, idempotencyKey, emailEnabled, smsEnabled, currentUser)
            ReminderResourceType.DOCUMENT -> document(organizationId, resourceId, idempotencyKey, emailEnabled, smsEnabled, currentUser)
            ReminderResourceType.ELIGIBILITY ->
                eligibility(
                    organizationId,
                    resourceId,
                    idempotencyKey,
                    emailEnabled,
                    smsEnabled,
                    currentUser,
                )
        }

    private fun campaign(
        organizationId: UUID,
        id: UUID,
        key: String,
        email: Boolean,
        sms: Boolean,
        currentUser: CurrentUser,
    ): Announcement {
        membershipService.requireManagerRole(organizationId, currentUser)
        val campaign =
            sources.findCampaign(organizationId, id)
                ?: throw NotFoundException("CAMPAIGN_NOT_FOUND", "The campaign could not be found.")
        if (campaign.status !=
            "ACTIVE"
        ) {
            throw ConflictException("CAMPAIGN_NOT_ACTIVE", "Only an active campaign can send a launch reminder.")
        }
        val scopeType = if (campaign.teamId == null) AnnouncementScopeType.ORGANIZATION else AnnouncementScopeType.TEAM
        val scopeId = campaign.teamId ?: organizationId
        return announcements.createSystemAndPublish(
            organizationId,
            scopeType,
            scopeId,
            AnnouncementKind.CAMPAIGN_LAUNCH,
            "CAMPAIGN",
            campaign.id,
            null,
            sourceKey("CAMPAIGN", campaign.id, key),
            "Campaign launched: ${campaign.name}",
            "${campaign.name} is now open. View and share the campaign at /campaigns/${campaign.slug}.",
            AnnouncementAudience.ALL,
            email,
            sms,
            currentUser,
        )
    }

    private fun event(
        organizationId: UUID,
        id: UUID,
        key: String,
        email: Boolean,
        sms: Boolean,
        currentUser: CurrentUser,
    ): Announcement {
        val event =
            sources.findEvent(organizationId, id)
                ?: throw NotFoundException("EVENT_NOT_FOUND", "The event could not be found.")
        if (event.status == "DRAFT" ||
            event.status == "CANCELLED" ||
            event.status == "COMPLETED" ||
            event.startAt == null ||
            !event.startAt.isAfter(Instant.now(clock))
        ) {
            throw ConflictException("EVENT_NOT_REMINDABLE", "Only a published upcoming event with a future start time can send a reminder.")
        }
        val scopeType: AnnouncementScopeType
        val scopeId: UUID
        when {
            event.teamId != null -> {
                scopeType = AnnouncementScopeType.TEAM
                scopeId = event.teamId
                authorizationService.requireTeamCapability(organizationId, scopeId, currentUser, Capabilities.TEAM_COMMUNICATION_MANAGE)
            }
            event.tournamentId != null -> {
                scopeType = AnnouncementScopeType.TOURNAMENT
                scopeId = event.tournamentId
                authorizationService.requireTournamentCapability(
                    organizationId,
                    scopeId,
                    currentUser,
                    Capabilities.TOURNAMENT_COMMUNICATION_MANAGE,
                )
            }
            else -> {
                scopeType = AnnouncementScopeType.ORGANIZATION
                scopeId = organizationId
                membershipService.requireManagerRole(organizationId, currentUser)
            }
        }
        val whenText = event.startAt.atZone(ZoneId.of(event.timezone)).format(DateTimeFormatter.ofPattern("MMM d, yyyy 'at' h:mm a"))
        val whereText = event.venueName?.let { " at $it" } ?: ""
        val audience = if (scopeType == AnnouncementScopeType.TEAM) AnnouncementAudience.ALL else AnnouncementAudience.STAFF
        return announcements.createSystemAndPublish(
            organizationId,
            scopeType,
            scopeId,
            AnnouncementKind.EVENT_REMINDER,
            "EVENT",
            event.id,
            null,
            sourceKey("EVENT", event.id, key),
            "Event reminder: ${event.title}",
            "${event.title} is scheduled for $whenText$whereText. Open Rally26 for the latest details and RSVP status.",
            audience,
            email,
            sms,
            currentUser,
        )
    }

    private fun fee(
        organizationId: UUID,
        id: UUID,
        key: String,
        email: Boolean,
        sms: Boolean,
        currentUser: CurrentUser,
    ): Announcement {
        membershipService.requireManagerRole(organizationId, currentUser)
        val fee =
            sources.findFee(organizationId, id)
                ?: throw NotFoundException("FEE_ASSIGNMENT_NOT_FOUND", "The fee assignment could not be found.")
        if (fee.status !in setOf("OPEN", "PARTIALLY_PAID") || fee.balanceMinor <= 0) {
            throw ConflictException("FEE_NOT_REMINDABLE", "This fee no longer has an outstanding balance.")
        }
        val amount =
            NumberFormat
                .getCurrencyInstance(Locale.US)
                .apply {
                    currency = java.util.Currency.getInstance(fee.currency)
                }.format(java.math.BigDecimal.valueOf(fee.balanceMinor, 2))
        val due = fee.dueDate?.let { " due $it" } ?: ""
        return announcements.createSystemAndPublish(
            organizationId,
            AnnouncementScopeType.ORGANIZATION,
            organizationId,
            AnnouncementKind.FEE_REMINDER,
            "FEE_ASSIGNMENT",
            fee.id,
            fee.householdId,
            sourceKey("FEE", fee.id, key),
            "Payment reminder: ${fee.description}",
            "$amount remains for ${fee.description}$due. Sign in to the household workspace to review the balance and payment history.",
            AnnouncementAudience.GUARDIANS,
            email,
            sms,
            currentUser,
        )
    }

    private fun document(
        organizationId: UUID,
        id: UUID,
        key: String,
        email: Boolean,
        sms: Boolean,
        currentUser: CurrentUser,
    ): Announcement {
        membershipService.requireManagerRole(organizationId, currentUser)
        val document =
            sources.findDocument(organizationId, id)
                ?: throw NotFoundException("DOCUMENT_NOT_FOUND", "The household document could not be found.")
        return announcements.createSystemAndPublish(
            organizationId,
            AnnouncementScopeType.ORGANIZATION,
            organizationId,
            AnnouncementKind.DOCUMENT_REMINDER,
            "DOCUMENT",
            document.id,
            document.householdId,
            sourceKey("DOCUMENT", document.id, key),
            "Document reminder: ${document.title}",
            "Please review and acknowledge ${document.title} in your Rally26 household documents.",
            AnnouncementAudience.GUARDIANS,
            email,
            sms,
            currentUser,
        )
    }

    /** Phase 31 slice 31.5 — the first real sender for the "Documents & eligibility" notification preference (V67, ADR-088). */
    private fun eligibility(
        organizationId: UUID,
        id: UUID,
        key: String,
        email: Boolean,
        sms: Boolean,
        currentUser: CurrentUser,
    ): Announcement {
        membershipService.requireManagerRole(organizationId, currentUser)
        val participant =
            sources.findParticipant(organizationId, id)
                ?: throw NotFoundException("PARTICIPANT_NOT_FOUND", "The participant could not be found.")
        return announcements.createSystemAndPublish(
            organizationId,
            AnnouncementScopeType.ORGANIZATION,
            organizationId,
            AnnouncementKind.ELIGIBILITY_REMINDER,
            "PARTICIPANT",
            participant.id,
            participant.householdId,
            sourceKey("ELIGIBILITY", participant.id, key),
            "Eligibility reminder: ${participant.firstName} ${participant.lastName}",
            "${participant.firstName} ${participant.lastName} has outstanding eligibility requirements for their team. " +
                "Sign in to Rally26 to review and complete them.",
            AnnouncementAudience.GUARDIANS,
            email,
            sms,
            currentUser,
        )
    }

    private fun sourceKey(
        prefix: String,
        id: UUID,
        key: String,
    ): String {
        val normalized = key.trim()
        if (normalized.length !in 8..100) throw ValidationException("Idempotency key must be between 8 and 100 characters.")
        return "$prefix:$id:$normalized"
    }
}
