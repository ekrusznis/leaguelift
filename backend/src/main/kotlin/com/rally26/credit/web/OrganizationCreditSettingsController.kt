package com.rally26.credit.web

import com.rally26.common.web.CurrentUser
import com.rally26.credit.application.FamilyCreditService
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/** Phase 23 (DESIGN-DOC.md section 13/14.1): org-owner-configurable credit percentage, expiration policy, and P2P transfer toggle. */
@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/credit-settings")
class OrganizationCreditSettingsController(
    private val familyCreditService: FamilyCreditService,
) {
    @GetMapping
    fun get(
        @PathVariable organizationId: UUID,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): OrganizationCreditSettingsResponse = familyCreditService.getSettings(organizationId, currentUser).toResponse()

    @PutMapping
    fun update(
        @PathVariable organizationId: UUID,
        @Valid @RequestBody request: UpdateOrganizationCreditSettingsRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): OrganizationCreditSettingsResponse =
        familyCreditService
            .updateSettings(
                organizationId,
                request.defaultCreditPercent,
                request.expirationPolicy,
                request.expirationMonths,
                request.p2pTransferEnabled,
                currentUser,
            ).toResponse()
}
