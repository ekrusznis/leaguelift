package com.rally26.profilecorrection.web

import com.rally26.profilecorrection.domain.ProfileCorrectionField
import com.rally26.profilecorrection.domain.ProfileCorrectionRequest
import com.rally26.profilecorrection.domain.ProfileCorrectionStatus
import com.rally26.profilecorrection.domain.ProfileCorrectionTargetType
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

data class CreateProfileCorrectionRequest(
    val targetType: ProfileCorrectionTargetType,
    val targetId: UUID,
    val field: ProfileCorrectionField,
    @field:NotBlank @field:Size(max = 500) val proposedValue: String,
    @field:NotBlank @field:Size(min = 5, max = 500) val reason: String,
)

data class ReviewProfileCorrectionRequest(
    @field:Size(max = 500) val reviewNote: String? = null,
)

data class RejectProfileCorrectionRequest(
    @field:NotBlank @field:Size(min = 3, max = 500) val reviewNote: String,
)

data class ProfileCorrectionResponse(
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

fun ProfileCorrectionRequest.toResponse() = ProfileCorrectionResponse(
    id = id,
    organizationId = organizationId,
    householdId = householdId,
    targetType = targetType,
    targetId = targetId,
    field = field,
    targetLabel = targetLabel,
    currentValue = currentValue,
    proposedValue = proposedValue,
    reason = reason,
    status = status,
    requestedBy = requestedBy,
    requesterName = requesterName,
    requesterEmail = requesterEmail,
    reviewedBy = reviewedBy,
    reviewerName = reviewerName,
    reviewNote = reviewNote,
    requestedAt = requestedAt,
    reviewedAt = reviewedAt,
    updatedAt = updatedAt,
)
