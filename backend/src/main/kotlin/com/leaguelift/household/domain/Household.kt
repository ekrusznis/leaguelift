package com.leaguelift.household.domain

import java.time.Instant
import java.util.UUID

enum class HouseholdStatus { ACTIVE, ARCHIVED }

data class Household(
    val id: UUID,
    val organizationId: UUID,
    val displayName: String,
    val contactEmail: String?,
    val contactPhone: String?,
    val notes: String?,
    val status: HouseholdStatus,
    val createdAt: Instant,
    val updatedAt: Instant,
)

enum class AdultStatus { ACTIVE, ARCHIVED }

data class HouseholdAdult(
    val id: UUID,
    val householdId: UUID,
    val organizationId: UUID,
    val firstName: String,
    val lastName: String,
    val email: String?,
    val phone: String?,
    val relationship: String?,
    val isPrimary: Boolean,
    val status: AdultStatus,
    val createdAt: Instant,
    val updatedAt: Instant,
)
