package com.rally26.subscription.web

import com.rally26.common.web.CurrentUser
import com.rally26.subscription.application.OrganizationSubscriptionService
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

data class OrganizationSubscriptionResponse(
    val id: UUID,
    val organizationId: UUID,
    val planCode: String,
    val status: String,
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
        subscriptionService.getForOrganization(organizationId, currentUser)?.let {
            OrganizationSubscriptionResponse(it.id, it.organizationId, it.planCode, it.status.name)
        }

    @PostMapping("/portal")
    fun portal(
        @PathVariable organizationId: UUID,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): BillingPortalResponse = BillingPortalResponse(subscriptionService.createBillingPortal(organizationId, currentUser))
}
