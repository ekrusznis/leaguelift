package com.rally26.social.application

import com.rally26.common.error.NotFoundException
import com.rally26.common.error.ValidationException
import com.rally26.common.web.CurrentUser
import com.rally26.config.FrontendProperties
import com.rally26.fundraising.application.CampaignService
import com.rally26.fundraising.application.ContributionService
import com.rally26.integration.core.domain.IntegrationProvider
import com.rally26.membership.application.MembershipService
import com.rally26.social.domain.SocialDraftSourceType
import com.rally26.social.domain.SocialPostDraft
import com.rally26.social.persistence.SocialPostDraftRepository
import org.springframework.stereotype.Service
import java.util.Locale
import java.util.UUID

private val SOCIAL_PROVIDERS = listOf(IntegrationProvider.INSTAGRAM, IntegrationProvider.FACEBOOK, IntegrationProvider.X)

/**
 * Social Sharing & Connected Accounts, Slice 3 (brief §7's `SocialPostDraft`). Only
 * [SocialDraftSourceType.FUNDRAISER] is wired to real content this pass — see
 * [SocialPostDraft]'s doc comment. Each source type gets its own generator here
 * rather than a generic one, since the caption/URL shape genuinely differs per
 * content type (brief §5's examples aren't interchangeable templates).
 */
@Service
class SocialDraftService(
    private val draftRepository: SocialPostDraftRepository,
    private val campaignService: CampaignService,
    private val contributionService: ContributionService,
    private val membershipService: MembershipService,
    private val frontendProperties: FrontendProperties,
) {
    fun createDraft(
        organizationId: UUID,
        sourceType: SocialDraftSourceType,
        sourceId: UUID,
        currentUser: CurrentUser,
    ): SocialPostDraft {
        membershipService.requireActiveMembership(organizationId, currentUser)
        return when (sourceType) {
            SocialDraftSourceType.FUNDRAISER -> fundraiserDraft(organizationId, sourceId, currentUser)
            else ->
                throw ValidationException(
                    "Sharing ${sourceType.name.lowercase().replace('_', ' ')} content isn't available yet.",
                )
        }
    }

    fun findForUser(
        id: UUID,
        currentUser: CurrentUser,
    ): SocialPostDraft =
        draftRepository.findByIdForUser(id, currentUser.userId)
            ?: throw NotFoundException("SOCIAL_DRAFT_NOT_FOUND", "The social post draft could not be found.")

    fun updateCaption(
        id: UUID,
        caption: String,
        currentUser: CurrentUser,
    ): SocialPostDraft {
        if (caption.isBlank()) throw ValidationException("The caption can't be empty.")
        return draftRepository.updateCaption(id, currentUser.userId, caption)
            ?: throw NotFoundException("SOCIAL_DRAFT_NOT_FOUND", "The social post draft could not be found.")
    }

    private fun fundraiserDraft(
        organizationId: UUID,
        campaignId: UUID,
        currentUser: CurrentUser,
    ): SocialPostDraft {
        val campaign = campaignService.get(organizationId, campaignId, currentUser)
        val raisedMinor = contributionService.getConfirmedTotal(campaignId)
        val publicUrl = "${frontendProperties.baseUrl.trimEnd('/')}/campaigns/${campaign.slug}"
        val caption =
            buildString {
                append("Help ${campaign.name} reach its goal! \n\n")
                append("Every contribution helps support our athletes and their season.\n\n")
                append(
                    "${formatMinor(
                        raisedMinor,
                        campaign.currency,
                    )} of ${formatMinor(campaign.goalAmountMinor, campaign.currency)} raised\n\n",
                )
                append("Support the team:\n")
                append(publicUrl)
            }
        return draftRepository.insert(
            sourceType = SocialDraftSourceType.FUNDRAISER,
            sourceId = campaignId,
            organizationId = organizationId,
            teamId = campaign.teamId,
            title = campaign.name,
            caption = caption,
            publicUrl = publicUrl,
            allowedProviders = SOCIAL_PROVIDERS,
            createdByUserId = currentUser.userId,
        )
    }

    /** USD-only for now — every real fundraiser in this codebase uses "USD" (matches DESIGN-DOC's current scope); revisit if/when multi-currency campaigns ship. */
    private fun formatMinor(
        amountMinor: Long,
        currency: String,
    ): String {
        val symbol = if (currency == "USD") "$" else "$currency "
        return String.format(Locale.US, "%s%,.2f", symbol, amountMinor / 100.0)
    }
}
