package com.rally26.participant.application

import com.rally26.audit.application.AuditService
import com.rally26.authorization.application.AuthorizationService
import com.rally26.authorization.domain.Capabilities
import com.rally26.common.error.ConflictException
import com.rally26.common.error.ForbiddenException
import com.rally26.common.error.NotFoundException
import com.rally26.common.web.CurrentUser
import com.rally26.household.persistence.HouseholdRepository
import com.rally26.membership.application.MembershipService
import com.rally26.participant.domain.Participant
import com.rally26.participant.domain.ParticipantTeamAssignment
import com.rally26.participant.persistence.ParticipantRepository
import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.util.UUID

@Service
class ParticipantService(
    private val participantRepository: ParticipantRepository,
    private val householdRepository: HouseholdRepository,
    private val membershipService: MembershipService,
    private val auditService: AuditService,
    private val authorizationService: AuthorizationService,
) {
    fun listForOrganization(
        organizationId: UUID,
        currentUser: CurrentUser,
        offset: Int,
        limit: Int,
    ): List<Participant> {
        membershipService.requireManagerRole(organizationId, currentUser)
        return participantRepository.findAllForOrganization(organizationId, offset, limit)
    }

    fun countForOrganization(
        organizationId: UUID,
        currentUser: CurrentUser,
    ): Long {
        membershipService.requireManagerRole(organizationId, currentUser)
        return participantRepository.countAllForOrganization(organizationId)
    }

    /** Read-only, so open to a guardian viewing their own household's participants, not just org staff. */
    fun listForHousehold(
        organizationId: UUID,
        householdId: UUID,
        currentUser: CurrentUser,
    ): List<Participant> {
        householdRepository.findById(householdId, organizationId)
            ?: throw NotFoundException("HOUSEHOLD_NOT_FOUND", "The household could not be found.")
        if (!authorizationService.hasHouseholdCapability(organizationId, householdId, currentUser, Capabilities.HOUSEHOLD_VIEW)) {
            throw ForbiddenException("HOUSEHOLD_ACCESS_DENIED", "You do not have access to this household.")
        }
        return participantRepository.findByHousehold(householdId, organizationId)
    }

    @Transactional
    fun create(
        organizationId: UUID,
        householdId: UUID,
        firstName: String,
        lastName: String,
        dateOfBirth: LocalDate?,
        notes: String?,
        currentUser: CurrentUser,
    ): Participant {
        membershipService.requireManagerRole(organizationId, currentUser)
        householdRepository.findById(householdId, organizationId)
            ?: throw NotFoundException("HOUSEHOLD_NOT_FOUND", "The household could not be found.")
        val participant =
            participantRepository.insert(
                organizationId = organizationId,
                householdId = householdId,
                firstName = firstName,
                lastName = lastName,
                dateOfBirth = dateOfBirth,
                notes = notes,
            )
        auditService.record(currentUser.userId, organizationId, "participant.created", "participant", participant.id)
        return participant
    }

    @Transactional
    fun update(
        organizationId: UUID,
        participantId: UUID,
        firstName: String?,
        lastName: String?,
        dateOfBirth: LocalDate?,
        notes: String?,
        currentUser: CurrentUser,
    ): Participant {
        membershipService.requireManagerRole(organizationId, currentUser)
        participantRepository.findById(participantId, organizationId)
            ?: throw NotFoundException("PARTICIPANT_NOT_FOUND", "The participant could not be found.")
        participantRepository.update(participantId, organizationId, firstName, lastName, dateOfBirth, notes)
        auditService.record(currentUser.userId, organizationId, "participant.updated", "participant", participantId)
        return participantRepository.findById(participantId, organizationId)!!
    }

    fun listTeams(
        organizationId: UUID,
        participantId: UUID,
        currentUser: CurrentUser,
    ): List<ParticipantTeamAssignment> {
        membershipService.requireActiveMembership(organizationId, currentUser)
        participantRepository.findById(participantId, organizationId)
            ?: throw NotFoundException("PARTICIPANT_NOT_FOUND", "The participant could not be found.")
        return participantRepository.listTeamAssignments(participantId, organizationId)
    }

    @Transactional
    fun assignToTeam(
        organizationId: UUID,
        participantId: UUID,
        teamId: UUID,
        joinedAt: LocalDate?,
        currentUser: CurrentUser,
    ): ParticipantTeamAssignment {
        membershipService.requireManagerRole(organizationId, currentUser)
        participantRepository.findById(participantId, organizationId)
            ?: throw NotFoundException("PARTICIPANT_NOT_FOUND", "The participant could not be found.")
        return try {
            val assignment = participantRepository.assignToTeam(participantId, teamId, organizationId, joinedAt)
            auditService.record(currentUser.userId, organizationId, "participant.team.assigned", "participant_team", assignment.id)
            assignment
        } catch (e: DuplicateKeyException) {
            throw ConflictException("PARTICIPANT_ALREADY_ON_TEAM", "The participant is already assigned to this team.")
        }
    }

    @Transactional
    fun removeFromTeam(
        organizationId: UUID,
        participantId: UUID,
        teamId: UUID,
        currentUser: CurrentUser,
    ) {
        membershipService.requireManagerRole(organizationId, currentUser)
        val rows = participantRepository.removeFromTeam(participantId, teamId, organizationId)
        if (rows == 0) throw NotFoundException("ASSIGNMENT_NOT_FOUND", "The participant is not assigned to this team.")
        auditService.record(currentUser.userId, organizationId, "participant.team.removed", "participant_team", participantId)
    }
}
