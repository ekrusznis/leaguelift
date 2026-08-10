package com.rally26.dashboard.application

import com.rally26.authorization.domain.GuardianRelationship
import com.rally26.authorization.domain.GuardianRelationshipStatus
import com.rally26.authorization.domain.ResourceRole
import com.rally26.authorization.domain.RoleAssignment
import com.rally26.authorization.domain.RoleAssignmentContextType
import com.rally26.authorization.domain.RoleAssignmentStatus
import com.rally26.authorization.persistence.GuardianRelationshipRepository
import com.rally26.authorization.persistence.RoleAssignmentRepository
import com.rally26.common.web.CurrentUser
import com.rally26.dashboard.domain.DashboardRole
import com.rally26.household.domain.AdultStatus
import com.rally26.household.domain.HouseholdAdult
import com.rally26.household.persistence.HouseholdRepository
import com.rally26.membership.domain.MembershipRole
import com.rally26.membership.domain.MembershipStatus
import com.rally26.membership.domain.OrganizationMembership
import com.rally26.membership.persistence.MembershipRepository
import com.rally26.onboarding.owner.domain.OwnerOnboarding
import com.rally26.onboarding.owner.domain.OwnerOnboardingStep
import com.rally26.onboarding.owner.persistence.OwnerOnboardingRepository
import io.mockk.every
import io.mockk.mockk
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DashboardContextServiceTest {
    private val membershipRepository = mockk<MembershipRepository>()
    private val householdRepository = mockk<HouseholdRepository>()
    private val roleAssignmentRepository = mockk<RoleAssignmentRepository>()
    private val guardianRelationshipRepository = mockk<GuardianRelationshipRepository>()
    private val ownerOnboardingRepository = mockk<OwnerOnboardingRepository>()
    private val service =
        DashboardContextService(
            membershipRepository,
            householdRepository,
            roleAssignmentRepository,
            guardianRelationshipRepository,
            ownerOnboardingRepository,
        )

    private val currentUser = CurrentUser(UUID.randomUUID(), "person@example.com", "Person")

    @Test
    fun `a platform administrator always resolves to the Platform Admin dashboard`() {
        val admin = CurrentUser(UUID.randomUUID(), "admin@example.com", "Admin", platformAdministrator = true)

        val result = service.resolve(admin)

        assertEquals(DashboardRole.PLATFORM_ADMIN, result.role)
    }

    @Test
    fun `org owner membership resolves to OWNER dashboard`() {
        every { membershipRepository.findAnyActiveMembershipForUser(currentUser.userId) } returns membership(MembershipRole.OWNER)

        val result = service.resolve(currentUser)

        assertEquals(DashboardRole.OWNER, result.role)
    }

    @Test
    fun `administrator and viewer roles also resolve to OWNER dashboard`() {
        every { membershipRepository.findAnyActiveMembershipForUser(currentUser.userId) } returns membership(MembershipRole.ADMINISTRATOR)
        assertEquals(DashboardRole.OWNER, service.resolve(currentUser).role)

        every { membershipRepository.findAnyActiveMembershipForUser(currentUser.userId) } returns membership(MembershipRole.VIEWER)
        assertEquals(DashboardRole.OWNER, service.resolve(currentUser).role)
    }

    @Test
    fun `team administrator membership resolves to COACH dashboard`() {
        every { membershipRepository.findAnyActiveMembershipForUser(currentUser.userId) } returns
            membership(MembershipRole.TEAM_ADMINISTRATOR)

        val result = service.resolve(currentUser)

        assertEquals(DashboardRole.COACH, result.role)
    }

    @Test
    fun `tournament administrator membership resolves to TOURNAMENT_ADMIN dashboard`() {
        val orgMembership = membership(MembershipRole.TOURNAMENT_ADMINISTRATOR)
        every { membershipRepository.findAnyActiveMembershipForUser(currentUser.userId) } returns orgMembership
        every { roleAssignmentRepository.findActiveForUserAndContext(currentUser.userId, RoleAssignmentContextType.TOURNAMENT) } returns
            emptyList()

        val result = service.resolve(currentUser)

        assertEquals(DashboardRole.TOURNAMENT_ADMIN, result.role)
    }

    @Test
    fun `a team-only coach with no organization membership still resolves to COACH dashboard`() {
        every { membershipRepository.findAnyActiveMembershipForUser(currentUser.userId) } returns null
        val organizationId = UUID.randomUUID()
        every { roleAssignmentRepository.findActiveForUserAndContext(currentUser.userId, RoleAssignmentContextType.TEAM) } returns
            listOf(
                RoleAssignment(
                    UUID.randomUUID(),
                    organizationId,
                    currentUser.userId,
                    RoleAssignmentContextType.TEAM,
                    UUID.randomUUID(),
                    ResourceRole.COACH_READ,
                    RoleAssignmentStatus.ACTIVE,
                    null,
                    Instant.now(),
                    Instant.now(),
                ),
            )

        val result = service.resolve(currentUser)

        assertEquals(DashboardRole.COACH, result.role)
        assertEquals(organizationId, result.organizationId)
    }

    @Test
    fun `a real guardian_relationship resolves to PARENT dashboard`() {
        every { membershipRepository.findAnyActiveMembershipForUser(currentUser.userId) } returns null
        every { roleAssignmentRepository.findActiveForUserAndContext(currentUser.userId, RoleAssignmentContextType.TEAM) } returns
            emptyList()
        every { roleAssignmentRepository.findActiveForUserAndContext(currentUser.userId, RoleAssignmentContextType.TOURNAMENT) } returns
            emptyList()
        val organizationId = UUID.randomUUID()
        val householdId = UUID.randomUUID()
        every { guardianRelationshipRepository.findActiveForUser(currentUser.userId) } returns
            listOf(
                GuardianRelationship(
                    UUID.randomUUID(),
                    organizationId,
                    householdId,
                    UUID.randomUUID(),
                    currentUser.userId,
                    GuardianRelationshipStatus.ACTIVE,
                    Instant.now(),
                    Instant.now(),
                ),
            )

        val result = service.resolve(currentUser)

        assertEquals(DashboardRole.PARENT, result.role)
        assertEquals(householdId, result.householdId)
    }

    @Test
    fun `household adult email match resolves to PARENT dashboard when no guardian_relationship exists`() {
        every { membershipRepository.findAnyActiveMembershipForUser(currentUser.userId) } returns null
        every { roleAssignmentRepository.findActiveForUserAndContext(currentUser.userId, RoleAssignmentContextType.TEAM) } returns
            emptyList()
        every { roleAssignmentRepository.findActiveForUserAndContext(currentUser.userId, RoleAssignmentContextType.TOURNAMENT) } returns
            emptyList()
        every { guardianRelationshipRepository.findActiveForUser(currentUser.userId) } returns emptyList()
        val adult =
            HouseholdAdult(
                id = UUID.randomUUID(),
                householdId = UUID.randomUUID(),
                organizationId = UUID.randomUUID(),
                firstName = "Person",
                lastName = "Example",
                email = currentUser.email,
                phone = null,
                relationship = "Parent",
                isPrimary = true,
                status = AdultStatus.ACTIVE,
                createdAt = Instant.now(),
                updatedAt = Instant.now(),
            )
        every { householdRepository.findActiveAdultByEmail(currentUser.email) } returns adult

        val result = service.resolve(currentUser)

        assertEquals(DashboardRole.PARENT, result.role)
        assertEquals(adult.householdId, result.householdId)
        assertEquals(adult.organizationId, result.organizationId)
    }

    @Test
    fun `falls back to ATHLETE dashboard when nothing else matches`() {
        every { membershipRepository.findAnyActiveMembershipForUser(currentUser.userId) } returns null
        every { roleAssignmentRepository.findActiveForUserAndContext(currentUser.userId, RoleAssignmentContextType.TEAM) } returns
            emptyList()
        every { roleAssignmentRepository.findActiveForUserAndContext(currentUser.userId, RoleAssignmentContextType.TOURNAMENT) } returns
            emptyList()
        every { guardianRelationshipRepository.findActiveForUser(currentUser.userId) } returns emptyList()
        every { householdRepository.findActiveAdultByEmail(currentUser.email) } returns null
        every { ownerOnboardingRepository.findByOwnerUserId(currentUser.userId) } returns null

        val result = service.resolve(currentUser)

        assertEquals(DashboardRole.ATHLETE, result.role)
        assertNull(result.organizationId)
        assertNull(result.householdId)
    }

    @Test
    fun `an owner mid-onboarding with no organization yet resolves to OWNER dashboard instead of the ATHLETE fallback`() {
        every { membershipRepository.findAnyActiveMembershipForUser(currentUser.userId) } returns null
        every { roleAssignmentRepository.findActiveForUserAndContext(currentUser.userId, RoleAssignmentContextType.TEAM) } returns
            emptyList()
        every { roleAssignmentRepository.findActiveForUserAndContext(currentUser.userId, RoleAssignmentContextType.TOURNAMENT) } returns
            emptyList()
        every { guardianRelationshipRepository.findActiveForUser(currentUser.userId) } returns emptyList()
        every { householdRepository.findActiveAdultByEmail(currentUser.email) } returns null
        every { ownerOnboardingRepository.findByOwnerUserId(currentUser.userId) } returns
            OwnerOnboarding(
                id = UUID.randomUUID(),
                ownerUserId = currentUser.userId,
                organizationId = null,
                currentStep = OwnerOnboardingStep.ORGANIZATION,
                selectedPlanCode = null,
                selectedBillingFrequency = null,
                checkoutSessionId = null,
                checkoutStartedAt = null,
                completedAt = null,
                createdAt = Instant.now(),
                updatedAt = Instant.now(),
            )

        val result = service.resolve(currentUser)

        assertEquals(DashboardRole.OWNER, result.role)
        assertNull(result.organizationId)
        assertNull(result.householdId)
    }

    private fun membership(role: MembershipRole) =
        OrganizationMembership(
            id = UUID.randomUUID(),
            organizationId = UUID.randomUUID(),
            userId = currentUser.userId,
            role = role,
            status = MembershipStatus.ACTIVE,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
        )
}
