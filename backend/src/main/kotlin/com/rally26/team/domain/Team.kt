package com.rally26.team.domain

import java.time.Instant
import java.util.UUID

enum class TeamStatus { ACTIVE, ARCHIVED }

enum class TeamGenderCategory { BOYS, GIRLS, COED, MENS, WOMENS, OPEN }

data class Team(
    val id: UUID,
    val organizationId: UUID,
    val name: String,
    val sport: String,
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
