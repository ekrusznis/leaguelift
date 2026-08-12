package com.rally26.team.application

import com.rally26.audit.application.AuditService
import com.rally26.common.error.ConflictException
import com.rally26.common.error.FieldError
import com.rally26.common.error.NotFoundException
import com.rally26.common.error.ValidationException
import com.rally26.common.web.CurrentUser
import com.rally26.membership.application.MembershipService
import com.rally26.subscription.application.PlanEntitlementService
import com.rally26.team.domain.Team
import com.rally26.team.domain.TeamGenderCategory
import com.rally26.team.persistence.TeamRepository
import com.rally26.timezone.application.TimeZoneService
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
    private val timeZoneService: TimeZoneService,
    private val planEntitlementService: PlanEntitlementService,
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
        ageGroup: String?,
        genderCategory: TeamGenderCategory?,
        level: String?,
        currentUser: CurrentUser,
    ): Team {
        membershipService.requireManagerRole(organizationId, currentUser)
        planEntitlementService.requireTeamCapacity(organizationId, teamRepository.countAll(organizationId))
        validateContactEmail(contactEmail)
        return try {
            val team = teamRepository.insert(organizationId, name, sport, season, contactEmail, ageGroup, genderCategory, level)
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
        ageGroup: String?,
        genderCategory: TeamGenderCategory?,
        level: String?,
        currentUser: CurrentUser,
    ): Team {
        membershipService.requireManagerRole(organizationId, currentUser)
        teamRepository.findById(teamId, organizationId)
            ?: throw NotFoundException("TEAM_NOT_FOUND", "The team could not be found.")
        validateContactEmail(contactEmail)
        return try {
            teamRepository.update(teamId, organizationId, name, sport, season, contactEmail, ageGroup, genderCategory, level)
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

    /** Phase 24 slice 24.5 (ADR-071): [timezoneOverride] null explicitly clears back to "inherit organization default" — a real, distinct intent from "leave unchanged," unlike every coalesce-based field above. */
    @Transactional
    fun updateTimezoneOverride(
        organizationId: UUID,
        teamId: UUID,
        timezoneOverride: String?,
        currentUser: CurrentUser,
    ): Team {
        membershipService.requireManagerRole(organizationId, currentUser)
        teamRepository.findById(teamId, organizationId)
            ?: throw NotFoundException("TEAM_NOT_FOUND", "The team could not be found.")
        if (timezoneOverride != null) timeZoneService.requireValid(timezoneOverride)
        teamRepository.updateTimezoneOverride(teamId, organizationId, timezoneOverride)
        auditService.record(
            actorUserId = currentUser.userId,
            organizationId = organizationId,
            action = "team.timezone_override_updated",
            entityType = "team",
            entityId = teamId,
        )
        return teamRepository.findById(teamId, organizationId)!!
    }

    /** Phase 35 (ADR-099): null explicitly clears that color back to Rally26's default brand color — same explicit-set convention as [updateTimezoneOverride]. */
    @Transactional
    fun updateColors(
        organizationId: UUID,
        teamId: UUID,
        primaryColor: String?,
        secondaryColor: String?,
        currentUser: CurrentUser,
    ): Team {
        membershipService.requireManagerRole(organizationId, currentUser)
        teamRepository.findById(teamId, organizationId)
            ?: throw NotFoundException("TEAM_NOT_FOUND", "The team could not be found.")
        requireValidHexColor("primaryColor", primaryColor)
        requireValidHexColor("secondaryColor", secondaryColor)
        teamRepository.updateColors(teamId, organizationId, primaryColor, secondaryColor)
        auditService.record(
            actorUserId = currentUser.userId,
            organizationId = organizationId,
            action = "team.colors_updated",
            entityType = "team",
            entityId = teamId,
        )
        return teamRepository.findById(teamId, organizationId)!!
    }

    private fun requireValidHexColor(
        field: String,
        value: String?,
    ) {
        if (value != null && !Team.HEX_COLOR_PATTERN.matches(value)) {
            throw ValidationException(
                "Team colors must be a 6-digit hex value like #0B1F33.",
                listOf(FieldError(field, "Must be a 6-digit hex color like #0B1F33.")),
            )
        }
    }

    private fun validateContactEmail(email: String?) {
        if (email != null && !EMAIL_PATTERN.matches(email)) {
            throw ValidationException(
                "Contact email is not a valid email address.",
                listOf(FieldError("contactEmail", "Invalid email format.")),
            )
        }
    }
}
