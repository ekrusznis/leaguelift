package com.rally26.foundingorg.web

import com.rally26.common.web.CurrentUser
import com.rally26.foundingorg.application.FoundingPromoCodeService
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/platform/admin/founding-promo-codes")
class PlatformFoundingPromoCodeController(
    private val service: FoundingPromoCodeService,
) {
    @GetMapping
    fun listCodes(
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): List<FoundingOrgPromoCodeResponse> = service.listCodes(currentUser).map { it.toResponse() }

    @PostMapping
    fun generateCode(
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): FoundingOrgPromoCodeResponse = service.generateCode(currentUser).toResponse()
}
