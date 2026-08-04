package com.rally26.integration.quickbooks.web

import com.rally26.common.error.ValidationException
import com.rally26.common.web.CurrentUser
import com.rally26.integration.quickbooks.application.QuickBooksService
import com.rally26.integration.quickbooks.domain.QuickBooksMappingType
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/integrations/quickbooks")
class QuickBooksController(private val service: QuickBooksService) {
    @GetMapping
    fun overview(@PathVariable organizationId: UUID, @AuthenticationPrincipal currentUser: CurrentUser) =
        service.overview(organizationId, currentUser).toResponse()

    @PostMapping("/connections/{connectionId}/company/refresh")
    fun company(@PathVariable organizationId: UUID, @PathVariable connectionId: UUID, @AuthenticationPrincipal currentUser: CurrentUser) =
        service.readCompany(organizationId, connectionId, currentUser).toResponse()

    @GetMapping("/connections/{connectionId}/accounts")
    fun accounts(@PathVariable organizationId: UUID, @PathVariable connectionId: UUID, @AuthenticationPrincipal currentUser: CurrentUser) =
        service.listAccounts(organizationId, connectionId, currentUser).map { it.toResponse() }

    @PutMapping("/connections/{connectionId}/mappings")
    fun mapping(
        @PathVariable organizationId: UUID,
        @PathVariable connectionId: UUID,
        @RequestBody request: UpdateQuickBooksMappingRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ) = service.saveMapping(organizationId, connectionId, mappingType(request.mappingType), request.accountId, currentUser).toResponse()

    @PostMapping("/connections/{connectionId}/exports/preview")
    fun preview(
        @PathVariable organizationId: UUID,
        @PathVariable connectionId: UUID,
        @RequestBody request: QuickBooksExportPreviewRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ) = service.previewExport(organizationId, connectionId, request.periodStart, request.periodEnd, request.idempotencyKey, currentUser).toResponse()

    private fun mappingType(value: String): QuickBooksMappingType =
        runCatching { QuickBooksMappingType.valueOf(value.uppercase()) }
            .getOrElse { throw ValidationException("Unknown QuickBooks mapping type.") }
}
