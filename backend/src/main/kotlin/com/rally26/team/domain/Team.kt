package com.rally26.team.domain

import java.time.Instant
import java.util.UUID

enum class TeamStatus { ACTIVE, ARCHIVED }

enum class TeamGenderCategory { BOYS, GIRLS, COED, MENS, WOMENS, OPEN }

/**
 * Drives the sport-terminology matrix (§8) — real UI vocabulary (Game/Match/Meet,
 * Player/Athlete/Swimmer, Court/Field/Rink/Pool) is resolved from this on any screen
 * scoped to one specific team; anything without a single team in view (a "My Teams"
 * list, a cross-team dashboard) stays generic on purpose, since one coach/athlete can
 * span multiple sports. [OTHER] covers a real but uncommon sport — its actual name is
 * preserved in [Team.sportOtherLabel] rather than lost.
 */
enum class Sport {
    SOCCER,
    BASKETBALL,
    BASEBALL,
    SOFTBALL,
    FOOTBALL,
    ICE_HOCKEY,
    FIELD_HOCKEY,
    VOLLEYBALL,
    LACROSSE,
    SWIMMING,
    TRACK_AND_FIELD,
    CROSS_COUNTRY,
    TENNIS,
    WRESTLING,
    CHEERLEADING,
    GYMNASTICS,
    GOLF,
    RUGBY,
    OTHER,
}

data class Team(
    val id: UUID,
    val organizationId: UUID,
    val name: String,
    val sport: Sport,
    val season: String?,
    val status: TeamStatus,
    val contactEmail: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
    /** Phase 24 slice 24.5 (ADR-071): null means "inherit organization default" — a real value overrides it. */
    val timezoneOverride: String? = null,
    /** Phase 35 (ADR-099): organization-defined free text, deliberately not a hardcoded global list. */
    val ageGroup: String? = null,
    val genderCategory: TeamGenderCategory? = null,
    val level: String? = null,
    /** Only meaningful when [sport] is [Sport.OTHER] — the org's real sport name, e.g. "Ultimate Frisbee". */
    val sportOtherLabel: String? = null,
    /** Phase 35 (ADR-099): null means "use Rally26's default brand color" — see [resolvedPrimaryColor]/[resolvedSecondaryColor]. */
    val primaryColor: String? = null,
    val secondaryColor: String? = null,
) {
    val resolvedPrimaryColor: String get() = primaryColor ?: DEFAULT_PRIMARY_COLOR
    val resolvedSecondaryColor: String get() = secondaryColor ?: DEFAULT_SECONDARY_COLOR

    companion object {
        const val DEFAULT_PRIMARY_COLOR = "#0B1F33"
        const val DEFAULT_SECONDARY_COLOR = "#20B26B"
        val HEX_COLOR_PATTERN = Regex("^#[0-9A-Fa-f]{6}$")
    }
}
