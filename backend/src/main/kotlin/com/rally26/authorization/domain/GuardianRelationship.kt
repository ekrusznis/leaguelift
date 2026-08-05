package com.rally26.authorization.domain

import java.time.Instant
import java.util.UUID

enum class GuardianRelationshipStatus { ACTIVE, REVOKED }

/**
 * The real FK-backed link between an `app_user` and the `household_adult` record they
 * are. Replaces `HouseholdRepository.findActiveAdultByEmail`'s email-matching interim
 * heuristic (DESIGN-DOC.md section 8.3) as the source of truth for the HOUSEHOLD
 * context going forward — see ADR-020's consequences for what still relies on the old
 * heuristic as a fallback rather than being migrated in this slice.
 */
data class GuardianRelationship(
    val id: UUID,
    val organizationId: UUID,
    val householdId: UUID,
    val householdAdultId: UUID,
    val userId: UUID,
    val status: GuardianRelationshipStatus,
    val createdAt: Instant,
    val updatedAt: Instant,
)
