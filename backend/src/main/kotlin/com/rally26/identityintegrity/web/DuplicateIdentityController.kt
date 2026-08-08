package com.rally26.identityintegrity.web

import com.rally26.common.web.CurrentUser
import com.rally26.identityintegrity.application.DuplicateIdentityService
import com.rally26.identityintegrity.domain.DuplicateIdentityKind
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

@RestController
@RequestMapping("/api/v1/platform/admin/data-integrity/duplicates")
class DuplicateIdentityController(
    private val service: DuplicateIdentityService,
) {
    @GetMapping
    fun candidates(
        @RequestParam(required = false) query: String?,
        @RequestParam(defaultValue = "50") size: Int,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): DuplicateCandidateListResponse =
        try {
            DuplicateCandidateListResponse(service.candidates(currentUser, query, size).map { it.toResponse() })
        } catch (exception: IllegalArgumentException) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, exception.message, exception)
        }

    @GetMapping("/preview")
    fun preview(
        @RequestParam sourceKind: DuplicateIdentityKind,
        @RequestParam sourceId: UUID,
        @RequestParam targetKind: DuplicateIdentityKind,
        @RequestParam targetId: UUID,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): DuplicateMergePreviewResponse =
        try {
            service.preview(currentUser, sourceKind, sourceId, targetKind, targetId).toResponse()
        } catch (exception: IllegalArgumentException) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, exception.message, exception)
        }
}
