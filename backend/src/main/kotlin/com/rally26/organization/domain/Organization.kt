package com.rally26.organization.domain

import java.time.Instant
import java.util.UUID

enum class OrganizationType {
    RECREATIONAL_LEAGUE,
    TRAVEL_CLUB,
    INDIVIDUAL_TEAM,
    TOURNAMENT_OPERATOR,
    BOOSTER_ORGANIZATION,
    MULTISPORT_FACILITY,
    COMMUNITY_PROGRAM,
    OTHER,
}

enum class OrganizationStatus { DRAFT, ACTIVE, SUSPENDED, ARCHIVED }

data class Organization(
    val id: UUID,
    val name: String,
    val slug: String,
    val organizationType: OrganizationType,
    val status: OrganizationStatus,
    val sports: List<String>,
    val contactEmail: String?,
    val contactPhone: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val addressLine1: String? = null,
    val addressLine2: String? = null,
    val addressCity: String? = null,
    val addressState: String? = null,
    val addressPostalCode: String? = null,
    val addressCountry: String? = null,
    /** Phase 24 slice 24.5 (ADR-071): only ever set via an owner-initiated confirm action; a non-null value IS the confirmation signal. */
    val timezone: String? = null,
)

private val SLUG_PATTERN = Regex("^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?$")
private val EMAIL_PATTERN = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")

fun isValidSlug(slug: String): Boolean = SLUG_PATTERN.matches(slug)

fun isValidContactEmail(email: String): Boolean = EMAIL_PATTERN.matches(email)

/** Lower-cases and trims a candidate name into a slug-safe default; callers may override. */
fun slugify(input: String): String =
    input
        .trim()
        .lowercase()
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')
        .take(63)
