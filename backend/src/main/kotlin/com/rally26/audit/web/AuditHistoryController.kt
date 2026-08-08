package com.rally26.audit.web

import com.rally26.audit.application.AuditHistoryService
import com.rally26.audit.domain.AuditHistoryCursorCodec
import com.rally26.audit.domain.AuditHistorySortDirection
import com.rally26.audit.domain.AuditHistorySortField
import com.rally26.audit.domain.AuditResult
import com.rally26.common.web.CurrentUser
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.UUID

@RestController
@RequestMapping("/api/v1/audit-history")
class AuditHistoryController(
    private val service: AuditHistoryService,
) {
    @GetMapping
    fun history(
        @RequestParam(required = false) from: Instant?,
        @RequestParam(required = false) to: Instant?,
        @RequestParam(required = false) action: String?,
        @RequestParam(required = false) result: AuditResult?,
        @RequestParam(required = false) keyword: String?,
        @RequestParam(required = false, name = "user") userQuery: String?,
        @RequestParam(required = false) organizationId: UUID?,
        @RequestParam(required = false) teamId: UUID?,
        @RequestParam(defaultValue = "DATE") sortBy: AuditHistorySortField,
        @RequestParam(defaultValue = "DESC") direction: AuditHistorySortDirection,
        @RequestParam(defaultValue = "50") size: Int,
        @RequestParam(required = false) cursor: String?,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): AuditHistoryPageResponse {
        val decodedCursor =
            cursor?.takeIf { it.isNotBlank() }?.let {
                try {
                    AuditHistoryCursorCodec.decode(it)
                } catch (exception: RuntimeException) {
                    throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid audit-history cursor.", exception)
                }
            }
        return try {
            service
                .search(
                    currentUser = currentUser,
                    from = from,
                    to = to,
                    action = action,
                    result = result,
                    keyword = keyword,
                    userQuery = userQuery,
                    organizationId = organizationId,
                    teamId = teamId,
                    sortBy = sortBy,
                    direction = direction,
                    size = size,
                    cursor = decodedCursor,
                ).toResponse()
        } catch (exception: IllegalArgumentException) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, exception.message, exception)
        }
    }
}
