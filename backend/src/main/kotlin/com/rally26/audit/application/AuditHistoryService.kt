package com.rally26.audit.application

import com.rally26.audit.domain.AuditHistoryCursor
import com.rally26.audit.domain.AuditHistoryCursorCodec
import com.rally26.audit.domain.AuditHistoryPage
import com.rally26.audit.domain.AuditHistoryQuery
import com.rally26.audit.persistence.AuditHistoryRepository
import com.rally26.common.error.ForbiddenException
import com.rally26.common.web.CurrentUser
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

@Service
class AuditHistoryService(
    private val repository: AuditHistoryRepository,
) {
    fun search(
        currentUser: CurrentUser,
        from: Instant?,
        to: Instant?,
        action: String?,
        result: com.rally26.audit.domain.AuditResult?,
        keyword: String?,
        userQuery: String?,
        organizationId: UUID?,
        teamId: UUID?,
        sortBy: com.rally26.audit.domain.AuditHistorySortField,
        direction: com.rally26.audit.domain.AuditHistorySortDirection,
        size: Int,
        cursor: AuditHistoryCursor?,
    ): AuditHistoryPage {
        require(size in 1..100) { "History page size must be between 1 and 100." }
        require(from == null || to == null || from.isBefore(to)) { "History 'from' must be before 'to'." }
        require((action?.length ?: 0) <= 120) { "History action filter is too long." }
        require((keyword?.length ?: 0) <= 200) { "History keyword filter is too long." }
        require((userQuery?.length ?: 0) <= 120) { "History user filter is too long." }
        if (cursor != null) {
            require(cursor.sortBy == sortBy && cursor.direction == direction) { "History cursor does not match the requested sort." }
        }

        val access = repository.resolveFilterAccess(currentUser.userId, currentUser.platformAdministrator)
        if (!userQuery.isNullOrBlank() && !access.canFilterUser) {
            throw ForbiddenException("AUDIT_USER_FILTER_DENIED", "Your current role cannot filter history by user.")
        }
        if (teamId != null && !access.canFilterTeam) {
            throw ForbiddenException("AUDIT_TEAM_FILTER_DENIED", "Your current role cannot filter history by team.")
        }
        if (organizationId != null && !access.canFilterOrganization) {
            throw ForbiddenException("AUDIT_ORGANIZATION_FILTER_DENIED", "Your current role cannot filter history by organization.")
        }

        val query =
            AuditHistoryQuery(
                from = from,
                to = to,
                action = action?.trim()?.takeIf { it.isNotEmpty() },
                result = result,
                keyword = keyword?.trim()?.takeIf { it.isNotEmpty() },
                userQuery = userQuery?.trim()?.takeIf { it.isNotEmpty() },
                organizationId = organizationId,
                teamId = teamId,
                sortBy = sortBy,
                direction = direction,
                size = size,
                cursor = cursor,
            )
        val fetched = repository.search(currentUser.userId, currentUser.platformAdministrator, query)
        val items = fetched.take(size)
        val nextCursor =
            if (fetched.size > size && items.isNotEmpty()) {
                val last = items.last()
                AuditHistoryCursorCodec.encode(
                    AuditHistoryCursor(
                        sortBy = sortBy,
                        direction = direction,
                        sortValue = AuditHistoryCursorCodec.sortValue(last, sortBy),
                        createdAt = last.createdAt,
                        id = last.id,
                    ),
                )
            } else {
                null
            }
        return AuditHistoryPage(items = items, nextCursor = nextCursor, filterAccess = access)
    }
}
