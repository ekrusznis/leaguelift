package com.leaguelift.financialcorrection.web

import com.leaguelift.common.web.CurrentUser
import com.leaguelift.common.web.PageResponse
import com.leaguelift.financialcorrection.application.FinancialCorrectionService
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
class FinancialCorrectionController(private val service: FinancialCorrectionService) {
    @PostMapping("/preview")
    fun preview(
        @PathVariable organizationId: UUID,
        @Valid @RequestBody request: PreviewFinancialCorrectionRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): FinancialCorrectionPreviewResponse = service.preview(
        organizationId, request.targetType, request.targetId, request.amountMinor, request.reason, currentUser,
    ).toResponse()

    @PostMapping("/execute")
    fun execute(
        @PathVariable organizationId: UUID,
        @Valid @RequestBody request: ExecuteFinancialCorrectionRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<FinancialCorrectionResponse> = ResponseEntity.status(HttpStatus.CREATED).body(
        service.execute(
            organizationId, request.targetType, request.targetId, request.amountMinor, request.reason,
            request.confirmationHash, request.idempotencyKey, currentUser,
        ).toResponse(),
    )

    @GetMapping
    fun list(
        @PathVariable organizationId: UUID,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): PageResponse<FinancialCorrectionResponse> {
        val items = service.list(organizationId, page * size, size, currentUser).map { it.toResponse() }
        return PageResponse(items, page, size, service.count(organizationId, currentUser))
    }
}
