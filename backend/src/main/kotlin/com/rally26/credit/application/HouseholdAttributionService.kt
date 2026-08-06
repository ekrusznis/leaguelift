package com.rally26.credit.application

import com.rally26.authorization.application.AuthorizationService
import com.rally26.authorization.domain.Capabilities
import com.rally26.common.error.ForbiddenException
import com.rally26.common.error.NotFoundException
import com.rally26.common.web.CurrentUser
import com.rally26.credit.domain.CampaignHouseholdAttributionLink
import com.rally26.credit.persistence.CampaignHouseholdAttributionLinkRepository
import com.rally26.fundraising.persistence.CampaignRepository
import com.rally26.household.persistence.HouseholdRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.SecureRandom
import java.util.UUID

private val CODE_ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ" // no 0/O/1/I — avoids visual ambiguity when shared aloud/printed
private const val CODE_LENGTH = 8
private const val MAX_GENERATION_ATTEMPTS = 5

/**
 * Household attribution for campaign contributions (Phase 23, DESIGN-DOC.md
 * section 13/14.1, resolving section 19.3 #7). A household's link is always
 * privately code-tracked; the supporter-facing public display name is a
 * separate, opt-in field — matching the founder's "both, supporter's choice"
 * decision. Scoped to campaigns specifically (the only real, already-shipped
 * source of attributable revenue) rather than a generic cross-feature
 * abstraction.
 */
@Service
class HouseholdAttributionService(
    private val linkRepository: CampaignHouseholdAttributionLinkRepository,
    private val householdRepository: HouseholdRepository,
    private val campaignRepository: CampaignRepository,
    private val authorizationService: AuthorizationService,
) {
    @Transactional
    fun getOrCreateLink(
        organizationId: UUID,
        householdId: UUID,
        campaignId: UUID,
        currentUser: CurrentUser,
    ): CampaignHouseholdAttributionLink {
        householdRepository.findById(householdId, organizationId)
            ?: throw NotFoundException("HOUSEHOLD_NOT_FOUND", "The household could not be found.")
        if (!authorizationService.hasHouseholdCapability(organizationId, householdId, currentUser, Capabilities.HOUSEHOLD_FEE_VIEW)) {
            throw ForbiddenException("HOUSEHOLD_ACCESS_DENIED", "You do not have access to this household.")
        }
        campaignRepository.findById(campaignId, organizationId)
            ?: throw NotFoundException("CAMPAIGN_NOT_FOUND", "The campaign could not be found.")
        linkRepository.findForHouseholdAndCampaign(organizationId, householdId, campaignId)?.let { return it }
        return insertWithRetry(organizationId, householdId, campaignId)
    }

    private fun insertWithRetry(
        organizationId: UUID,
        householdId: UUID,
        campaignId: UUID,
    ): CampaignHouseholdAttributionLink {
        repeat(MAX_GENERATION_ATTEMPTS) {
            try {
                return linkRepository.insert(organizationId, householdId, campaignId, generateCode())
            } catch (e: DataIntegrityViolationException) {
                // Unique-constraint collision on `code` (astronomically rare at this alphabet/length) or a
                // concurrent request already created this household+campaign's link — check for the latter.
                linkRepository.findForHouseholdAndCampaign(organizationId, householdId, campaignId)?.let { return it }
            }
        }
        error("Could not generate a unique attribution code after $MAX_GENERATION_ATTEMPTS attempts")
    }

    /** Public — resolved by the anonymous contribution-checkout flow, no auth required (matches the public campaign page itself). */
    fun resolveByCode(
        organizationId: UUID,
        campaignId: UUID,
        code: String,
    ): UUID? = linkRepository.findByCode(organizationId, campaignId, code.trim().uppercase())?.householdId

    @Transactional
    fun setPublicDisplayName(
        organizationId: UUID,
        householdId: UUID,
        campaignId: UUID,
        publicDisplayName: String?,
        currentUser: CurrentUser,
    ): CampaignHouseholdAttributionLink {
        val link = getOrCreateLink(organizationId, householdId, campaignId, currentUser)
        linkRepository.updatePublicDisplayName(organizationId, link.id, publicDisplayName?.trim()?.take(120)?.ifBlank { null })
        return linkRepository.findForHouseholdAndCampaign(organizationId, householdId, campaignId)!!
    }

    private fun generateCode(): String {
        val random = SecureRandom()
        return (1..CODE_LENGTH).map { CODE_ALPHABET[random.nextInt(CODE_ALPHABET.length)] }.joinToString("")
    }
}
