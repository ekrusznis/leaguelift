package com.rally26.audit.domain

import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.Base64
import java.util.UUID

enum class AuditHistorySortField {
    DATE,
    ACTION,
    RESULT,
}

enum class AuditHistorySortDirection {
    ASC,
    DESC,
}

data class AuditHistoryQuery(
    val from: Instant? = null,
    val to: Instant? = null,
    val action: String? = null,
    val result: AuditResult? = null,
    val keyword: String? = null,
    val userQuery: String? = null,
    val organizationId: UUID? = null,
    val teamId: UUID? = null,
    val sortBy: AuditHistorySortField = AuditHistorySortField.DATE,
    val direction: AuditHistorySortDirection = AuditHistorySortDirection.DESC,
    val size: Int = 50,
    val cursor: AuditHistoryCursor? = null,
)

data class AuditHistoryCursor(
    val sortBy: AuditHistorySortField,
    val direction: AuditHistorySortDirection,
    val sortValue: String,
    val createdAt: Instant,
    val id: UUID,
)

data class AuditHistoryFilterAccess(
    val canFilterUser: Boolean,
    val canFilterTeam: Boolean,
    val canFilterOrganization: Boolean,
)

data class AuditHistoryItem(
    val id: UUID,
    val createdAt: Instant,
    val action: String,
    val result: AuditResult,
    val summary: String,
    val actorType: AuditActorType,
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

data class AuditHistoryPage(
    val items: List<AuditHistoryItem>,
    val nextCursor: String?,
    val filterAccess: AuditHistoryFilterAccess,
)

object AuditHistoryCursorCodec {
    private val encoder = Base64.getUrlEncoder().withoutPadding()
    private val decoder = Base64.getUrlDecoder()

    fun encode(cursor: AuditHistoryCursor): String {
        val sortValue = encoder.encodeToString(cursor.sortValue.toByteArray(StandardCharsets.UTF_8))
        val raw =
            listOf(
                cursor.sortBy.name,
                cursor.direction.name,
                cursor.createdAt.toString(),
                cursor.id.toString(),
                sortValue,
            ).joinToString("|")
        return encoder.encodeToString(raw.toByteArray(StandardCharsets.UTF_8))
    }

    fun decode(value: String): AuditHistoryCursor {
        val raw = String(decoder.decode(value), StandardCharsets.UTF_8)
        val parts = raw.split('|')
        require(parts.size == 5) { "Invalid audit-history cursor." }
        return AuditHistoryCursor(
            sortBy = AuditHistorySortField.valueOf(parts[0]),
            direction = AuditHistorySortDirection.valueOf(parts[1]),
            createdAt = Instant.parse(parts[2]),
            id = UUID.fromString(parts[3]),
            sortValue = String(decoder.decode(parts[4]), StandardCharsets.UTF_8),
        )
    }

    fun sortValue(
        item: AuditHistoryItem,
        sortBy: AuditHistorySortField,
    ): String =
        when (sortBy) {
            AuditHistorySortField.DATE -> ""
            AuditHistorySortField.ACTION -> item.action.lowercase()
            AuditHistorySortField.RESULT -> item.result.name
        }
}
