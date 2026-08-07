package com.rally26.timezone.application

import com.rally26.common.error.FieldError
import com.rally26.common.error.ValidationException
import com.rally26.organization.persistence.OrganizationRepository
import com.rally26.team.persistence.TeamRepository
import com.rally26.tournament.persistence.TournamentRepository
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

/**
 * Phase 24 slice 24.5 (ADR-071): the single shared source of truth for timezone
 * validation, override-chain resolution, and DST-safe local<->instant conversion.
 * [com.rally26.event.application.EventService.requireValidTimezone] used to own
 * validation alone — that logic now lives here so every other caller (organization/
 * team/tournament settings, future report/notification formatting) shares it too.
 */
@Service
class TimeZoneService(
    private val organizationRepository: OrganizationRepository,
    private val teamRepository: TeamRepository,
    private val tournamentRepository: TournamentRepository,
) {
    fun requireValid(timezone: String): ZoneId =
        try {
            ZoneId.of(timezone)
        } catch (e: Exception) {
            throw ValidationException(
                "Timezone must be a valid IANA time zone id (e.g. America/New_York).",
                listOf(FieldError("timezone", "Invalid time zone.")),
            )
        }

    /**
     * Resolution order: team override -> tournament override -> organization default ->
     * [FALLBACK_ZONE]. Team and tournament are already mutually exclusive on an
     * [com.rally26.event.domain.Event] (an event belongs to at most one of the two), so
     * this never has to arbitrate between both overrides being present.
     */
    fun resolveEffectiveZone(
        organizationId: UUID,
        teamId: UUID?,
        tournamentId: UUID?,
    ): String {
        if (teamId != null) {
            teamRepository.findById(teamId, organizationId)?.timezoneOverride?.let { return it }
        }
        if (tournamentId != null) {
            tournamentRepository.findById(tournamentId, organizationId)?.timezoneOverride?.let { return it }
        }
        organizationRepository.findById(organizationId)?.timezone?.let { return it }
        return FALLBACK_ZONE
    }

    /**
     * Converts a wall-clock local time to a real instant, with explicit DST handling
     * rather than `java.time`'s default silent forward-shift:
     * - **Gap** (a local time that doesn't exist, e.g. 2:30 AM on a US spring-forward
     *   day): rejected with a [ValidationException] naming the exact gap.
     * - **Overlap** (an ambiguous local time, e.g. 1:30 AM on a US fall-back day):
     *   resolved deterministically to the pre-transition offset (the earlier of the two
     *   real moments that local time could mean).
     */
    fun resolveInstant(
        localDateTime: LocalDateTime,
        zone: ZoneId,
    ): Instant {
        val transition = zone.rules.getTransition(localDateTime)
        if (transition != null && transition.isGap) {
            throw ValidationException(
                "The time $localDateTime does not exist in $zone — clocks jump from " +
                    "${transition.dateTimeBefore} to ${transition.dateTimeAfter} for daylight saving time.",
                listOf(FieldError("startAt", "This local time falls in a daylight saving time gap.")),
            )
        }
        if (transition != null && transition.isOverlap) {
            return localDateTime.atOffset(transition.offsetBefore).toInstant()
        }
        return localDateTime.atZone(zone).toInstant()
    }

    fun toLocal(
        instant: Instant,
        zone: ZoneId,
    ): LocalDateTime = LocalDateTime.ofInstant(instant, zone)

    companion object {
        /** A real last-resort only — never presented to a user as an authoritative default. */
        const val FALLBACK_ZONE = "America/New_York"
    }
}
