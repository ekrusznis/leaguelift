package com.rally26.foundingorg.web

import com.rally26.foundingorg.application.FoundingPromoCodeService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** Public — see the permitAll matcher for this path in `SecurityConfig`. Used by the founding-org join page before showing the registration form. */
@RestController
@RequestMapping("/api/v1/founding-promo-codes")
class FoundingOrgPromoCodeController(
    private val service: FoundingPromoCodeService,
) {
    @GetMapping("/{code}/validate")
    fun validate(
        @PathVariable code: String,
    ): FoundingCodeValidationResponse = service.validate(code).toResponse()
}
