package com.rally26.fundraising.application

import com.rally26.audit.application.AuditService
import com.rally26.common.error.ConflictException
import com.rally26.common.error.NotFoundException
import com.rally26.common.error.ValidationException
import com.rally26.common.web.CurrentUser
import com.rally26.fundraising.domain.Campaign
import com.rally26.fundraising.domain.CampaignStatus
import com.rally26.fundraising.domain.CampaignType
import com.rally26.fundraising.persistence.CampaignRepository
import com.rally26.membership.application.MembershipService
import com.rally26.membership.domain.MembershipRole
import com.rally26.membership.domain.MembershipStatus
import com.rally26.membership.domain.OrganizationMembership
import com.rally26.team.persistence.TeamRepository
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.springframework.dao.DuplicateKeyException
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CampaignServiceTest {
    private val campaignRepository = mockk<CampaignRepository>()
    private val teamRepository = mockk<TeamRepository>()
    private val membershipService = mockk<MembershipService>()
    private val auditService = mockk<AuditService>()
    private val service = CampaignService(campaignRepository, teamRepository, membershipService, auditService)

    private val orgId = UUID.randomUUID()
    private val teamId = UUID.randomUUID()
    private val currentUser = CurrentUser(UUID.randomUUID(), "manager@example.com", "Manager")

    @Test
    fun `list requires active membership`() {
        every { membershipService.requireActiveMembership(orgId, currentUser) } returns managerMembership()
        every { campaignRepository.findAll(orgId, 0, 20) } returns emptyList()

        service.list(orgId, currentUser, 0, 20)

        verify(exactly = 1) { membershipService.requireActiveMembership(orgId, currentUser) }
    }

    @Test
    fun `get throws NotFoundException when campaign does not exist`() {
        every { membershipService.requireActiveMembership(orgId, currentUser) } returns managerMembership()
        every { campaignRepository.findById(any(), orgId) } returns null

        assertFailsWith<NotFoundException> {
            service.get(orgId, UUID.randomUUID(), currentUser)
        }
    }

    @Test
    fun `getPublic throws NotFoundException for a draft campaign`() {
        val campaign = sampleCampaign(status = CampaignStatus.DRAFT)
        every { campaignRepository.findBySlug(campaign.slug) } returns campaign

        assertFailsWith<NotFoundException> {
            service.getPublic(campaign.slug)
        }
    }

    @Test
    fun `getPublic returns an active campaign`() {
        val campaign = sampleCampaign(status = CampaignStatus.ACTIVE)
        every { campaignRepository.findBySlug(campaign.slug) } returns campaign

        val result = service.getPublic(campaign.slug)

        assertEquals(campaign.id, result.id)
    }

    @Test
    fun `create requires manager role, validates the team, and records audit`() {
        every { membershipService.requireManagerRole(orgId, currentUser) } returns managerMembership()
        every { teamRepository.findById(teamId, orgId) } returns mockk()
        val campaign = sampleCampaign(teamId = teamId)
        every {
            campaignRepository.insert(
                orgId,
                teamId,
                campaign.name,
                campaign.slug,
                campaign.description,
                campaign.campaignType,
                campaign.goalAmountMinor,
                campaign.currency,
                campaign.startDate,
                campaign.endDate,
                currentUser.userId,
                null,
            )
        } returns campaign
        every { auditService.record(any(), any(), any(), any(), any(), any()) } just runs

        val result =
            service.create(
                orgId,
                teamId,
                campaign.name,
                campaign.slug,
                campaign.description,
                campaign.campaignType,
                campaign.goalAmountMinor,
                campaign.currency,
                campaign.startDate,
                campaign.endDate,
                currentUser,
            )

        assertEquals(campaign.id, result.id)
        verify(exactly = 1) { membershipService.requireManagerRole(orgId, currentUser) }
        verify(exactly = 1) { auditService.record(currentUser.userId, orgId, "campaign.created", "campaign", campaign.id, any()) }
    }

    @Test
    fun `create throws NotFoundException when the team does not belong to the organization`() {
        every { membershipService.requireManagerRole(orgId, currentUser) } returns managerMembership()
        every { teamRepository.findById(teamId, orgId) } returns null

        assertFailsWith<NotFoundException> {
            service.create(
                orgId,
                teamId,
                "Spring Trip",
                "spring-trip",
                null,
                CampaignType.TRAVEL,
                100000L,
                "USD",
                null,
                null,
                currentUser,
            )
        }
    }

    @Test
    fun `create throws ValidationException for an invalid slug`() {
        every { membershipService.requireManagerRole(orgId, currentUser) } returns managerMembership()

        assertFailsWith<ValidationException> {
            service.create(
                orgId,
                null,
                "Spring Trip",
                "Not A Valid Slug!",
                null,
                CampaignType.TRAVEL,
                100000L,
                "USD",
                null,
                null,
                currentUser,
            )
        }
    }

    @Test
    fun `create throws ValidationException when end date is before start date`() {
        every { membershipService.requireManagerRole(orgId, currentUser) } returns managerMembership()

        assertFailsWith<ValidationException> {
            service.create(
                orgId,
                null,
                "Spring Trip",
                "spring-trip",
                null,
                CampaignType.TRAVEL,
                100000L,
                "USD",
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 1, 1),
                currentUser,
            )
        }
    }

    @Test
    fun `create throws ConflictException when the slug is already taken`() {
        every { membershipService.requireManagerRole(orgId, currentUser) } returns managerMembership()
        every {
            campaignRepository.insert(orgId, null, any(), "spring-trip", any(), any(), any(), any(), any(), any(), any(), any())
        } throws DuplicateKeyException("duplicate")

        assertFailsWith<ConflictException> {
            service.create(
                orgId,
                null,
                "Spring Trip",
                "spring-trip",
                null,
                CampaignType.TRAVEL,
                100000L,
                "USD",
                null,
                null,
                currentUser,
            )
        }
    }

    @Test
    fun `update throws NotFoundException when campaign does not exist`() {
        every { membershipService.requireManagerRole(orgId, currentUser) } returns managerMembership()
        every { campaignRepository.findById(any(), orgId) } returns null

        assertFailsWith<NotFoundException> {
            service.update(orgId, UUID.randomUUID(), "New Name", null, null, null, null, currentUser)
        }
    }

    @Test
    fun `publish transitions a draft campaign to active and records audit`() {
        val draft = sampleCampaign(status = CampaignStatus.DRAFT)
        val published = draft.copy(status = CampaignStatus.ACTIVE, publishedAt = Instant.now())
        every { membershipService.requireManagerRole(orgId, currentUser) } returns managerMembership()
        every { campaignRepository.findById(draft.id, orgId) } returns draft andThen published
        every { campaignRepository.updateStatus(draft.id, orgId, CampaignStatus.ACTIVE, any()) } returns 1
        every { auditService.record(any(), any(), any(), any(), any(), any()) } just runs

        val result = service.publish(orgId, draft.id, currentUser)

        assertEquals(CampaignStatus.ACTIVE, result.status)
        verify(exactly = 1) { auditService.record(currentUser.userId, orgId, "campaign.published", "campaign", draft.id, any()) }
    }

    @Test
    fun `publish is idempotent and does not re-record audit when already active`() {
        val active = sampleCampaign(status = CampaignStatus.ACTIVE)
        every { membershipService.requireManagerRole(orgId, currentUser) } returns managerMembership()
        every { campaignRepository.findById(active.id, orgId) } returns active

        val result = service.publish(orgId, active.id, currentUser)

        assertEquals(CampaignStatus.ACTIVE, result.status)
        verify(exactly = 0) { auditService.record(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `publish throws ValidationException for an archived campaign`() {
        val archived = sampleCampaign(status = CampaignStatus.ARCHIVED)
        every { membershipService.requireManagerRole(orgId, currentUser) } returns managerMembership()
        every { campaignRepository.findById(archived.id, orgId) } returns archived

        assertFailsWith<ValidationException> {
            service.publish(orgId, archived.id, currentUser)
        }
    }

    @Test
    fun `updateStatus records audit on success`() {
        val campaign = sampleCampaign(status = CampaignStatus.ACTIVE)
        val completed = campaign.copy(status = CampaignStatus.COMPLETED)
        every { membershipService.requireManagerRole(orgId, currentUser) } returns managerMembership()
        every { campaignRepository.findById(campaign.id, orgId) } returns campaign andThen completed
        every { campaignRepository.updateStatus(campaign.id, orgId, CampaignStatus.COMPLETED, null) } returns 1
        every { auditService.record(any(), any(), any(), any(), any(), any()) } just runs

        val result = service.updateStatus(orgId, campaign.id, CampaignStatus.COMPLETED, currentUser)

        assertEquals(CampaignStatus.COMPLETED, result.status)
        verify(exactly = 1) { auditService.record(currentUser.userId, orgId, "campaign.status_updated", "campaign", campaign.id, any()) }
    }

    private fun sampleCampaign(
        teamId: UUID? = null,
        status: CampaignStatus = CampaignStatus.DRAFT,
    ) = Campaign(
        id = UUID.randomUUID(),
        organizationId = orgId,
        teamId = teamId,
        name = "Spring Trip",
        slug = "spring-trip-${UUID.randomUUID()}",
        description = "Help send the team to regionals.",
        campaignType = CampaignType.TRAVEL,
        goalAmountMinor = 400000L,
        currency = "USD",
        startDate = LocalDate.of(2026, 1, 1),
        endDate = LocalDate.of(2026, 6, 1),
        status = status,
        publishedAt = if (status == CampaignStatus.ACTIVE || status == CampaignStatus.COMPLETED) Instant.now() else null,
        createdByUserId = null,
        templateKey = null,
        createdAt = Instant.now(),
        updatedAt = Instant.now(),
    )

    private fun managerMembership() =
        OrganizationMembership(
            id = UUID.randomUUID(),
            organizationId = orgId,
            userId = currentUser.userId,
            role = MembershipRole.ADMINISTRATOR,
            status = MembershipStatus.ACTIVE,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
        )
}
