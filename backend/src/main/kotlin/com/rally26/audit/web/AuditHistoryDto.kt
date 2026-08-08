package com.rally26.audit.web

import com.rally26.audit.domain.AuditHistoryFilterAccess
import com.rally26.audit.domain.AuditHistoryItem
import com.rally26.audit.domain.AuditHistoryPage
import java.time.Instant
import java.util.UUID

data class AuditHistoryPageResponse(
    val items: List<AuditHistoryItemResponse>,
    val nextCursor: String?,
    val filterAccess: AuditHistoryFilterAccessResponse,
)

data class AuditHistoryFilterAccessResponse(
    val canFilterUser: Boolean,
    val canFilterTeam: Boolean,
    val canFilterOrganization: Boolean,
)

data class AuditHistoryItemResponse(
    val id: UUID,
    val occurredAt: Instant,
    val action: String,
    val result: String,
    val summary: String,
    val actorType: String,
    val actorUserId: UUID?,
    val actorDisplayName: String?,
    val targetUserId: UUID?,
    val targetDisplayName: String?,
    val organizationId: UUID?,
    val organizationName: String?,
    val teamId: UUID?,
    val teamName: String?,
    val householdId: UUID?,
    val householdName: String?,
    val participantId: UUID?,
    val participantDisplayName: String?,
    val entityType: String,
    val entityId: UUID,
)

fun AuditHistoryPage.toResponse() =
    AuditHistoryPageResponse(
        items = items.map { it.toResponse() },
        nextCursor = nextCursor,
        filterAccess = filterAccess.toResponse(),
    )

private fun AuditHistoryFilterAccess.toResponse() =
    AuditHistoryFilterAccessResponse(
        canFilterUser = canFilterUser,
        canFilterTeam = canFilterTeam,
        canFilterOrganization = canFilterOrganization,
    )

private fun AuditHistoryItem.toResponse() =
    AuditHistoryItemResponse(
        id = id,
        occurredAt = createdAt,
        action = action,
        result = result.name,
        summary = summary,
        actorType = actorType.name,
        actorUserId = actorUserId,
        actorDisplayName = actorDisplayName,
        targetUserId = targetUserId,
        targetDisplayName = targetDisplayName,
        organizationId = organizationId,
        organizationName = organizationName,
        teamId = teamId,
        teamName = teamName,
        householdId = householdId,
        householdName = householdName,
        participantId = participantId,
        participantDisplayName = participantDisplayName,
        entityType = entityType,
        entityId = entityId,
    )
