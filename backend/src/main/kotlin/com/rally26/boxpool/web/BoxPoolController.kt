package com.rally26.boxpool.web

import com.rally26.boxpool.application.BoxPoolService
import com.rally26.common.web.CurrentUser
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/campaigns/{campaignId}/box-pool")
class BoxPoolController(
    private val boxPoolService: BoxPoolService,
) {
    @PostMapping
    fun create(
        @PathVariable organizationId: UUID,
        @PathVariable campaignId: UUID,
        @Valid @RequestBody request: CreateBoxPoolRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<BoxPoolResponse> {
        boxPoolService.create(
            organizationId,
            campaignId,
            request.sport,
            request.rows,
            request.cols,
            request.pricePerBoxMinor,
            request.rowAxisLabel,
            request.colAxisLabel,
            request.prizeDescription,
            currentUser,
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(boxPoolService.get(organizationId, campaignId, currentUser).toResponse())
    }

    @GetMapping
    fun get(
        @PathVariable organizationId: UUID,
        @PathVariable campaignId: UUID,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): BoxPoolResponse = boxPoolService.get(organizationId, campaignId, currentUser).toResponse()
}
