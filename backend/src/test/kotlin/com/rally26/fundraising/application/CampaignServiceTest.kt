package com.rally26.fundraising.application

import com.rally26.audit.application.AuditService
import com.rally26.authorization.application.AuthorizationService
import com.rally26.authorization.domain.Capabilities
import com.rally26.common.error.ForbiddenException
import com.rally26.common.error.NotFoundException
import com.rally26.common.error.ValidationException
import com.rally26.common.web.CurrentUser
import com.rally26.fundraising.domain.Campaign
import com.rally26.fundraising.domain.CampaignStatus
import com.rally26.fundraising.domain.CampaignType
import com.rally26.fundraising.domain.FundraisingSettings
import com.rally26.fundraising.persistence.CampaignRepository
import com.rally26.membership.application.MembershipService
import com.rally26.membership.domain.MembershipRole
import com.rally26.membership.domain.MembershipStatus
import com.rally26.membership.domain.OrganizationMembership
import com.rally26.sponsorship.infra.QrCodeGenerator
import com.rally26.subscription.application.PlanEntitlementService
import com.rally26.team.persistence.TeamRepository
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CampaignServiceTest {
    private val campaignRepository =
        mockk<CampaignRepository> {
            every { countActive(any()) } returns 0
        }
    private val teamRepository = mockk<TeamRepository>()
    private val membershipService = mockk<MembershipService>()
    private val authorizationService = mockk<AuthorizationService>()
    private val settingsService = mockk<FundraisingSettingsService>()
    private val planEntitlementService =
        mockk<PlanEntitlementService> {
            every { requireCampaignCapacity(any(), any()) } just runs
        }
    private val auditService = mockk<AuditService>(relaxed = true)
    private val qrCodeGenerator = mockk<QrCodeGenerator>()
    private val service =
        CampaignService(
            campaignRepository,
            teamRepository,
            membershipService,
            authorizationService,
            settingsService,
            planEntitlementService,
            auditService,
            qrCodeGenerator,
        )

    private val orgId = UUID.randomUUID()
    private val teamId = UUID.randomUUID()
    private val owner = CurrentUser(UUID.randomUUID(), "owner@example.com", "Owner")
    private val coach = CurrentUser(UUID.randomUUID(), "coach@example.com", "Coach")
    private val parent = CurrentUser(UUID.randomUUID(), "parent@example.com", "Parent")
    private val viewer = CurrentUser(UUID.randomUUID(), "viewer@example.com", "Viewer")

    @Test
    fun `list requires active membership`() {
        every { membershipService.requireActiveMembership(orgId, owner) } returns membership(owner, MembershipRole.OWNER)
        every { campaignRepository.findAll(orgId, 0, 20) } returns emptyList()

        service.list(orgId, owner, 0, 20)

        verify(exactly = 1) { membershipService.requireActiveMembership(orgId, owner) }
    }

    @Test
    fun `getPublic hides a pending fundraiser`() {
        val campaign = sampleCampaign(status = CampaignStatus.PENDING_APPROVAL)
        every { campaignRepository.findBySlug(campaign.slug) } returns campaign

        assertFailsWith<NotFoundException> { service.getPublic(campaign.slug) }
    }

    @Test
    fun `owner can create a fundraiser`() {
        every { membershipService.requireActiveMembership(orgId, owner) } returns membership(owner, MembershipRole.OWNER)
        every { teamRepository.findById(teamId, orgId) } returns mockk()
        val campaign = sampleCampaign(teamId = teamId, createdByUserId = owner.userId)
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
                owner.userId,
                null,
            )
        } returns campaign

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
                owner,
            )

        assertEquals(campaign.id, result.id)
        verify {
            auditService.record(
                actorUserId = owner.userId,
                organizationId = orgId,
                action = "campaign.created",
                entityType = "campaign",
                entityId = campaign.id,
                metadataJson = any(),
                teamId = teamId,
                summary = "Fundraiser created",
            )
        }
    }

    @Test
    fun `assigned coach can create a fundraiser for an assigned team`() {
        every { membershipService.requireActiveMembership(orgId, coach) } returns membership(coach, MembershipRole.VIEWER)
        every { authorizationService.hasGuardianRelationshipInOrganization(orgId, coach) } returns false
        every { authorizationService.listAccessibleTeamIds(orgId, coach, Capabilities.TEAM_FUNDRAISING_CREATE) } returns setOf(teamId)
        every { teamRepository.findById(teamId, orgId) } returns mockk()
        val campaign = sampleCampaign(teamId = teamId, createdByUserId = coach.userId)
        every {
            campaignRepository.insert(
                orgId,
                teamId,
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                coach.userId,
                any(),
            )
        } returns
            campaign

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
                coach,
            )

        assertEquals(coach.userId, result.createdByUserId)
    }

    @Test
    fun `coach cannot create a fundraiser for an unassigned team`() {
        every { membershipService.requireActiveMembership(orgId, coach) } returns membership(coach, MembershipRole.VIEWER)
        every { authorizationService.hasGuardianRelationshipInOrganization(orgId, coach) } returns false
        every { authorizationService.listAccessibleTeamIds(orgId, coach, Capabilities.TEAM_FUNDRAISING_CREATE) } returns
            setOf(UUID.randomUUID())

        assertFailsWith<ForbiddenException> {
            service.create(
                orgId,
                teamId,
                "Team Trip",
                "team-trip",
                null,
                CampaignType.TRAVEL,
                100_000L,
                "USD",
                null,
                null,
                coach,
            )
        }
    }

    @Test
    fun `guardian can create an organization fundraiser`() {
        every { membershipService.requireActiveMembership(orgId, parent) } returns membership(parent, MembershipRole.VIEWER)
        every { authorizationService.hasGuardianRelationshipInOrganization(orgId, parent) } returns true
        every { authorizationService.listAccessibleTeamIds(orgId, parent, Capabilities.TEAM_FUNDRAISING_CREATE) } returns emptySet()
        val campaign = sampleCampaign(createdByUserId = parent.userId)
        every {
            campaignRepository.insert(
                orgId,
                null,
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                parent.userId,
                any(),
            )
        } returns
            campaign

        val result =
            service.create(
                orgId,
                null,
                campaign.name,
                campaign.slug,
                campaign.description,
                campaign.campaignType,
                campaign.goalAmountMinor,
                campaign.currency,
                campaign.startDate,
                campaign.endDate,
                parent,
            )

        assertEquals(parent.userId, result.createdByUserId)
    }

    @Test
    fun `guardian can create a team fundraiser for a linked athlete team`() {
        every { membershipService.requireActiveMembership(orgId, parent) } returns membership(parent, MembershipRole.VIEWER)
        every { authorizationService.hasGuardianRelationshipInOrganization(orgId, parent) } returns true
        every { authorizationService.hasGuardianRelationshipForTeam(orgId, teamId, parent) } returns true
        every { authorizationService.listAccessibleTeamIds(orgId, parent, Capabilities.TEAM_FUNDRAISING_CREATE) } returns emptySet()
        every { teamRepository.findById(teamId, orgId) } returns mockk()
        val campaign = sampleCampaign(teamId = teamId, createdByUserId = parent.userId)
        every {
            campaignRepository.insert(
                orgId,
                teamId,
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                parent.userId,
                any(),
            )
        } returns
            campaign

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
                parent,
            )

        assertEquals(teamId, result.teamId)
    }

    @Test
    fun `guardian cannot create a team fundraiser for an unrelated team`() {
        every { membershipService.requireActiveMembership(orgId, parent) } returns membership(parent, MembershipRole.VIEWER)
        every { authorizationService.hasGuardianRelationshipInOrganization(orgId, parent) } returns true
        every { authorizationService.hasGuardianRelationshipForTeam(orgId, teamId, parent) } returns false
        every { authorizationService.listAccessibleTeamIds(orgId, parent, Capabilities.TEAM_FUNDRAISING_CREATE) } returns emptySet()

        assertFailsWith<ForbiddenException> {
            service.create(
                orgId,
                teamId,
                "Team Trip",
                "team-trip",
                null,
                CampaignType.TRAVEL,
                100_000L,
                "USD",
                null,
                null,
                parent,
            )
        }
    }

    @Test
    fun `ordinary viewer cannot create a fundraiser`() {
        every { membershipService.requireActiveMembership(orgId, viewer) } returns membership(viewer, MembershipRole.VIEWER)
        every { authorizationService.hasGuardianRelationshipInOrganization(orgId, viewer) } returns false
        every { authorizationService.listAccessibleTeamIds(orgId, viewer, Capabilities.TEAM_FUNDRAISING_CREATE) } returns emptySet()

        assertFailsWith<ForbiddenException> {
            service.create(
                orgId,
                null,
                "Fundraiser",
                "fundraiser",
                null,
                CampaignType.ORGANIZATION_GENERAL,
                10_000L,
                "USD",
                null,
                null,
                viewer,
            )
        }
    }

    @Test
    fun `non-owner submission becomes pending when owner approval is required`() {
        val draft = sampleCampaign(createdByUserId = parent.userId)
        val pending = draft.copy(status = CampaignStatus.PENDING_APPROVAL, submittedAt = Instant.now())
        every { membershipService.requireActiveMembership(orgId, parent) } returns membership(parent, MembershipRole.VIEWER)
        every { campaignRepository.findById(draft.id, orgId) } returnsMany listOf(draft, pending)
        every { authorizationService.hasGuardianRelationshipInOrganization(orgId, parent) } returns true
        every { authorizationService.listAccessibleTeamIds(orgId, parent, Capabilities.TEAM_FUNDRAISING_CREATE) } returns emptySet()
        every { settingsService.getInternal(orgId) } returns FundraisingSettings.defaultFor(orgId)
        every { campaignRepository.markPendingApproval(draft.id, orgId) } returns 1

        val result = service.requestActivation(orgId, draft.id, parent)

        assertEquals(CampaignStatus.PENDING_APPROVAL, result.status)
        verify(exactly = 1) { campaignRepository.markPendingApproval(draft.id, orgId) }
        verify(exactly = 0) { campaignRepository.markActive(any(), any(), any<UUID>()) }
    }

    @Test
    fun `non-owner activates immediately when owner approval is disabled`() {
        val draft = sampleCampaign(createdByUserId = coach.userId, teamId = teamId)
        val active = draft.copy(status = CampaignStatus.ACTIVE, publishedAt = Instant.now())
        every { membershipService.requireActiveMembership(orgId, coach) } returns membership(coach, MembershipRole.VIEWER)
        every { campaignRepository.findById(draft.id, orgId) } returnsMany listOf(draft, active)
        every { authorizationService.hasGuardianRelationshipInOrganization(orgId, coach) } returns false
        every { authorizationService.listAccessibleTeamIds(orgId, coach, Capabilities.TEAM_FUNDRAISING_CREATE) } returns setOf(teamId)
        every { settingsService.getInternal(orgId) } returns FundraisingSettings(orgId, false, owner.userId, Instant.now())
        every { campaignRepository.markActive(draft.id, orgId, null) } returns 1

        val result = service.requestActivation(orgId, draft.id, coach)

        assertEquals(CampaignStatus.ACTIVE, result.status)
        verify(exactly = 1) { campaignRepository.markActive(draft.id, orgId, null) }
    }

    @Test
    fun `owner can approve a pending fundraiser`() {
        val pending = sampleCampaign(status = CampaignStatus.PENDING_APPROVAL, createdByUserId = parent.userId)
        val active =
            pending.copy(
                status = CampaignStatus.ACTIVE,
                publishedAt = Instant.now(),
                approvedAt = Instant.now(),
                approvedByUserId = owner.userId,
            )
        every { membershipService.requireOwnerRole(orgId, owner) } returns membership(owner, MembershipRole.OWNER)
        every { campaignRepository.findById(pending.id, orgId) } returnsMany listOf(pending, active)
        every { campaignRepository.markActive(pending.id, orgId, owner.userId) } returns 1

        val result = service.approve(orgId, pending.id, owner)

        assertEquals(CampaignStatus.ACTIVE, result.status)
        assertEquals(owner.userId, result.approvedByUserId)
    }

    @Test
    fun `owner can close an active fundraiser`() {
        val active =
            sampleCampaign(
                status = CampaignStatus.ACTIVE,
                createdByUserId = parent.userId,
            )
        val closed = active.copy(status = CampaignStatus.CLOSED)

        every {
            membershipService.requireOwnerRole(orgId, owner)
        } returns membership(owner, MembershipRole.OWNER)

        every {
            campaignRepository.findById(active.id, orgId)
        } returnsMany listOf(active, closed)

        every {
            campaignRepository.updateStatus(
                active.id,
                orgId,
                CampaignStatus.CLOSED,
                null,
            )
        } returns 1

        val result =
            service.updateStatus(
                orgId,
                active.id,
                CampaignStatus.COMPLETED, // legacy input remains supported
                owner,
            )

        assertEquals(CampaignStatus.CLOSED, result.status)
    }

    @Test
    fun `active fundraiser must be closed before archive`() {
        val active = sampleCampaign(status = CampaignStatus.ACTIVE)
        every { membershipService.requireOwnerRole(orgId, owner) } returns membership(owner, MembershipRole.OWNER)
        every { campaignRepository.findById(active.id, orgId) } returns active

        assertFailsWith<ValidationException> {
            service.updateStatus(orgId, active.id, CampaignStatus.ARCHIVED, owner)
        }
    }

    @Test
    fun `creator cannot edit someone elses fundraiser`() {
        val draft = sampleCampaign(createdByUserId = UUID.randomUUID())
        every { membershipService.requireActiveMembership(orgId, parent) } returns membership(parent, MembershipRole.VIEWER)
        every { campaignRepository.findById(draft.id, orgId) } returns draft

        assertFailsWith<ForbiddenException> {
            service.update(orgId, draft.id, "Changed", null, null, null, null, parent)
        }
    }

    @Test
    fun `invalid date range is rejected`() {
        every { membershipService.requireActiveMembership(orgId, owner) } returns membership(owner, MembershipRole.OWNER)

        assertFailsWith<ValidationException> {
            service.create(
                orgId,
                null,
                "Spring Trip",
                "spring-trip",
                null,
                CampaignType.TRAVEL,
                100_000L,
                "USD",
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 1, 1),
                owner,
            )
        }
    }

    private fun sampleCampaign(
        teamId: UUID? = null,
        status: CampaignStatus = CampaignStatus.DRAFT,
        createdByUserId: UUID? = owner.userId,
    ) = Campaign(
        id = UUID.randomUUID(),
        organizationId = orgId,
        teamId = teamId,
        name = "Spring Trip",
        slug = "spring-trip-${UUID.randomUUID()}",
        description = "Help send the team to regionals.",
        campaignType = CampaignType.TRAVEL,
        goalAmountMinor = 400_000L,
        currency = "USD",
        startDate = LocalDate.of(2026, 1, 1),
        endDate = LocalDate.of(2026, 6, 1),
        status = status,
        publishedAt = if (status == CampaignStatus.ACTIVE || status == CampaignStatus.COMPLETED) Instant.now() else null,
        createdByUserId = createdByUserId,
        templateKey = null,
        submittedAt = if (status == CampaignStatus.PENDING_APPROVAL) Instant.now() else null,
        approvedAt = null,
        approvedByUserId = null,
        createdAt = Instant.now(),
        updatedAt = Instant.now(),
    )

    private fun membership(
        user: CurrentUser,
        role: MembershipRole,
    ) = OrganizationMembership(
        id = UUID.randomUUID(),
        organizationId = orgId,
        userId = user.userId,
        role = role,
        status = MembershipStatus.ACTIVE,
        createdAt = Instant.now(),
        updatedAt = Instant.now(),
    )
}
