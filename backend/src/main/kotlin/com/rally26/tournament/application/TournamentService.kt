package com.rally26.tournament.application

import com.rally26.audit.application.AuditService
import com.rally26.common.error.ConflictException
import com.rally26.common.error.NotFoundException
import com.rally26.common.error.ValidationException
import com.rally26.common.web.CurrentUser
import com.rally26.membership.application.MembershipService
import com.rally26.timezone.application.TimeZoneService
import com.rally26.tournament.domain.Tournament
import com.rally26.tournament.persistence.TournamentRepository
import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.util.UUID

private val EMAIL_PATTERN = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")

@Service
class TournamentService(
    private val tournamentRepository: TournamentRepository,
    private val membershipService: MembershipService,
    private val auditService: AuditService,
    private val timeZoneService: TimeZoneService,
) {
    fun list(
        organizationId: UUID,
        currentUser: CurrentUser,
        offset: Int,
        limit: Int,
    ): List<Tournament> {
        membershipService.requireActiveMembership(organizationId, currentUser)
        return tournamentRepository.findAll(organizationId, offset, limit)
    }

    fun count(
        organizationId: UUID,
        currentUser: CurrentUser,
    ): Long {
        membershipService.requireActiveMembership(organizationId, currentUser)
        return tournamentRepository.countAll(organizationId)
    }

    fun get(
        organizationId: UUID,
        tournamentId: UUID,
        currentUser: CurrentUser,
    ): Tournament {
        membershipService.requireActiveMembership(organizationId, currentUser)
        return tournamentRepository.findById(tournamentId, organizationId)
            ?: throw NotFoundException("TOURNAMENT_NOT_FOUND", "The tournament could not be found.")
    }

    @Transactional
    fun create(
        organizationId: UUID,
        name: String,
        sport: String?,
        startDate: LocalDate?,
        endDate: LocalDate?,
        location: String?,
        contactEmail: String?,
        currentUser: CurrentUser,
    ): Tournament {
        membershipService.requireManagerRole(organizationId, currentUser)
        validateDates(startDate, endDate)
        validateContactEmail(contactEmail)
        return try {
            val tournament = tournamentRepository.insert(organizationId, name, sport, startDate, endDate, location, contactEmail)
            auditService.record(
                actorUserId = currentUser.userId,
                organizationId = organizationId,
                action = "tournament.created",
                entityType = "tournament",
                entityId = tournament.id,
            )
            tournament
        } catch (e: DuplicateKeyException) {
            throw ConflictException(
                "TOURNAMENT_NAME_TAKEN",
                "A tournament with this name already exists in the organization.",
            )
        }
    }

    @Transactional
    fun update(
        organizationId: UUID,
        tournamentId: UUID,
        name: String?,
        sport: String?,
        startDate: LocalDate?,
        endDate: LocalDate?,
        location: String?,
        contactEmail: String?,
        currentUser: CurrentUser,
    ): Tournament {
        membershipService.requireManagerRole(organizationId, currentUser)
        tournamentRepository.findById(tournamentId, organizationId)
            ?: throw NotFoundException("TOURNAMENT_NOT_FOUND", "The tournament could not be found.")
        validateDates(startDate, endDate)
        validateContactEmail(contactEmail)
        return try {
            tournamentRepository.update(tournamentId, organizationId, name, sport, startDate, endDate, location, contactEmail)
            auditService.record(
                actorUserId = currentUser.userId,
                organizationId = organizationId,
                action = "tournament.updated",
                entityType = "tournament",
                entityId = tournamentId,
            )
            tournamentRepository.findById(tournamentId, organizationId)!!
        } catch (e: DuplicateKeyException) {
            throw ConflictException(
                "TOURNAMENT_NAME_TAKEN",
                "A tournament with this name already exists in the organization.",
            )
        }
    }

    @Transactional
    fun archive(
        organizationId: UUID,
        tournamentId: UUID,
        currentUser: CurrentUser,
    ) {
        membershipService.requireManagerRole(organizationId, currentUser)
        val rows = tournamentRepository.archive(tournamentId, organizationId)
        if (rows == 0) throw NotFoundException("TOURNAMENT_NOT_FOUND", "The tournament could not be found.")
        auditService.record(
            actorUserId = currentUser.userId,
            organizationId = organizationId,
            action = "tournament.archived",
            entityType = "tournament",
            entityId = tournamentId,
        )
    }

    /** Phase 24 slice 24.5 (ADR-071): [timezoneOverride] null explicitly clears back to "inherit organization default." */
    @Transactional
    fun updateTimezoneOverride(
        organizationId: UUID,
        tournamentId: UUID,
        timezoneOverride: String?,
        currentUser: CurrentUser,
    ): Tournament {
        membershipService.requireManagerRole(organizationId, currentUser)
        tournamentRepository.findById(tournamentId, organizationId)
            ?: throw NotFoundException("TOURNAMENT_NOT_FOUND", "The tournament could not be found.")
        if (timezoneOverride != null) timeZoneService.requireValid(timezoneOverride)
        tournamentRepository.updateTimezoneOverride(tournamentId, organizationId, timezoneOverride)
        auditService.record(
            actorUserId = currentUser.userId,
            organizationId = organizationId,
            action = "tournament.timezone_override_updated",
            entityType = "tournament",
            entityId = tournamentId,
        )
        return tournamentRepository.findById(tournamentId, organizationId)!!
    }

    private fun validateDates(
        startDate: LocalDate?,
        endDate: LocalDate?,
    ) {
        if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
            throw ValidationException(
                "End date must not be before start date.",
                listOf(
                    com.rally26.common.error
                        .FieldError("endDate", "End date must not be before start date."),
                ),
            )
        }
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
