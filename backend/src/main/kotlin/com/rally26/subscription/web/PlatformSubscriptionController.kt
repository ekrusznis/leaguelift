package com.rally26.subscription.web

import com.rally26.common.web.CurrentUser
import com.rally26.common.web.PageResponse
import com.rally26.subscription.application.OrganizationSubscriptionService
import com.rally26.subscription.domain.PlatformOrganizationSubscription
import com.rally26.subscription.domain.billingRecoveryState
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.UUID

data class PlatformOrganizationSubscriptionResponse(
    val organizationId: UUID,
    val organizationName: String,
    val organizationStatus: String,
    val planCode: String?,
    val planName: String?,
    val amountMinor: Long?,
    val currency: String?,
    val status: String?,
    val recoveryState: String,
    val lastPaymentFailureAt: Instant?,
    val lastPaymentSuccessAt: Instant?,
    val hasStripeCustomer: Boolean,
    val hasStripeSubscription: Boolean,
    val cancelAtPeriodEnd: Boolean,
    val updatedAt: Instant,
)

@RestController
@RequestMapping("/api/v1/platform/admin/subscriptions")
class PlatformSubscriptionController(
    private val subscriptionService: OrganizationSubscriptionService,
) {
    @GetMapping
    fun list(
        @RequestParam(required = false) query: String?,
        @RequestParam(required = false) status: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "25") size: Int,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): PageResponse<PlatformOrganizationSubscriptionResponse> {
        val result = subscriptionService.listForPlatformAdmin(query, status, page, size, currentUser)
        return PageResponse(
            items = result.items.map(::toResponse),
            page = result.page,
            size = result.size,
            totalElements = result.totalElements,
        )
    }

    private fun toResponse(value: PlatformOrganizationSubscription): PlatformOrganizationSubscriptionResponse =
        PlatformOrganizationSubscriptionResponse(
            organizationId = value.organizationId,
            organizationName = value.organizationName,
            organizationStatus = value.organizationStatus,
            planCode = value.planCode,
            planName = value.planName,
            amountMinor = value.amountMinor,
            currency = value.currency,
            status = value.status?.name,
            recoveryState = value.status?.let { billingRecoveryState(it).name } ?: "NOT_STARTED",
            lastPaymentFailureAt = value.lastPaymentFailureAt,
            lastPaymentSuccessAt = value.lastPaymentSuccessAt,
            hasStripeCustomer = value.hasStripeCustomer,
            hasStripeSubscription = value.hasStripeSubscription,
            cancelAtPeriodEnd = value.cancelAtPeriodEnd,
            updatedAt = value.updatedAt,
        )
}
