package com.rally26.event.application

import com.rally26.authorization.application.AuthorizationService
import com.rally26.authorization.domain.Capabilities
import com.rally26.common.error.ForbiddenException
import com.rally26.common.error.NotFoundException
import com.rally26.common.web.CurrentUser
import com.rally26.event.domain.Event
import com.rally26.event.domain.EventListCriteria
import com.rally26.event.persistence.EventSearchRepository
import com.rally26.household.persistence.HouseholdRepository
import com.rally26.membership.application.MembershipService
import com.rally26.participant.persistence.ParticipantRepository
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class EventSearchService(
    private val repository: EventSearchRepository,
    private val membershipService: MembershipService,
    private val authorizationService: AuthorizationService,
    private val householdRepository: HouseholdRepository,
    private val participantRepository: ParticipantRepository,
) {
    fun organization(
        organizationId: UUID,
        criteria: EventListCriteria,
        currentUser: CurrentUser,
        offset: Int,
        limit: Int,
    ): Pair<List<Event>, Long> {
        membershipService.requireActiveMembership(organizationId, currentUser)
        return repository.searchOrganization(organizationId, criteria, offset, limit) to
            repository.countOrganization(organizationId, criteria)
    }

    fun team(
        organizationId: UUID,
        teamId: UUID,
        criteria: EventListCriteria,
        currentUser: CurrentUser,
        offset: Int,
        limit: Int,
    ): Pair<List<Event>, Long> {
        authorizationService.requireTeamCapability(organizationId, teamId, currentUser, Capabilities.EVENT_READ)
        return repository.searchTeam(organizationId, teamId, criteria, offset, limit) to
            repository.countTeam(organizationId, teamId, criteria)
    }

    fun tournament(
        organizationId: UUID,
        tournamentId: UUID,
        criteria: EventListCriteria,
        currentUser: CurrentUser,
        offset: Int,
        limit: Int,
    ): Pair<List<Event>, Long> {
        authorizationService.requireTournamentCapability(organizationId, tournamentId, currentUser, Capabilities.EVENT_READ)
        return repository.searchTournament(organizationId, tournamentId, criteria, offset, limit) to
            repository.countTournament(organizationId, tournamentId, criteria)
    }

    fun household(
        organizationId: UUID,
        householdId: UUID,
        criteria: EventListCriteria,
        currentUser: CurrentUser,
        offset: Int,
        limit: Int,
    ): Pair<List<Event>, Long> {
        householdRepository.findById(householdId, organizationId)
            ?: throw NotFoundException("HOUSEHOLD_NOT_FOUND", "The household could not be found.")
        if (!authorizationService.hasHouseholdCapability(organizationId, householdId, currentUser, Capabilities.EVENT_READ)) {
            throw ForbiddenException("CAPABILITY_DENIED", "You do not have access to this household's schedule.")
        }
        val teamIds =
            participantRepository
                .findByHousehold(householdId, organizationId)
                .flatMap { participantRepository.listTeamAssignments(it.id, organizationId) }
                .map { it.teamId }
                .toSet()
        return repository.searchTeams(organizationId, teamIds, criteria, offset, limit) to
            repository.countTeams(organizationId, teamIds, criteria)
    }

    fun participant(
        organizationId: UUID,
        participantId: UUID,
        criteria: EventListCriteria,
        currentUser: CurrentUser,
        offset: Int,
        limit: Int,
    ): Pair<List<Event>, Long> {
        val participant =
            participantRepository.findById(participantId, organizationId)
                ?: throw NotFoundException("PARTICIPANT_NOT_FOUND", "The participant could not be found.")
        val isSelf = authorizationService.hasParticipantCapability(currentUser, participantId, Capabilities.ATHLETE_SCHEDULE_VIEW)
        val isAuthorized =
            isSelf ||
                authorizationService.hasHouseholdCapability(
                    organizationId,
                    participant.householdId,
                    currentUser,
                    Capabilities.EVENT_READ,
                )
        if (!isAuthorized) {
            throw ForbiddenException("CAPABILITY_DENIED", "You do not have access to this participant's schedule.")
        }
        val teamIds = participantRepository.listTeamAssignments(participantId, organizationId).map { it.teamId }.toSet()
        return repository.searchTeams(organizationId, teamIds, criteria, offset, limit) to
            repository.countTeams(organizationId, teamIds, criteria)
    }
}
