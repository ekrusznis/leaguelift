package com.leaguelift.media.application

import com.leaguelift.authorization.application.AuthorizationService
import com.leaguelift.authorization.domain.Capabilities
import com.leaguelift.common.error.ForbiddenException
import com.leaguelift.common.error.ValidationException
import com.leaguelift.common.web.CurrentUser
import com.leaguelift.household.domain.AdultStatus
import com.leaguelift.household.domain.HouseholdAdult
import com.leaguelift.household.persistence.HouseholdRepository
import com.leaguelift.media.domain.MediaEntityType
import com.leaguelift.media.domain.MediaUsageSlot
import com.leaguelift.media.domain.Visibility
import com.leaguelift.membership.application.MembershipService
import com.leaguelift.participant.domain.Participant
import com.leaguelift.participant.domain.ParticipantStatus
import com.leaguelift.participant.persistence.ParticipantRepository
import com.leaguelift.publicpage.domain.PageStatus
import com.leaguelift.publicpage.domain.PageType
import com.leaguelift.publicpage.domain.PublicPage
import com.leaguelift.publicpage.persistence.PublicPageRepository
import com.leaguelift.team.domain.Team
import com.leaguelift.team.domain.TeamStatus
import com.leaguelift.team.persistence.TeamRepository
import com.leaguelift.tournament.persistence.TournamentRepository
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
	private val service = MediaEntityAccessService(
		membershipService,
		authorizationService,
		teamRepository,
		tournamentRepository,
		householdRepository,
		participantRepository,
		publicPageRepository,
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
		assertEquals(setOf(MediaUsageSlot.PROFILE_PHOTO), target.allowedSlots)
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

	private fun team() = Team(
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

	private fun adult() = HouseholdAdult(
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

	private fun participant() = Participant(
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

	private fun publishedTeamPage(teamId: UUID) = PublicPage(
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
