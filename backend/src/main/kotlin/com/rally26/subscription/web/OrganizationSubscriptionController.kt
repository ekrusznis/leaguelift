package com.rally26.subscription.web

import com.rally26.common.web.CurrentUser
import com.rally26.subscription.application.OrganizationSubscriptionService
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.UUID

data class OrganizationSubscriptionResponse(
    val id: UUID,
    val organizationId: UUID,
    val planCode: String,
    val planName: String?,
    val amountMinor: Long?,
    val currency: String?,
    val billingInterval: String?,
    val status: String,
    val recoveryState: String,
    val lastPaymentFailureAt: Instant?,
    val lastPaymentSuccessAt: Instant?,
    val billingPortalAvailable: Boolean,
    val cancelAtPeriodEnd: Boolean,
)

data class BillingPortalResponse(
    val url: String,
)

@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/subscription")
class OrganizationSubscriptionController(
    private val subscriptionService: OrganizationSubscriptionService,
) {
    @GetMapping
    fun get(
        @PathVariable organizationId: UUID,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): OrganizationSubscriptionResponse? =
        subscriptionService.getBillingOverview(organizationId, currentUser)?.let { overview ->
            val subscription = overview.subscription
            OrganizationSubscriptionResponse(
                id = subscription.id,
                organizationId = subscription.organizationId,
                planCode = subscription.planCode,
                planName = overview.plan?.name,
                amountMinor = overview.plan?.amountMinor,
                currency = overview.plan?.currency,
                billingInterval = overview.plan?.billingInterval,
                status = subscription.status.name,
                recoveryState = overview.recoveryState.name,
                lastPaymentFailureAt = subscription.lastPaymentFailureAt,
                lastPaymentSuccessAt = subscription.lastPaymentSuccessAt,
                billingPortalAvailable = subscription.stripeCustomerId != null,
                cancelAtPeriodEnd = subscription.cancelAtPeriodEnd,
            )
        }

    @PostMapping("/portal")
    fun portal(
        @PathVariable organizationId: UUID,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): BillingPortalResponse = BillingPortalResponse(subscriptionService.createBillingPortal(organizationId, currentUser))
}
