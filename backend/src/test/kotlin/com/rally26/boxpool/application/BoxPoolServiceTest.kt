package com.rally26.boxpool.application

import com.rally26.audit.application.AuditService
import com.rally26.boxpool.domain.BoxPool
import com.rally26.boxpool.domain.BoxPoolBox
import com.rally26.boxpool.domain.BoxPoolBoxStatus
import com.rally26.boxpool.persistence.BoxPoolBoxRepository
import com.rally26.boxpool.persistence.BoxPoolRepository
import com.rally26.common.error.ConflictException
import com.rally26.common.error.NotFoundException
import com.rally26.common.error.ValidationException
import com.rally26.common.web.CurrentUser
import com.rally26.fundraising.application.ContributionCheckout
import com.rally26.fundraising.application.ContributionService
import com.rally26.fundraising.domain.Campaign
import com.rally26.fundraising.domain.CampaignStatus
import com.rally26.fundraising.domain.CampaignType
import com.rally26.fundraising.domain.FundraiserTemplateKey
import com.rally26.fundraising.persistence.CampaignRepository
import com.rally26.membership.application.MembershipService
import com.rally26.membership.domain.MembershipRole
import com.rally26.membership.domain.MembershipStatus
import com.rally26.membership.domain.OrganizationMembership
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BoxPoolServiceTest {
    private val boxPoolRepository = mockk<BoxPoolRepository>()
    private val boxPoolBoxRepository = mockk<BoxPoolBoxRepository>()
    private val campaignRepository = mockk<CampaignRepository>()
    private val contributionService = mockk<ContributionService>()
    private val membershipService = mockk<MembershipService>()
    private val auditService = mockk<AuditService>(relaxed = true)
    private val service =
        BoxPoolService(boxPoolRepository, boxPoolBoxRepository, campaignRepository, contributionService, membershipService, auditService)

    private val orgId = UUID.randomUUID()
    private val campaignId = UUID.randomUUID()
    private val currentUser = CurrentUser(UUID.randomUUID(), "manager@example.com", "Manager")

    private fun managerMembership() =
        OrganizationMembership(UUID.randomUUID(), orgId, currentUser.userId, MembershipRole.ADMINISTRATOR, MembershipStatus.ACTIVE, Instant.now(), Instant.now())

    private fun campaign(templateKey: FundraiserTemplateKey? = FundraiserTemplateKey.BOX_POOL) =
        Campaign(
            id = campaignId,
            organizationId = orgId,
            teamId = null,
            name = "Playoff Pool",
            slug = "playoff-pool",
            description = null,
            campaignType = CampaignType.SPECIAL_EVENTS,
            goalAmountMinor = 0,
            currency = "USD",
            startDate = null,
            endDate = null,
            status = CampaignStatus.ACTIVE,
            publishedAt = Instant.now(),
            createdByUserId = currentUser.userId,
            templateKey = templateKey,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
        )

    private fun pool() =
        BoxPool(UUID.randomUUID(), campaignId, orgId, "FOOTBALL", 10, 10, 500, "Home", "Away", "Winner takes the pot", Instant.now(), Instant.now())

    private fun box(status: BoxPoolBoxStatus = BoxPoolBoxStatus.OPEN) =
        BoxPoolBox(UUID.randomUUID(), pool().id, 0, 0, status, null, null, null, null, null, Instant.now(), Instant.now())

    @Test
    fun `create requires manager role and the campaign to use the BOX_POOL template`() {
        every { membershipService.requireManagerRole(orgId, currentUser) } returns managerMembership()
        every { campaignRepository.findById(campaignId, orgId) } returns campaign(templateKey = FundraiserTemplateKey.BAKE_SALE)

        assertFailsWith<ValidationException> {
            service.create(orgId, campaignId, "FOOTBALL", 10, 10, 500, "Home", "Away", null, currentUser)
        }
    }

    @Test
    fun `create rejects a campaign that already has a box pool`() {
        every { membershipService.requireManagerRole(orgId, currentUser) } returns managerMembership()
        every { campaignRepository.findById(campaignId, orgId) } returns campaign()
        every { boxPoolRepository.findByCampaignId(campaignId) } returns pool()

        assertFailsWith<ConflictException> {
            service.create(orgId, campaignId, "FOOTBALL", 10, 10, 500, "Home", "Away", null, currentUser)
        }
    }

    @Test
    fun `create inserts the pool and auto-creates the full grid`() {
        every { membershipService.requireManagerRole(orgId, currentUser) } returns managerMembership()
        every { campaignRepository.findById(campaignId, orgId) } returns campaign()
        every { boxPoolRepository.findByCampaignId(campaignId) } returns null
        val created = pool()
        every { boxPoolRepository.insert(campaignId, orgId, "FOOTBALL", 10, 10, 500, "Home", "Away", "Winner takes the pot") } returns created
        every { boxPoolBoxRepository.insertGrid(created.id, 10, 10) } returns emptyList()

        val result = service.create(orgId, campaignId, "FOOTBALL", 10, 10, 500, "Home", "Away", "Winner takes the pot", currentUser)

        assertEquals(created.id, result.id)
        verify(exactly = 1) { boxPoolBoxRepository.insertGrid(created.id, 10, 10) }
        verify(exactly = 1) { auditService.record(currentUser.userId, orgId, "box_pool.created", "box_pool", created.id) }
    }

    @Test
    fun `create rejects an out-of-range grid size`() {
        every { membershipService.requireManagerRole(orgId, currentUser) } returns managerMembership()
        every { campaignRepository.findById(campaignId, orgId) } returns campaign()
        every { boxPoolRepository.findByCampaignId(campaignId) } returns null

        assertFailsWith<ValidationException> {
            service.create(orgId, campaignId, "FOOTBALL", 27, 10, 500, null, null, null, currentUser)
        }
    }

    @Test
    fun `reserveBox reuses ContributionService checkout and reserves the specific box`() {
        val slug = "playoff-pool"
        val theCampaign = campaign()
        val thePool = pool()
        val openBox = box(BoxPoolBoxStatus.OPEN)
        val checkout = ContributionCheckout(UUID.randomUUID(), "https://checkout.stripe.com/test")
        every { campaignRepository.findBySlug(slug) } returns theCampaign
        every { boxPoolRepository.findByCampaignId(theCampaign.id) } returns thePool
        every { boxPoolBoxRepository.findClaimableByPosition(thePool.id, 0, 0) } returns openBox
        every {
            contributionService.createCheckoutSession(slug, thePool.pricePerBoxMinor, "Jamie", false, "jamie@example.com", "https://success", "https://cancel")
        } returns checkout
        every { boxPoolBoxRepository.reserve(openBox.id, "Jamie", "jamie@example.com", checkout.contributionId, any()) } returns 1

        val result = service.reserveBox(slug, 0, 0, "Jamie", "jamie@example.com", "https://success", "https://cancel")

        assertEquals(checkout.checkoutUrl, result.checkoutUrl)
        verify(exactly = 1) { boxPoolBoxRepository.reserve(openBox.id, "Jamie", "jamie@example.com", checkout.contributionId, any()) }
    }

    @Test
    fun `reserveBox throws ConflictException when the box is already taken`() {
        val slug = "playoff-pool"
        every { campaignRepository.findBySlug(slug) } returns campaign()
        every { boxPoolRepository.findByCampaignId(campaignId) } returns pool()
        every { boxPoolBoxRepository.findClaimableByPosition(any(), 0, 0) } returns null

        assertFailsWith<ConflictException> {
            service.reserveBox(slug, 0, 0, "Jamie", "jamie@example.com", "https://success", "https://cancel")
        }
    }

    @Test
    fun `reserveBox throws NotFoundException when the campaign has no box pool`() {
        val slug = "playoff-pool"
        every { campaignRepository.findBySlug(slug) } returns campaign()
        every { boxPoolRepository.findByCampaignId(campaignId) } returns null

        assertFailsWith<NotFoundException> {
            service.reserveBox(slug, 0, 0, "Jamie", "jamie@example.com", "https://success", "https://cancel")
        }
    }
}
