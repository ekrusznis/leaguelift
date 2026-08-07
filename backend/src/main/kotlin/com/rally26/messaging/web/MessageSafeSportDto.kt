package com.rally26.messaging.web

import com.rally26.messaging.domain.GuardianMessagingParticipant
import com.rally26.messaging.domain.MessageContactRestriction
import com.rally26.messaging.domain.MessageContactRestrictionKind
import com.rally26.messaging.domain.MessageSafeSportPolicy
import com.rally26.messaging.domain.MessageSafeSportReviewStatus
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

data class UpdateMessageSafeSportPolicyRequest(
    val reviewStatus: MessageSafeSportReviewStatus,
    val athleteMessagingEnabled: Boolean = false,
    @field:Size(max = 500) val reviewReference: String? = null,
)

data class CreateMessageContactRestrictionRequest(
    val organizationId: UUID,
    val participantId: UUID,
    val kind: MessageContactRestrictionKind,
    @field:Size(max = 1000) val note: String? = null,
)

data class LiftMessageContactRestrictionRequest(
    @field:NotBlank @field:Size(min = 3, max = 1000) val note: String,
)

data class MessageSafeSportPolicyResponse(
    val organizationId: UUID,
    val reviewStatus: String,
    val athleteMessagingEnabled: Boolean,
    val reviewReference: String?,
    val reviewedByUserId: UUID?,
    val reviewedAt: Instant?,
    val retentionMode: String,
    val guardianVisibilityRequired: Boolean,
    val adultMinorOpenTransparentRequired: Boolean,
    val parentDiscontinueRequestsEnforced: Boolean,
    val updatedAt: Instant,
)

data class GuardianMessagingParticipantResponse(
    val organizationId: UUID,
    val participantId: UUID,
    val displayName: String,
)

data class MessageContactRestrictionResponse(
    val id: UUID,
    val organizationId: UUID,
    val participantId: UUID,
    val participantDisplayName: String,
    val requestedByUserId: UUID,
    val kind: String,
    val note: String?,
    val status: String,
    val createdAt: Instant,
    val liftedAt: Instant?,
    val liftedByUserId: UUID?,
    val liftNote: String?,
)

fun MessageSafeSportPolicy.toResponse() =
    MessageSafeSportPolicyResponse(
        organizationId,
        reviewStatus.name,
        athleteMessagingEnabled,
        reviewReference,
        reviewedByUserId,
        reviewedAt,
        retentionMode.name,
        guardianVisibilityRequired,
        adultMinorOpenTransparentRequired,
        parentDiscontinueRequestsEnforced,
        updatedAt,
    )

fun GuardianMessagingParticipant.toResponse() = GuardianMessagingParticipantResponse(organizationId, participantId, displayName)

fun MessageContactRestriction.toResponse() =
    MessageContactRestrictionResponse(
        id,
        organizationId,
        participantId,
        participantDisplayName,
        requestedByUserId,
        kind.name,
        note,
        status.name,
        createdAt,
        liftedAt,
        liftedByUserId,
        liftNote,
    )
