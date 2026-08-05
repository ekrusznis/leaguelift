package com.rally26.fee.web

import com.rally26.common.web.CurrentUser
import com.rally26.common.web.PageResponse
import com.rally26.fee.application.FeeService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/fee-templates")
class FeeTemplateController(
    private val feeService: FeeService,
) {
    @GetMapping
    fun list(
        @PathVariable organizationId: UUID,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): PageResponse<FeeTemplateResponse> {
        val offset = page * size
        val items = feeService.listTemplates(organizationId, currentUser, offset, size).map { it.toResponse() }
        val total = feeService.countTemplates(organizationId, currentUser)
        return PageResponse(items, page, size, total)
    }

    @PostMapping
    fun create(
        @PathVariable organizationId: UUID,
        @Valid @RequestBody request: CreateFeeTemplateRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<FeeTemplateResponse> {
        val template =
            feeService.createTemplate(
                organizationId,
                request.name,
                request.description,
                request.amountMinor,
                request.currency,
                currentUser,
            )
        return ResponseEntity.status(HttpStatus.CREATED).body(template.toResponse())
    }

    @GetMapping("/{templateId}")
    fun get(
        @PathVariable organizationId: UUID,
        @PathVariable templateId: UUID,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): FeeTemplateResponse = feeService.getTemplate(organizationId, templateId, currentUser).toResponse()

    @PatchMapping("/{templateId}")
    fun update(
        @PathVariable organizationId: UUID,
        @PathVariable templateId: UUID,
        @Valid @RequestBody request: UpdateFeeTemplateRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): FeeTemplateResponse =
        feeService
            .updateTemplate(
                organizationId,
                templateId,
                request.name,
                request.description,
                request.amountMinor,
                currentUser,
            ).toResponse()

    @DeleteMapping("/{templateId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun archive(
        @PathVariable organizationId: UUID,
        @PathVariable templateId: UUID,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ) = feeService.archiveTemplate(organizationId, templateId, currentUser)
}
