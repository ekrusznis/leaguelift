package com.rally26.fundraising.web

import com.rally26.common.error.ValidationException
import com.rally26.common.web.CurrentUser
import com.rally26.common.web.PageResponse
import com.rally26.finance.domain.PaymentSource
import com.rally26.fundraising.application.ContributionSearchService
import com.rally26.fundraising.domain.ContributionListCriteria
import com.rally26.fundraising.domain.ContributionListSort
import com.rally26.fundraising.domain.ContributionStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/campaigns/{campaignId}/contributions/search")
class ContributionSearchController(
    private val service: ContributionSearchService,
) {
    @GetMapping
    fun search(
        @PathVariable organizationId: UUID,
        @PathVariable campaignId: UUID,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "25") size: Int,
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) status: String?,
        @RequestParam(required = false) paymentSource: String?,
        @RequestParam(defaultValue = "NEWEST") sort: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): PageResponse<ContributionResponse> {
        validatePage(page, size)
        val criteria =
            ContributionListCriteria(
                keyword = q,
                status = enumValueOrNull<ContributionStatus>(status, "contribution status"),
                paymentSource = enumValueOrNull<PaymentSource>(paymentSource, "payment source"),
                sort = enumValue<ContributionListSort>(sort, "contribution sort"),
            )
        val items = service.search(organizationId, campaignId, criteria, currentUser, page * size, size).map { it.toResponse() }
        return PageResponse(items, page, size, service.count(organizationId, campaignId, criteria, currentUser))
    }

    private fun validatePage(
        page: Int,
        size: Int,
    ) {
        if (page < 0 || size !in 1..100) {
            throw ValidationException("Page must be zero or greater and size must be between 1 and 100.")
        }
    }

    private inline fun <reified T : Enum<T>> enumValue(
        value: String,
        label: String,
    ): T =
        runCatching { enumValueOf<T>(value.uppercase()) }
            .getOrElse { throw ValidationException("Unknown $label.") }

    private inline fun <reified T : Enum<T>> enumValueOrNull(
        value: String?,
        label: String,
    ): T? = value?.trim()?.takeIf { it.isNotEmpty() }?.let { enumValue<T>(it, label) }
}
