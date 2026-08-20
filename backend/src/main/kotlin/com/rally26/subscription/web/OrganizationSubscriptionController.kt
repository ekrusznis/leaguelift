package com.rally26.subscription.web

import com.rally26.common.web.CurrentUser
import com.rally26.subscription.application.OrganizationPlanChangeService
import com.rally26.subscription.application.OrganizationSubscriptionService
import com.rally26.subscription.application.PlanChangeResult
import com.rally26.subscription.domain.SubscriptionPlan
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
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
    val downgradeToPlanCode: String?,
    val currentPeriodEnd: Instant?,
)

data class BillingPortalResponse(
    val url: String,
)

data class SubscriptionPlanOptionResponse(
    val code: String,
    val name: String,
    val description: String,
    val amountMinor: Long?,
    val currency: String?,
    val billingInterval: String?,
)

data class PlanChangeViolationResponse(
    val code: String,
    val message: String,
    val actionLink: String?,
)

data class PlanChangePreviewResponse(
    val currentPlanCode: String,
    val targetPlanCode: String,
    val direction: String,
    val violations: List<PlanChangeViolationResponse>,
)

data class PlanChangeRequest(
    @field:NotBlank val targetPlanCode: String,
)

data class PlanChangeResultResponse(
    val outcome: String,
    val violations: List<PlanChangeViolationResponse>? = null,
    val checkoutUrl: String? = null,
    val effectiveAt: Instant? = null,
)

private fun SubscriptionPlan.toOptionResponse() =
    SubscriptionPlanOptionResponse(code, name, description, amountMinor, currency, billingInterval)

@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/subscription")
class OrganizationSubscriptionController(
    private val subscriptionService: OrganizationSubscriptionService,
    private val planChangeService: OrganizationPlanChangeService,
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
                downgradeToPlanCode = subscription.downgradeToPlanCode,
                currentPeriodEnd = subscription.currentPeriodEnd,
            )
        }

    @PostMapping("/portal")
    fun portal(
        @PathVariable organizationId: UUID,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): BillingPortalResponse = BillingPortalResponse(subscriptionService.createBillingPortal(organizationId, currentUser))

    /** Self-serve plans only (never League/CONTACT_RALLY26 — sales-only, never an in-app plan-change target). */
    @GetMapping("/plans")
    fun plans(
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): List<SubscriptionPlanOptionResponse> = subscriptionService.listPlans().filter { !it.contactOnly }.map { it.toOptionResponse() }

    @PostMapping("/plan-change/preview")
    fun previewPlanChange(
        @PathVariable organizationId: UUID,
        @Valid @RequestBody request: PlanChangeRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): PlanChangePreviewResponse {
        val preview = planChangeService.previewPlanChange(organizationId, request.targetPlanCode, currentUser)
        return PlanChangePreviewResponse(
            currentPlanCode = preview.currentPlanCode,
            targetPlanCode = preview.targetPlanCode,
            direction = preview.direction.name,
            violations = preview.violations.map { PlanChangeViolationResponse(it.code, it.message, it.actionLink) },
        )
    }

    @PostMapping("/plan-change/apply")
    fun applyPlanChange(
        @PathVariable organizationId: UUID,
        @Valid @RequestBody request: PlanChangeRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): PlanChangeResultResponse =
        when (val result = planChangeService.applyPlanChange(organizationId, request.targetPlanCode, currentUser)) {
            is PlanChangeResult.Blocked ->
                PlanChangeResultResponse(
                    outcome = "BLOCKED",
                    violations = result.violations.map { PlanChangeViolationResponse(it.code, it.message, it.actionLink) },
                )
            is PlanChangeResult.CheckoutRequired ->
                PlanChangeResultResponse(
                    outcome = "CHECKOUT_REQUIRED",
                    checkoutUrl = result.checkout.checkoutUrl,
                )
            is PlanChangeResult.AppliedImmediately -> PlanChangeResultResponse(outcome = "APPLIED")
            is PlanChangeResult.ScheduledDowngrade ->
                PlanChangeResultResponse(
                    outcome = "SCHEDULED_DOWNGRADE",
                    effectiveAt = result.effectiveAt,
                )
        }
}
