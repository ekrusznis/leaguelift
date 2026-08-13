package com.rally26.media.application

import com.rally26.authorization.application.AuthorizationService
import com.rally26.authorization.domain.Capabilities
import com.rally26.common.error.ForbiddenException
import com.rally26.common.error.ValidationException
import com.rally26.common.web.CurrentUser
import com.rally26.household.domain.AdultStatus
import com.rally26.household.domain.Household
import com.rally26.household.domain.HouseholdAdult
import com.rally26.household.domain.HouseholdStatus
import com.rally26.household.persistence.HouseholdRepository
import com.rally26.media.domain.MediaEntityType
import com.rally26.media.domain.MediaUsageSlot
import com.rally26.media.domain.Visibility
import com.rally26.membership.application.MembershipService
import com.rally26.participant.domain.Participant
import com.rally26.participant.domain.ParticipantStatus
import com.rally26.participant.persistence.ParticipantRepository
import com.rally26.publicpage.domain.PageStatus
import com.rally26.publicpage.domain.PageType
import com.rally26.publicpage.domain.PublicPage
import com.rally26.publicpage.persistence.PublicPageRepository
import com.rally26.support.domain.SupportArticle
import com.rally26.support.domain.SupportArticleStatus
import com.rally26.support.domain.SupportAudience
import com.rally26.support.persistence.SupportArticleRepository
import com.rally26.team.domain.Team
import com.rally26.team.domain.TeamStatus
import com.rally26.team.persistence.TeamRepository
import com.rally26.tournament.persistence.TournamentRepository
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

class MediaEntityAccessServiceTest {
    private val membershipService = mockk<MembershipService>()
    private val authorizationService = mockk<AuthorizationService>()
    private val teamRepository = mockk<TeamRepository>()
    private val tournamentRepository = mockk<TournamentRepository>()
    private val householdRepository = mockk<HouseholdRepository>()
    private val participantRepository = mockk<ParticipantRepository>()
    private val publicPageRepository = mockk<PublicPageRepository>()
    private val supportArticleRepository = mockk<SupportArticleRepository>()
    private val service =
        MediaEntityAccessService(
            membershipService,
            authorizationService,
            teamRepository,
            tournamentRepository,
            householdRepository,
            participantRepository,
            publicPageRepository,
            supportArticleRepository,
        )

    private val organizationId = UUID.randomUUID()
    private val currentUser = CurrentUser(UUID.randomUUID(), "guardian@example.com", "Guardian")

    @Test
    fun `team branding requires the team page edit capability and becomes public for a published page`() {
        val team = team()
        every { teamRepository.findById(team.id, organizationId) } returns team
        every {
            authorizationService.requireTeamCapability(
                organizationId,
                team.id,
                currentUser,
                Capabilities.TEAM_PAGE_EDIT,
            )
        } just runs
        every { publicPageRepository.findByEntityId(team.id) } returns publishedTeamPage(team.id)

        val target = service.resolveForManage(organizationId, MediaEntityType.TEAM, team.id, currentUser)

        assertEquals(setOf(MediaUsageSlot.LOGO, MediaUsageSlot.COVER), target.allowedSlots)
        assertEquals(Visibility.PUBLIC, target.visibility)
        verify(exactly = 1) {
            authorizationService.requireTeamCapability(
                organizationId,
                team.id,
                currentUser,
                Capabilities.TEAM_PAGE_EDIT,
            )
        }
    }

    @Test
    fun `a guardian may view household adult photos but may manage only their own adult profile`() {
        val adult = adult()
        every { householdRepository.findAdultById(adult.id, organizationId) } returns adult
        every { membershipService.hasManagerRole(organizationId, currentUser) } returns false
        every { authorizationService.hasGuardianRelationship(organizationId, adult.householdId, currentUser) } returns true
        every { authorizationService.hasGuardianAdultRelationship(organizationId, adult.id, currentUser) } returns false

        val readTarget = service.resolveForRead(organizationId, MediaEntityType.HOUSEHOLD_ADULT, adult.id, currentUser)

        assertEquals(Visibility.HOUSEHOLD_PRIVATE, readTarget.visibility)
        assertFailsWith<ForbiddenException> {
            service.resolveForManage(organizationId, MediaEntityType.HOUSEHOLD_ADULT, adult.id, currentUser)
        }

        every { authorizationService.hasGuardianAdultRelationship(organizationId, adult.id, currentUser) } returns true
        val managedTarget = service.resolveForManage(organizationId, MediaEntityType.HOUSEHOLD_ADULT, adult.id, currentUser)
        assertEquals(setOf(MediaUsageSlot.PROFILE_PHOTO), managedTarget.allowedSlots)
    }

    @Test
    fun `a linked guardian may manage a participant photo without receiving organization manager access`() {
        val participant = participant()
        every { participantRepository.findById(participant.id, organizationId) } returns participant
        every { membershipService.hasManagerRole(organizationId, currentUser) } returns false
        every {
            authorizationService.hasGuardianRelationship(organizationId, participant.householdId, currentUser)
        } returns true
        every {
            authorizationService.hasParticipantCapability(currentUser, participant.id, Capabilities.ATHLETE_PROFILE_UPDATE)
        } returns false

        val target = service.resolveForManage(organizationId, MediaEntityType.PARTICIPANT, participant.id, currentUser)

        assertEquals(Visibility.HOUSEHOLD_PRIVATE, target.visibility)
        assertEquals(setOf(MediaUsageSlot.PROFILE_PHOTO, MediaUsageSlot.DOCUMENT), target.allowedSlots)
    }

    @Test
    fun `a guardian may manage household media without organization manager access`() {
        val household = household()
        every { householdRepository.findById(household.id, organizationId) } returns household
        every { membershipService.hasManagerRole(organizationId, currentUser) } returns false
        every { authorizationService.hasGuardianRelationship(organizationId, household.id, currentUser) } returns true

        val target = service.resolveForManage(organizationId, MediaEntityType.HOUSEHOLD, household.id, currentUser)

        assertEquals(setOf(MediaUsageSlot.HOUSEHOLD_MEDIA), target.allowedSlots)
        assertEquals(Visibility.HOUSEHOLD_PRIVATE, target.visibility)
    }

    @Test
    fun `a stranger with no guardian relationship or manager role cannot manage household media`() {
        val household = household()
        every { householdRepository.findById(household.id, organizationId) } returns household
        every { membershipService.hasManagerRole(organizationId, currentUser) } returns false
        every { authorizationService.hasGuardianRelationship(organizationId, household.id, currentUser) } returns false

        assertFailsWith<ForbiddenException> {
            service.resolveForManage(organizationId, MediaEntityType.HOUSEHOLD, household.id, currentUser)
        }
    }

    @Test
    fun `a platform admin may manage a support article's attachments`() {
        val article = supportArticle()
        every { supportArticleRepository.findById(article.id) } returns article
        every { authorizationService.requirePlatformCapability(currentUser, Capabilities.PLATFORM_HELP_MANAGE) } just runs

        val target = service.resolveForManage(organizationId, MediaEntityType.SUPPORT_ARTICLE, article.id, currentUser)

        assertEquals(setOf(MediaUsageSlot.ARTICLE_ATTACHMENT), target.allowedSlots)
        assertEquals(Visibility.PUBLIC, target.visibility)
    }

    @Test
    fun `a non platform admin cannot manage a support article's attachments`() {
        val article = supportArticle()
        every { supportArticleRepository.findById(article.id) } returns article
        every {
            authorizationService.requirePlatformCapability(currentUser, Capabilities.PLATFORM_HELP_MANAGE)
        } throws ForbiddenException("PLATFORM_CAPABILITY_DENIED", "You do not have this platform capability.")

        assertFailsWith<ForbiddenException> {
            service.resolveForManage(organizationId, MediaEntityType.SUPPORT_ARTICLE, article.id, currentUser)
        }
    }

    @Test
    fun `profile targets reject branding slots`() {
        val participant = participant()
        every { participantRepository.findById(participant.id, organizationId) } returns participant
        every { membershipService.hasManagerRole(organizationId, currentUser) } returns true
        every {
            authorizationService.hasParticipantCapability(currentUser, participant.id, Capabilities.ATHLETE_PROFILE_UPDATE)
        } returns false
        val target = service.resolveForManage(organizationId, MediaEntityType.PARTICIPANT, participant.id, currentUser)

        assertFailsWith<ValidationException> {
            service.requireAllowedSlot(target, MediaUsageSlot.LOGO)
        }
    }

    private fun team() =
        Team(
            id = UUID.randomUUID(),
            organizationId = organizationId,
            name = "U14 Blue",
            sport = "Volleyball",
            season = "2026-27",
            status = TeamStatus.ACTIVE,
            contactEmail = null,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
        )

    private fun household() =
        Household(
            id = UUID.randomUUID(),
            organizationId = organizationId,
            displayName = "Smith Family",
            contactEmail = null,
            contactPhone = null,
            notes = null,
            emailRemindersOptOut = false,
            smsRemindersOptIn = false,
            status = HouseholdStatus.ACTIVE,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
        )

    private fun adult() =
        HouseholdAdult(
            id = UUID.randomUUID(),
            householdId = UUID.randomUUID(),
            organizationId = organizationId,
            firstName = "Taylor",
            lastName = "Morgan",
            email = "guardian@example.com",
            phone = null,
            relationship = "Parent",
            isPrimary = true,
            status = AdultStatus.ACTIVE,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
        )

    private fun participant() =
        Participant(
            id = UUID.randomUUID(),
            householdId = UUID.randomUUID(),
            organizationId = organizationId,
            firstName = "Avery",
            lastName = "Morgan",
            dateOfBirth = null,
            notes = null,
            status = ParticipantStatus.ACTIVE,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
        )

    private fun supportArticle() =
        SupportArticle(
            id = UUID.randomUUID(),
            slug = "getting-started",
            title = "Getting started",
            summary = "A practical introduction to the Rally26 workspace.",
            bodyMarkdown = "## Start here\n\nUse the organization workspace to begin setup.",
            category = "Getting Started",
            audience = SupportAudience.PUBLIC,
            status = SupportArticleStatus.DRAFT,
            sortOrder = 10,
            publishedAt = null,
            createdBy = null,
            updatedBy = null,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
        )

    private fun publishedTeamPage(teamId: UUID) =
        PublicPage(
            id = UUID.randomUUID(),
            organizationId = organizationId,
            pageType = PageType.TEAM,
            entityId = teamId,
            slug = "u14-blue",
            title = "U14 Blue",
            summary = null,
            status = PageStatus.PUBLISHED,
            publishedAt = Instant.now(),
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
        )
}
