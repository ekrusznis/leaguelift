package com.rally26.team.application

import com.rally26.audit.application.AuditService
import com.rally26.common.error.ConflictException
import com.rally26.common.error.NotFoundException
import com.rally26.common.error.ValidationException
import com.rally26.common.web.CurrentUser
import com.rally26.membership.application.MembershipService
import com.rally26.team.domain.Team
import com.rally26.team.persistence.TeamRepository
import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

private val EMAIL_PATTERN = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")

@Service
class TeamService(
    private val teamRepository: TeamRepository,
    private val membershipService: MembershipService,
    private val auditService: AuditService,
) {
    fun list(
        organizationId: UUID,
        currentUser: CurrentUser,
        offset: Int,
        limit: Int,
    ): List<Team> {
        membershipService.requireActiveMembership(organizationId, currentUser)
        return teamRepository.findAll(organizationId, offset, limit)
    }

    fun count(
        organizationId: UUID,
        currentUser: CurrentUser,
    ): Long {
        membershipService.requireActiveMembership(organizationId, currentUser)
        return teamRepository.countAll(organizationId)
    }

    fun get(
        organizationId: UUID,
        teamId: UUID,
        currentUser: CurrentUser,
    ): Team {
        membershipService.requireActiveMembership(organizationId, currentUser)
        return teamRepository.findById(teamId, organizationId)
            ?: throw NotFoundException("TEAM_NOT_FOUND", "The team could not be found.")
    }

    @Transactional
    fun create(
        organizationId: UUID,
        name: String,
        sport: String,
        season: String?,
        contactEmail: String?,
        currentUser: CurrentUser,
    ): Team {
        membershipService.requireManagerRole(organizationId, currentUser)
        validateContactEmail(contactEmail)
        return try {
            val team = teamRepository.insert(organizationId, name, sport, season, contactEmail)
            auditService.record(
                actorUserId = currentUser.userId,
                organizationId = organizationId,
                action = "team.created",
                entityType = "team",
                entityId = team.id,
            )
            team
        } catch (e: DuplicateKeyException) {
            throw ConflictException("TEAM_NAME_TAKEN", "A team with this name already exists in the organization.")
        }
    }

    @Transactional
    fun update(
        organizationId: UUID,
        teamId: UUID,
        name: String?,
        sport: String?,
        season: String?,
        contactEmail: String?,
        currentUser: CurrentUser,
    ): Team {
        membershipService.requireManagerRole(organizationId, currentUser)
        teamRepository.findById(teamId, organizationId)
            ?: throw NotFoundException("TEAM_NOT_FOUND", "The team could not be found.")
        validateContactEmail(contactEmail)
        return try {
            teamRepository.update(teamId, organizationId, name, sport, season, contactEmail)
            auditService.record(
                actorUserId = currentUser.userId,
                organizationId = organizationId,
                action = "team.updated",
                entityType = "team",
                entityId = teamId,
            )
            teamRepository.findById(teamId, organizationId)!!
        } catch (e: DuplicateKeyException) {
            throw ConflictException("TEAM_NAME_TAKEN", "A team with this name already exists in the organization.")
        }
    }

    @Transactional
    fun archive(
        organizationId: UUID,
        teamId: UUID,
        currentUser: CurrentUser,
    ) {
        membershipService.requireManagerRole(organizationId, currentUser)
        val rows = teamRepository.archive(teamId, organizationId)
        if (rows == 0) throw NotFoundException("TEAM_NOT_FOUND", "The team could not be found.")
        auditService.record(
            actorUserId = currentUser.userId,
            organizationId = organizationId,
            action = "team.archived",
            entityType = "team",
            entityId = teamId,
        )
    }

    private fun validateContactEmail(email: String?) {
        if (email != null && !EMAIL_PATTERN.matches(email)) {
            throw ValidationException(
                "Contact email is not a valid email address.",
                listOf(
                    com.rally26.common.error
                        .FieldError("contactEmail", "Invalid email format."),
                ),
            )
        }
    }
}
