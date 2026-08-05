package com.rally26.fundraising.application

import com.rally26.audit.application.AuditService
import com.rally26.common.error.ConflictException
import com.rally26.common.error.FieldError
import com.rally26.common.error.NotFoundException
import com.rally26.common.error.ValidationException
import com.rally26.common.web.CurrentUser
import com.rally26.fundraising.domain.Campaign
import com.rally26.fundraising.domain.CampaignStatus
import com.rally26.fundraising.domain.CampaignType
import com.rally26.fundraising.domain.isValidCampaignSlug
import com.rally26.fundraising.persistence.CampaignRepository
import com.rally26.membership.application.MembershipService
import com.rally26.team.persistence.TeamRepository
import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Service
class CampaignService(
    private val campaignRepository: CampaignRepository,
    private val teamRepository: TeamRepository,
    private val membershipService: MembershipService,
    private val auditService: AuditService,
) {
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
        return campaignRepository.findById(campaignId, organizationId)
            ?: throw NotFoundException("CAMPAIGN_NOT_FOUND", "The campaign could not be found.")
    }

    /** Only campaigns that have been published at least once are publicly visible (section 16.6). */
    fun getPublic(slug: String): Campaign {
        val campaign =
            campaignRepository.findBySlug(slug)
                ?: throw NotFoundException("CAMPAIGN_NOT_FOUND", "The campaign could not be found.")
        if (campaign.status != CampaignStatus.ACTIVE && campaign.status != CampaignStatus.COMPLETED) {
            throw NotFoundException("CAMPAIGN_NOT_FOUND", "The campaign could not be found.")
        }
        return campaign
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
    ): Campaign {
        membershipService.requireManagerRole(organizationId, currentUser)
        validateSlug(slug)
        validateDateRange(startDate, endDate)
        if (teamId != null) {
            teamRepository.findById(teamId, organizationId)
                ?: throw NotFoundException("TEAM_NOT_FOUND", "The team could not be found.")
        }
        return try {
            val campaign =
                campaignRepository.insert(
                    organizationId,
                    teamId,
                    name,
                    slug,
                    description,
                    campaignType,
                    goalAmountMinor,
                    currency,
                    startDate,
                    endDate,
                )
            auditService.record(currentUser.userId, organizationId, "campaign.created", "campaign", campaign.id)
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
    ): Campaign {
        membershipService.requireManagerRole(organizationId, currentUser)
        val existing =
            campaignRepository.findById(campaignId, organizationId)
                ?: throw NotFoundException("CAMPAIGN_NOT_FOUND", "The campaign could not be found.")
        validateDateRange(startDate ?: existing.startDate, endDate ?: existing.endDate)
        campaignRepository.update(campaignId, organizationId, name, description, goalAmountMinor, startDate, endDate)
        auditService.record(currentUser.userId, organizationId, "campaign.updated", "campaign", campaignId)
        return campaignRepository.findById(campaignId, organizationId)!!
    }

    @Transactional
    fun publish(
        organizationId: UUID,
        campaignId: UUID,
        currentUser: CurrentUser,
    ): Campaign {
        membershipService.requireManagerRole(organizationId, currentUser)
        val campaign =
            campaignRepository.findById(campaignId, organizationId)
                ?: throw NotFoundException("CAMPAIGN_NOT_FOUND", "The campaign could not be found.")
        if (campaign.status == CampaignStatus.ACTIVE || campaign.status == CampaignStatus.COMPLETED) return campaign
        if (campaign.status == CampaignStatus.ARCHIVED) {
            throw ValidationException("An archived campaign cannot be published.")
        }
        campaignRepository.updateStatus(campaignId, organizationId, CampaignStatus.ACTIVE, Instant.now())
        auditService.record(currentUser.userId, organizationId, "campaign.published", "campaign", campaignId)
        return campaignRepository.findById(campaignId, organizationId)!!
    }

    @Transactional
    fun updateStatus(
        organizationId: UUID,
        campaignId: UUID,
        status: CampaignStatus,
        currentUser: CurrentUser,
    ): Campaign {
        membershipService.requireManagerRole(organizationId, currentUser)
        campaignRepository.findById(campaignId, organizationId)
            ?: throw NotFoundException("CAMPAIGN_NOT_FOUND", "The campaign could not be found.")
        campaignRepository.updateStatus(campaignId, organizationId, status, null)
        auditService.record(currentUser.userId, organizationId, "campaign.status_updated", "campaign", campaignId)
        return campaignRepository.findById(campaignId, organizationId)!!
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
