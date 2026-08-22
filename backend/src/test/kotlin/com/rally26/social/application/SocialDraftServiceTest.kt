package com.rally26.social.application

import com.rally26.common.error.NotFoundException
import com.rally26.common.error.ValidationException
import com.rally26.common.web.CurrentUser
import com.rally26.config.FrontendProperties
import com.rally26.fundraising.application.CampaignService
import com.rally26.fundraising.application.ContributionService
import com.rally26.fundraising.domain.Campaign
import com.rally26.fundraising.domain.CampaignStatus
import com.rally26.fundraising.domain.CampaignType
import com.rally26.integration.core.domain.IntegrationProvider
import com.rally26.membership.application.MembershipService
import com.rally26.social.domain.SocialDraftSourceType
import com.rally26.social.domain.SocialPostDraft
import com.rally26.social.persistence.SocialPostDraftRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SocialDraftServiceTest {
    private val draftRepository = mockk<SocialPostDraftRepository>()
    private val campaignService = mockk<CampaignService>()
    private val contributionService = mockk<ContributionService>()
    private val membershipService = mockk<MembershipService>(relaxed = true)
    private val frontendProperties = FrontendProperties(baseUrl = "https://rally26.com")
    private val service =
        SocialDraftService(draftRepository, campaignService, contributionService, membershipService, frontendProperties)

    private val organizationId = UUID.randomUUID()
    private val campaignId = UUID.randomUUID()
    private val currentUser = CurrentUser(UUID.randomUUID(), "owner@example.com", "Owner")

    private fun campaign() =
        Campaign(
            id = campaignId,
            organizationId = organizationId,
            teamId = null,
            name = "12U National",
            slug = "12u-national-2026",
            description = null,
            campaignType = CampaignType.TEAM_GENERAL,
            goalAmountMinor = 500000,
            currency = "USD",
            startDate = null,
            endDate = null,
            status = CampaignStatus.ACTIVE,
            publishedAt = Instant.now(),
            createdByUserId = currentUser.userId,
            templateKey = null,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
        )

    @Test
    fun `a fundraiser draft has a real generated caption, public URL, and all 3 social providers allowed`() {
        every { campaignService.get(organizationId, campaignId, currentUser) } returns campaign()
        every { contributionService.getConfirmedTotal(campaignId) } returns 385000
        val captionSlot = slot<String>()
        val publicUrlSlot = slot<String>()
        every {
            draftRepository.insert(
                SocialDraftSourceType.FUNDRAISER,
                campaignId,
                organizationId,
                null,
                "12U National",
                capture(captionSlot),
                capture(publicUrlSlot),
                listOf(IntegrationProvider.INSTAGRAM, IntegrationProvider.FACEBOOK, IntegrationProvider.X),
                currentUser.userId,
            )
        } returns mockk(relaxed = true)

        service.createDraft(organizationId, SocialDraftSourceType.FUNDRAISER, campaignId, currentUser)

        assertEquals("https://rally26.com/campaigns/12u-national-2026", publicUrlSlot.captured)
        assertTrue(captionSlot.captured.contains("$3,850.00 of $5,000.00 raised"))
        assertTrue(captionSlot.captured.contains(publicUrlSlot.captured))
    }

    @Test
    fun `requesting a draft for a not-yet-wired source type fails with a clear error, not silently`() {
        assertFailsWith<ValidationException> {
            service.createDraft(organizationId, SocialDraftSourceType.EVENT, UUID.randomUUID(), currentUser)
        }
    }

    @Test
    fun `updateCaption persists the edited caption so it's what actually gets published`() {
        val draftId = UUID.randomUUID()
        val updated =
            SocialPostDraft(
                id = draftId,
                sourceType = SocialDraftSourceType.FUNDRAISER,
                sourceId = campaignId,
                organizationId = organizationId,
                teamId = null,
                title = "12U National",
                caption = "Edited caption",
                publicUrl = "https://rally26.com/campaigns/12u-national-2026",
                allowedProviders = listOf(IntegrationProvider.X),
                createdByUserId = currentUser.userId,
                createdAt = Instant.now(),
            )
        every { draftRepository.updateCaption(draftId, currentUser.userId, "Edited caption") } returns updated

        val result = service.updateCaption(draftId, "Edited caption", currentUser)

        assertEquals("Edited caption", result.caption)
    }

    @Test
    fun `updateCaption rejects a blank caption before touching the repository`() {
        assertFailsWith<ValidationException> {
            service.updateCaption(UUID.randomUUID(), "   ", currentUser)
        }
    }

    @Test
    fun `updateCaption on someone else's draft fails as not found, not silently succeeding`() {
        val draftId = UUID.randomUUID()
        every { draftRepository.updateCaption(draftId, currentUser.userId, "New caption") } returns null

        assertFailsWith<NotFoundException> {
            service.updateCaption(draftId, "New caption", currentUser)
        }
    }
}
