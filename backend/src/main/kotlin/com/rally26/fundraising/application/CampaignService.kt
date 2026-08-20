package com.rally26.fundraising.application

import com.rally26.audit.application.AuditService
import com.rally26.authorization.application.AuthorizationService
import com.rally26.authorization.domain.Capabilities
import com.rally26.common.error.ConflictException
import com.rally26.common.error.FieldError
import com.rally26.common.error.ForbiddenException
import com.rally26.common.error.NotFoundException
import com.rally26.common.error.ValidationException
import com.rally26.common.web.CurrentUser
import com.rally26.fundraising.domain.Campaign
import com.rally26.fundraising.domain.CampaignStatus
import com.rally26.fundraising.domain.CampaignType
import com.rally26.fundraising.domain.FundraiserTemplateKey
import com.rally26.fundraising.domain.isValidCampaignSlug
import com.rally26.fundraising.persistence.CampaignRepository
import com.rally26.membership.application.MembershipService
import com.rally26.membership.domain.MembershipRole
import com.rally26.membership.domain.OrganizationMembership
import com.rally26.outbox.application.OutboxWriter
import com.rally26.sponsorship.infra.QrCodeGenerator
import com.rally26.subscription.application.PlanEntitlementService
import com.rally26.team.persistence.TeamRepository
import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.util.UUID

data class CampaignPermissions(
    val canEdit: Boolean,
    val canRequestActivation: Boolean,
    val canApprove: Boolean,
    val canReturnToDraft: Boolean,
    val canClose: Boolean,
    val canArchive: Boolean,
    val canManageBoxPool: Boolean,
)

@Service
class CampaignService(
    private val campaignRepository: CampaignRepository,
    private val teamRepository: TeamRepository,
    private val membershipService: MembershipService,
    private val authorizationService: AuthorizationService,
    private val fundraisingSettingsService: FundraisingSettingsService,
    private val planEntitlementService: PlanEntitlementService,
    private val auditService: AuditService,
    private val qrCodeGenerator: QrCodeGenerator,
    private val outboxWriter: OutboxWriter? = null,
) {
    private val allowedShareLinkPrefixes = listOf("http://", "https://")
    private val publicStatuses =
        setOf(
            CampaignStatus.SCHEDULED,
            CampaignStatus.ACTIVE,
            CampaignStatus.ENDED,
            CampaignStatus.CLOSED,
            CampaignStatus.COMPLETED,
        )

    fun buildShareLink(
        organizationId: UUID,
        url: String,
        currentUser: CurrentUser,
    ): String {
        membershipService.requireActiveMembership(organizationId, currentUser)
        if (url.length > 2000 || allowedShareLinkPrefixes.none { url.startsWith(it) }) {
            throw ValidationException("A valid http(s) URL is required to generate a QR code.")
        }
        return qrCodeGenerator.generatePngDataUri(url)
    }

    fun list(
        organizationId: UUID,
        currentUser: CurrentUser,
        offset: Int,
        limit: Int,
    ): List<Campaign> {
        membershipService.requireActiveMembership(organizationId, currentUser)
        return campaignRepository.findAll(organizationId, offset, limit)
    }

    fun count(
        organizationId: UUID,
        currentUser: CurrentUser,
    ): Long {
        membershipService.requireActiveMembership(organizationId, currentUser)
        return campaignRepository.countAll(organizationId)
    }

    fun get(
        organizationId: UUID,
        campaignId: UUID,
        currentUser: CurrentUser,
    ): Campaign {
        membershipService.requireActiveMembership(organizationId, currentUser)
        return requireCampaign(organizationId, campaignId)
    }

    /** Only campaigns that have been activated at least once are publicly visible. */
    fun getPublic(slug: String): Campaign {
        val campaign =
            campaignRepository.findBySlug(slug)
                ?: throw NotFoundException("CAMPAIGN_NOT_FOUND", "The campaign could not be found.")
        if (campaign.status !in publicStatuses) {
            throw NotFoundException("CAMPAIGN_NOT_FOUND", "The campaign could not be found.")
        }
        return campaign
    }

    fun permissionsFor(
        campaign: Campaign,
        currentUser: CurrentUser,
    ): CampaignPermissions {
        val membership = membershipService.requireActiveMembership(campaign.organizationId, currentUser)
        val owner = isOwner(membership)
        val manager = isManager(membership)
        val creatorStillHasAccess =
            manager ||
                (
                    campaign.createdByUserId == currentUser.userId &&
                        hasCreatorAccessWithoutThrowing(campaign.organizationId, campaign.teamId, currentUser)
                )
        val settings = fundraisingSettingsService.getInternal(campaign.organizationId)
        val editableStatus =
            campaign.status == CampaignStatus.DRAFT ||
                campaign.status == CampaignStatus.PENDING_APPROVAL ||
                campaign.status == CampaignStatus.SCHEDULED ||
                campaign.status == CampaignStatus.ACTIVE
        val alreadyApprovedOrLive = campaign.status == CampaignStatus.SCHEDULED || campaign.status == CampaignStatus.ACTIVE
        val canEditActive = !alreadyApprovedOrLive || !settings.requireOwnerApproval || owner

        return CampaignPermissions(
            canEdit = creatorStillHasAccess && editableStatus && canEditActive,
            canRequestActivation = creatorStillHasAccess && campaign.status == CampaignStatus.DRAFT,
            canApprove = owner && campaign.status == CampaignStatus.PENDING_APPROVAL,
            canReturnToDraft = owner && campaign.status == CampaignStatus.PENDING_APPROVAL,
            canClose = owner && campaign.status in setOf(CampaignStatus.ACTIVE, CampaignStatus.ENDED),
            canArchive =
                owner &&
                    campaign.status in
                    setOf(
                        CampaignStatus.DRAFT,
                        CampaignStatus.ENDED,
                        CampaignStatus.CLOSED,
                        CampaignStatus.COMPLETED,
                    ),
            canManageBoxPool = manager,
        )
    }

    @Transactional
    fun create(
        organizationId: UUID,
        teamId: UUID?,
        name: String,
        slug: String,
        description: String?,
        campaignType: CampaignType,
        goalAmountMinor: Long,
        currency: String,
        startDate: LocalDate?,
        endDate: LocalDate?,
        currentUser: CurrentUser,
        templateKey: FundraiserTemplateKey? = null,
        eventLocationName: String? = null,
        eventAddress: String? = null,
    ): Campaign {
        requireCreatorAccess(organizationId, teamId, currentUser)
        planEntitlementService.requireCampaignCapacity(organizationId, campaignRepository.countActive(organizationId))
        validateSlug(slug)
        validateDateRange(startDate, endDate)
        validateEventLocation(templateKey, eventLocationName, eventAddress)
        if (teamId != null) {
            teamRepository.findById(teamId, organizationId)
                ?: throw NotFoundException("TEAM_NOT_FOUND", "The team could not be found.")
        }

        return try {
            val campaign =
                campaignRepository.insert(
                    organizationId = organizationId,
                    teamId = teamId,
                    name = name,
                    slug = slug,
                    description = description,
                    campaignType = campaignType,
                    goalAmountMinor = goalAmountMinor,
                    currency = currency,
                    startDate = startDate,
                    endDate = endDate,
                    createdByUserId = currentUser.userId,
                    templateKey = templateKey,
                    eventLocationName = eventLocationName?.trim()?.takeIf { it.isNotEmpty() },
                    eventAddress = eventAddress?.trim()?.takeIf { it.isNotEmpty() },
                )
            auditService.record(
                currentUser.userId,
                organizationId,
                "campaign.created",
                "campaign",
                campaign.id,
                teamId = teamId,
                summary = "Fundraiser created",
            )
            campaign
        } catch (e: DuplicateKeyException) {
            throw ConflictException("CAMPAIGN_SLUG_TAKEN", "This slug is already in use.")
        }
    }

    @Transactional
    fun update(
        organizationId: UUID,
        campaignId: UUID,
        name: String?,
        description: String?,
        goalAmountMinor: Long?,
        startDate: LocalDate?,
        endDate: LocalDate?,
        currentUser: CurrentUser,
        eventLocationName: String? = null,
        eventAddress: String? = null,
    ): Campaign {
        val membership = membershipService.requireActiveMembership(organizationId, currentUser)
        val existing = requireCampaign(organizationId, campaignId)
        requireCanEdit(existing, membership, currentUser)

        if (existing.status in setOf(CampaignStatus.ENDED, CampaignStatus.CLOSED, CampaignStatus.COMPLETED, CampaignStatus.ARCHIVED)) {
            throw ValidationException("An ended, closed, or archived fundraiser cannot be edited.")
        }
        val settings = fundraisingSettingsService.getInternal(organizationId)
        if (existing.status in setOf(CampaignStatus.SCHEDULED, CampaignStatus.ACTIVE) &&
            settings.requireOwnerApproval &&
            !isOwner(membership)
        ) {
            throw ForbiddenException(
                "FUNDRAISER_ACTIVE_EDIT_REQUIRES_OWNER",
                "Only the organization owner can edit an approved or active fundraiser while owner approval is required.",
            )
        }

        validateDateRange(startDate ?: existing.startDate, endDate ?: existing.endDate)
        validateEventLocation(existing.templateKey, eventLocationName ?: existing.eventLocationName, eventAddress ?: existing.eventAddress)
        campaignRepository.update(
            id = campaignId,
            organizationId = organizationId,
            name = name,
            description = description,
            goalAmountMinor = goalAmountMinor,
            startDate = startDate,
            endDate = endDate,
            eventLocationName = eventLocationName,
            eventAddress = eventAddress,
        )

        // Editing a pending request changes what the owner would be approving. Return it
        // to DRAFT so the creator/admin must submit the revised fundraiser again.
        if (existing.status == CampaignStatus.PENDING_APPROVAL) {
            campaignRepository.returnToDraft(campaignId, organizationId)
        }

        auditService.record(
            currentUser.userId,
            organizationId,
            "campaign.updated",
            "campaign",
            campaignId,
            metadataJson =
                if (existing.status == CampaignStatus.PENDING_APPROVAL) {
                    """{"approvalReset":true,"statusBefore":"PENDING_APPROVAL","statusAfter":"DRAFT"}"""
                } else {
                    "{}"
                },
            teamId = existing.teamId,
            summary = "Fundraiser updated",
        )
        return requireCampaign(organizationId, campaignId)
    }

    /**
     * Backward-compatible replacement for the old immediate publish action.
     *
     * OWNER: activates immediately.
     * Non-owner + approval required: submits for owner approval.
     * Non-owner + approval disabled: activates immediately.
     */
    @Transactional
    fun requestActivation(
        organizationId: UUID,
        campaignId: UUID,
        currentUser: CurrentUser,
    ): Campaign {
        val membership = membershipService.requireActiveMembership(organizationId, currentUser)
        val campaign = requireCampaign(organizationId, campaignId)

        if (campaign.status == CampaignStatus.SCHEDULED || campaign.status == CampaignStatus.ACTIVE) return campaign
        if (campaign.status in setOf(CampaignStatus.ENDED, CampaignStatus.CLOSED, CampaignStatus.COMPLETED, CampaignStatus.ARCHIVED)) {
            throw ValidationException("An ended, closed, completed, or archived fundraiser cannot be activated.")
        }
        requireCanRequestActivation(campaign, membership, currentUser)

        val requireOwnerApproval = fundraisingSettingsService.getInternal(organizationId).requireOwnerApproval
        if (requireOwnerApproval && !isOwner(membership)) {
            if (campaign.status == CampaignStatus.PENDING_APPROVAL) return campaign
            campaignRepository.markPendingApproval(campaignId, organizationId)
            auditService.record(
                currentUser.userId,
                organizationId,
                "campaign.submitted_for_approval",
                "campaign",
                campaignId,
                metadataJson = """{"statusBefore":"${campaign.status.name}","statusAfter":"PENDING_APPROVAL"}""",
                teamId = campaign.teamId,
                summary = "Fundraiser submitted for owner approval",
            )
            enqueueLifecycleNotification("fundraiser.submitted", campaign)
        } else {
            val approvedBy = if (isOwner(membership)) currentUser.userId else null
            activateOrSchedule(
                campaign = campaign,
                approvedByUserId = approvedBy,
                actorUserId = currentUser.userId,
                ownerApprovalRequired = requireOwnerApproval,
            )
        }
        return requireCampaign(organizationId, campaignId)
    }

    /** Kept for API/source compatibility with the existing /publish endpoint. */
    @Transactional
    fun publish(
        organizationId: UUID,
        campaignId: UUID,
        currentUser: CurrentUser,
    ): Campaign = requestActivation(organizationId, campaignId, currentUser)

    @Transactional
    fun approve(
        organizationId: UUID,
        campaignId: UUID,
        currentUser: CurrentUser,
    ): Campaign {
        membershipService.requireOwnerRole(organizationId, currentUser)
        val campaign = requireCampaign(organizationId, campaignId)
        if (campaign.status == CampaignStatus.SCHEDULED || campaign.status == CampaignStatus.ACTIVE) return campaign
        if (campaign.status != CampaignStatus.PENDING_APPROVAL) {
            throw ValidationException("Only a fundraiser pending approval can be approved.")
        }

        val targetStatus = activationTargetStatus(campaign)
        auditService.record(
            currentUser.userId,
            organizationId,
            "campaign.approved",
            "campaign",
            campaignId,
            metadataJson = """{"statusBefore":"PENDING_APPROVAL","statusAfter":"${targetStatus.name}"}""",
            teamId = campaign.teamId,
            summary = "Fundraiser approved by owner",
        )
        activateOrSchedule(
            campaign = campaign,
            approvedByUserId = currentUser.userId,
            actorUserId = currentUser.userId,
            ownerApprovalRequired = true,
        )
        enqueueLifecycleNotification("fundraiser.approved", campaign, targetStatus)
        return requireCampaign(organizationId, campaignId)
    }

    @Transactional
    fun rejectApproval(
        organizationId: UUID,
        campaignId: UUID,
        currentUser: CurrentUser,
    ): Campaign {
        membershipService.requireOwnerRole(organizationId, currentUser)
        val campaign = requireCampaign(organizationId, campaignId)
        if (campaign.status != CampaignStatus.PENDING_APPROVAL) {
            throw ValidationException("Only a fundraiser pending approval can be returned to draft.")
        }
        campaignRepository.returnToDraft(campaignId, organizationId)
        auditService.record(
            currentUser.userId,
            organizationId,
            "campaign.approval_rejected",
            "campaign",
            campaignId,
            metadataJson = """{"statusBefore":"PENDING_APPROVAL","statusAfter":"DRAFT"}""",
            teamId = campaign.teamId,
            summary = "Fundraiser returned to draft by owner",
        )
        enqueueLifecycleNotification("fundraiser.returned_to_draft", campaign)
        return requireCampaign(organizationId, campaignId)
    }

    /**
     * Owner-only terminal lifecycle action. ACTIVE/PENDING transitions are deliberately
     * not accepted here; callers must use requestActivation/approve/rejectApproval.
     */
    @Transactional
    fun updateStatus(
        organizationId: UUID,
        campaignId: UUID,
        status: CampaignStatus,
        currentUser: CurrentUser,
    ): Campaign {
        membershipService.requireOwnerRole(organizationId, currentUser)
        val existing = requireCampaign(organizationId, campaignId)
        if (existing.status == status) return existing

        val action =
            when (status) {
                CampaignStatus.CLOSED -> {
                    if (existing.status !in setOf(CampaignStatus.ACTIVE, CampaignStatus.ENDED)) {
                        throw ValidationException("Only an active or ended fundraiser can be closed.")
                    }
                    "campaign.closed"
                }
                CampaignStatus.ARCHIVED -> {
                    if (existing.status !in
                        setOf(CampaignStatus.DRAFT, CampaignStatus.ENDED, CampaignStatus.CLOSED, CampaignStatus.COMPLETED)
                    ) {
                        throw ValidationException("Close or end this fundraiser before archiving it.")
                    }
                    "campaign.archived"
                }
                CampaignStatus.COMPLETED -> {
                    // Legacy client compatibility: COMPLETED now persists as CLOSED.
                    if (existing.status !in setOf(CampaignStatus.ACTIVE, CampaignStatus.ENDED)) {
                        throw ValidationException("Only an active or ended fundraiser can be closed.")
                    }
                    "campaign.closed"
                }
                CampaignStatus.DRAFT,
                CampaignStatus.PENDING_APPROVAL,
                CampaignStatus.SCHEDULED,
                CampaignStatus.ACTIVE,
                CampaignStatus.ENDED,
                -> throw ValidationException("Use the fundraiser approval/activation workflow for this status change.")
            }
        val persistedStatus = if (status == CampaignStatus.COMPLETED) CampaignStatus.CLOSED else status
        campaignRepository.updateStatus(campaignId, organizationId, persistedStatus)
        auditService.record(
            currentUser.userId,
            organizationId,
            action,
            "campaign",
            campaignId,
            metadataJson = """{"statusBefore":"${existing.status.name}","statusAfter":"${persistedStatus.name}"}""",
            teamId = existing.teamId,
            summary = if (persistedStatus == CampaignStatus.CLOSED) "Fundraiser closed" else "Fundraiser archived",
        )
        return requireCampaign(organizationId, campaignId)
    }

    private fun activateOrSchedule(
        campaign: Campaign,
        approvedByUserId: UUID?,
        actorUserId: UUID,
        ownerApprovalRequired: Boolean,
    ) {
        val targetStatus = activationTargetStatus(campaign)
        if (targetStatus == CampaignStatus.SCHEDULED) {
            campaignRepository.markScheduled(campaign.id, campaign.organizationId, approvedByUserId)
            auditService.record(
                actorUserId,
                campaign.organizationId,
                "campaign.scheduled",
                "campaign",
                campaign.id,
                metadataJson =
                    """{"statusBefore":"${campaign.status.name}","statusAfter":"SCHEDULED","ownerApprovalRequired":$ownerApprovalRequired,"startDate":"${campaign.startDate}"}""",
                teamId = campaign.teamId,
                summary = "Fundraiser scheduled",
            )
        } else {
            campaignRepository.markActive(campaign.id, campaign.organizationId, approvedByUserId)
            auditService.record(
                actorUserId,
                campaign.organizationId,
                "campaign.activated",
                "campaign",
                campaign.id,
                metadataJson =
                    """{"statusBefore":"${campaign.status.name}","statusAfter":"ACTIVE","ownerApprovalRequired":$ownerApprovalRequired}""",
                teamId = campaign.teamId,
                summary = "Fundraiser activated",
            )
            if (!ownerApprovalRequired) {
                enqueueLifecycleNotification("fundraiser.activated", campaign, CampaignStatus.ACTIVE)
            }
        }
    }

    private fun enqueueLifecycleNotification(
        eventType: String,
        campaign: Campaign,
        targetStatus: CampaignStatus? = null,
    ) {
        val targetStatusJson = targetStatus?.let { ",\"targetStatus\":\"${it.name}\"" } ?: ""
        outboxWriter?.write(
            aggregateType = "campaign",
            aggregateId = campaign.id,
            organizationId = campaign.organizationId,
            eventType = eventType,
            payloadJson = """{"campaignId":"${campaign.id}"$targetStatusJson}""",
        )
    }

    private fun activationTargetStatus(campaign: Campaign): CampaignStatus =
        if (campaign.startDate != null && campaign.startDate.isAfter(LocalDate.now())) CampaignStatus.SCHEDULED else CampaignStatus.ACTIVE

    private fun hasCreatorAccessWithoutThrowing(
        organizationId: UUID,
        teamId: UUID?,
        currentUser: CurrentUser,
    ): Boolean {
        val guardian = authorizationService.hasGuardianRelationshipInOrganization(organizationId, currentUser)
        val coachTeamIds =
            authorizationService.listAccessibleTeamIds(
                organizationId,
                currentUser,
                Capabilities.TEAM_FUNDRAISING_CREATE,
            )
        if (!guardian && coachTeamIds.isEmpty()) return false
        if (teamId == null) return true
        return (guardian && authorizationService.hasGuardianRelationshipForTeam(organizationId, teamId, currentUser)) ||
            teamId in coachTeamIds
    }

    private fun requireCreatorAccess(
        organizationId: UUID,
        teamId: UUID?,
        currentUser: CurrentUser,
    ): OrganizationMembership {
        val membership = membershipService.requireActiveMembership(organizationId, currentUser)
        if (isManager(membership)) return membership

        val isGuardian = authorizationService.hasGuardianRelationshipInOrganization(organizationId, currentUser)
        val coachTeamIds =
            authorizationService.listAccessibleTeamIds(
                organizationId,
                currentUser,
                Capabilities.TEAM_FUNDRAISING_CREATE,
            )
        val isCoach = coachTeamIds.isNotEmpty()
        if (!isGuardian && !isCoach) {
            throw ForbiddenException(
                "FUNDRAISER_CREATE_DENIED",
                "Only organization managers, assigned coaches, and guardians can create fundraisers.",
            )
        }
        if (teamId != null) {
            val guardianHasTeam =
                isGuardian && authorizationService.hasGuardianRelationshipForTeam(organizationId, teamId, currentUser)
            val coachHasTeam = isCoach && teamId in coachTeamIds
            if (!guardianHasTeam && !coachHasTeam) {
                throw ForbiddenException(
                    "FUNDRAISER_TEAM_ACCESS_DENIED",
                    "You can only create a team fundraiser for a team connected to your coach or guardian access.",
                )
            }
        }
        return membership
    }

    private fun requireCanEdit(
        campaign: Campaign,
        membership: OrganizationMembership,
        currentUser: CurrentUser,
    ) {
        if (isManager(membership)) return
        if (campaign.createdByUserId != currentUser.userId) {
            throw ForbiddenException(
                "FUNDRAISER_EDIT_DENIED",
                "You can only edit fundraisers that you created.",
            )
        }
        requireCreatorAccess(campaign.organizationId, campaign.teamId, currentUser)
    }

    private fun requireCanRequestActivation(
        campaign: Campaign,
        membership: OrganizationMembership,
        currentUser: CurrentUser,
    ) {
        if (isManager(membership)) return
        if (campaign.createdByUserId != currentUser.userId) {
            throw ForbiddenException(
                "FUNDRAISER_ACTIVATION_DENIED",
                "You can only submit or activate fundraisers that you created.",
            )
        }
        requireCreatorAccess(campaign.organizationId, campaign.teamId, currentUser)
    }

    private fun isManager(membership: OrganizationMembership): Boolean =
        membership.role == MembershipRole.OWNER || membership.role == MembershipRole.ADMINISTRATOR

    private fun isOwner(membership: OrganizationMembership): Boolean = membership.role == MembershipRole.OWNER

    private fun requireCampaign(
        organizationId: UUID,
        campaignId: UUID,
    ): Campaign =
        campaignRepository.findById(campaignId, organizationId)
            ?: throw NotFoundException("CAMPAIGN_NOT_FOUND", "The campaign could not be found.")

    private fun validateEventLocation(
        templateKey: FundraiserTemplateKey?,
        eventLocationName: String?,
        eventAddress: String?,
    ) {
        if ((eventLocationName?.length ?: 0) > 160) {
            throw ValidationException(
                "Event location name is too long.",
                listOf(FieldError("eventLocationName", "Use 160 characters or fewer.")),
            )
        }
        if ((eventAddress?.length ?: 0) > 500) {
            throw ValidationException(
                "Event address is too long.",
                listOf(FieldError("eventAddress", "Use 500 characters or fewer.")),
            )
        }
        val inPersonTemplate =
            templateKey == FundraiserTemplateKey.IN_PERSON_EVENT ||
                templateKey == FundraiserTemplateKey.BAKE_SALE ||
                templateKey == FundraiserTemplateKey.CAR_WASH
        if (inPersonTemplate && eventLocationName.isNullOrBlank() && eventAddress.isNullOrBlank()) {
            throw ValidationException(
                "In-person fundraisers need a location.",
                listOf(FieldError("eventLocationName", "Add a venue name or address.")),
            )
        }
    }

    private fun validateSlug(slug: String) {
        if (!isValidCampaignSlug(slug)) {
            throw ValidationException(
                "Slug must be lowercase alphanumeric with optional hyphens.",
                listOf(FieldError("slug", "Invalid slug format.")),
            )
        }
    }

    private fun validateDateRange(
        startDate: LocalDate?,
        endDate: LocalDate?,
    ) {
        if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
            throw ValidationException(
                "End date must be on or after the start date.",
                listOf(FieldError("endDate", "End date must be on or after the start date.")),
            )
        }
    }
}
