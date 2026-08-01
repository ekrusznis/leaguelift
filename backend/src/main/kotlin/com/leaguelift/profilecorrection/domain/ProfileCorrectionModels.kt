package com.leaguelift.profilecorrection.domain

import java.time.Instant
import java.util.UUID

enum class ProfileCorrectionTargetType { HOUSEHOLD_ADULT, PARTICIPANT }

enum class ProfileCorrectionField(val targetType: ProfileCorrectionTargetType) {
    ADULT_FIRST_NAME(ProfileCorrectionTargetType.HOUSEHOLD_ADULT),
    ADULT_LAST_NAME(ProfileCorrectionTargetType.HOUSEHOLD_ADULT),
    ADULT_EMAIL(ProfileCorrectionTargetType.HOUSEHOLD_ADULT),
    ADULT_PHONE(ProfileCorrectionTargetType.HOUSEHOLD_ADULT),
    ADULT_RELATIONSHIP(ProfileCorrectionTargetType.HOUSEHOLD_ADULT),
    PARTICIPANT_FIRST_NAME(ProfileCorrectionTargetType.PARTICIPANT),
    PARTICIPANT_LAST_NAME(ProfileCorrectionTargetType.PARTICIPANT),
    PARTICIPANT_DATE_OF_BIRTH(ProfileCorrectionTargetType.PARTICIPANT),
}

enum class ProfileCorrectionStatus { PENDING, APPROVED, REJECTED, WITHDRAWN }

data class ProfileCorrectionRequest(
    val id: UUID,
    val organizationId: UUID,
    val householdId: UUID,
    val targetType: ProfileCorrectionTargetType,
    val targetId: UUID,
    val field: ProfileCorrectionField,
    val targetLabel: String,
    val currentValue: String?,
    val proposedValue: String,
    val reason: String,
    val status: ProfileCorrectionStatus,
    val requestedBy: UUID,
    val requesterName: String,
    val requesterEmail: String,
    val reviewedBy: UUID?,
    val reviewerName: String?,
    val reviewNote: String?,
    val requestedAt: Instant,
    val reviewedAt: Instant?,
    val updatedAt: Instant,
)
