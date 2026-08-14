package com.rally26.financialcorrection.web

import com.rally26.common.error.ValidationException
import com.rally26.common.web.CurrentUser
import com.rally26.common.web.PageResponse
import com.rally26.financialcorrection.application.FinancialCorrectionService
import com.rally26.financialcorrection.domain.FinancialCorrectionTargetType
import com.rally26.financialcorrection.domain.FinancialCorrectionType
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/financial-corrections")
class FinancialCorrectionController(
    private val service: FinancialCorrectionService,
) {
    @PostMapping("/preview")
    fun preview(
        @PathVariable organizationId: UUID,
        @Valid @RequestBody request: PreviewFinancialCorrectionRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): FinancialCorrectionPreviewResponse =
        service
            .preview(
                organizationId,
                request.targetType,
                request.targetId,
                request.amountMinor,
                request.reason,
                currentUser,
            ).toResponse()

    @PostMapping("/execute")
    fun execute(
        @PathVariable organizationId: UUID,
        @Valid @RequestBody request: ExecuteFinancialCorrectionRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<FinancialCorrectionResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(
            service
                .execute(
                    organizationId,
                    request.targetType,
                    request.targetId,
                    request.amountMinor,
                    request.reason,
                    request.confirmationHash,
                    request.idempotencyKey,
                    currentUser,
                ).toResponse(),
        )

    @GetMapping
    fun list(
        @PathVariable organizationId: UUID,
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) targetType: FinancialCorrectionTargetType?,
        @RequestParam(required = false) correctionType: FinancialCorrectionType?,
        @RequestParam(defaultValue = "newest") sort: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): PageResponse<FinancialCorrectionResponse> {
        val safePage = page.coerceAtLeast(0)
        val safeSize = size.coerceIn(1, 100)
        val ascending = parseAscending(sort)
        val items =
            service
                .search(
                    organizationId,
                    q,
                    targetType,
                    correctionType,
                    ascending,
                    safePage * safeSize,
                    safeSize,
                    currentUser,
                ).map { it.toResponse() }
        val total = service.countSearch(organizationId, q, targetType, correctionType, currentUser)
        return PageResponse(items, safePage, safeSize, total)
    }

    private fun parseAscending(sort: String): Boolean =
        when (sort.lowercase()) {
            "newest" -> false
            "oldest" -> true
            else -> throw ValidationException("sort must be 'newest' or 'oldest'.")
        }
}
