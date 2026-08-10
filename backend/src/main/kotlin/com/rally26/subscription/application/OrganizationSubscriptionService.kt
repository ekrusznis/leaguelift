package com.rally26.subscription.application

import com.rally26.audit.application.AuditService
import com.rally26.common.error.ConflictException
import com.rally26.common.error.ForbiddenException
import com.rally26.common.error.NotFoundException
import com.rally26.common.error.ServiceUnavailableException
import com.rally26.common.error.ValidationException
import com.rally26.common.web.CurrentUser
import com.rally26.common.web.PageRequest
import com.rally26.common.web.PageResponse
import com.rally26.config.FrontendProperties
import com.rally26.identity.persistence.AppUserRepository
import com.rally26.membership.application.MembershipService
import com.rally26.onboarding.owner.persistence.OwnerOnboardingRepository
import com.rally26.organization.domain.OrganizationStatus
import com.rally26.organization.persistence.OrganizationRepository
import com.rally26.subscription.domain.BillingRecoveryState
import com.rally26.subscription.domain.OrganizationAccessDecision
import com.rally26.subscription.domain.OrganizationSubscription
import com.rally26.subscription.domain.OrganizationSubscriptionStatus
import com.rally26.subscription.domain.PlatformOrganizationSubscription
import com.rally26.subscription.domain.SubscriptionPlan
import com.rally26.subscription.domain.billingRecoveryState
import com.rally26.subscription.domain.organizationAccessDecision
import com.rally26.subscription.domain.stripeSubscriptionStatus
import com.rally26.subscription.infra.StripeSubscriptionBillingClient
import com.rally26.subscription.infra.StripeSubscriptionCheckout
import com.rally26.subscription.persistence.OrganizationSubscriptionRepository
import com.rally26.subscription.persistence.SubscriptionPlanRepository
import com.stripe.exception.StripeException
import com.stripe.model.Invoice
import com.stripe.model.Subscription
import com.stripe.model.checkout.Session
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

data class OrganizationBillingOverview(
    val subscription: OrganizationSubscription,
    val plan: SubscriptionPlan?,
    val recoveryState: BillingRecoveryState,
)

@Service
class OrganizationSubscriptionService(
    private val planRepository: SubscriptionPlanRepository,
    private val subscriptionRepository: OrganizationSubscriptionRepository,
    private val stripeBillingClient: StripeSubscriptionBillingClient,
    private val organizationRepository: OrganizationRepository,
    private val appUserRepository: AppUserRepository,
    private val membershipService: MembershipService,
    private val onboardingRepository: OwnerOnboardingRepository,
    private val frontendProperties: FrontendProperties,
    private val auditService: AuditService,
) {
    private val log = LoggerFactory.getLogger(OrganizationSubscriptionService::class.java)

    fun listPlans(): List<SubscriptionPlan> = planRepository.listActive()

    fun getPlan(code: String): SubscriptionPlan =
        planRepository
            .findByCode(code.trim().uppercase())
            ?.takeIf { it.active }
            ?: throw NotFoundException("SUBSCRIPTION_PLAN_NOT_FOUND", "The subscription plan could not be found.")

    fun getForOrganization(
        organizationId: UUID,
        currentUser: CurrentUser,
    ): OrganizationSubscription? {
        membershipService.requireOwnerRole(organizationId, currentUser)
        return subscriptionRepository.findByOrganizationId(organizationId)
    }

    fun getBillingOverview(
        organizationId: UUID,
        currentUser: CurrentUser,
    ): OrganizationBillingOverview? {
        membershipService.requireOwnerRole(organizationId, currentUser)
        val subscription = subscriptionRepository.findByOrganizationId(organizationId) ?: return null
        return OrganizationBillingOverview(
            subscription = subscription,
            plan = planRepository.findByCode(subscription.planCode),
            recoveryState = billingRecoveryState(subscription.status),
        )
    }

    /**
     * Starts or resumes a Stripe Checkout subscription session. This method deliberately
     * does not activate the organization. Only signed Stripe subscription webhooks do that.
     */
    @Transactional
    fun createCheckout(
        organizationId: UUID,
        planCode: String,
        currentUser: CurrentUser,
    ): StripeSubscriptionCheckout {
        requireOnboardingOwner(organizationId, currentUser)
        val organization =
            organizationRepository.findById(organizationId)
                ?: throw NotFoundException("ORGANIZATION_NOT_FOUND", "The organization could not be found.")
        if (organization.status == OrganizationStatus.ARCHIVED) {
            throw ConflictException("ORGANIZATION_ARCHIVED", "An archived organization cannot start subscription checkout.")
        }

        val normalizedPlanCode = planCode.trim().uppercase()
        val plan =
            planRepository
                .findByCodeForUpdate(normalizedPlanCode)
                ?.takeIf { it.active }
                ?: throw NotFoundException("SUBSCRIPTION_PLAN_NOT_FOUND", "The subscription plan could not be found.")
        if (plan.contactOnly) {
            throw ValidationException("This plan requires contacting Rally26 and cannot be purchased through Checkout.")
        }
        if (plan.billingInterval != "MONTHLY") {
            throw ValidationException("Only monthly subscription billing is currently supported.")
        }

        try {
            val assets = stripeBillingClient.ensurePlanAssets(plan)
            if (plan.stripeProductId != assets.productId || plan.stripePriceId != assets.priceId) {
                planRepository.saveStripeIds(plan.code, assets.productId, assets.priceId)
            }

            var local = subscriptionRepository.findByOrganizationIdForUpdate(organizationId)
            if (local == null) {
                local = subscriptionRepository.insert(organizationId, plan.code)
            } else {
                if (local.status in
                    setOf(
                        OrganizationSubscriptionStatus.ACTIVE,
                        OrganizationSubscriptionStatus.TRIALING,
                        OrganizationSubscriptionStatus.PAST_DUE,
                    )
                ) {
                    throw ConflictException(
                        "SUBSCRIPTION_ALREADY_ACTIVE",
                        "Use billing management for an existing active or recoverable subscription instead of creating a duplicate.",
                    )
                }
                if (local.planCode != plan.code) {
                    subscriptionRepository.updatePlan(local.id, plan.code)
                    local = local.copy(planCode = plan.code)
                }
            }

            local.stripeCheckoutSessionId?.let { existingSessionId ->
                stripeBillingClient.retrieveOpenCheckout(existingSessionId)?.let { return it }
            }

            val customerId =
                local.stripeCustomerId ?: run {
                    val owner =
                        appUserRepository.findById(currentUser.userId)
                            ?: throw NotFoundException("USER_NOT_FOUND", "The owner account could not be found.")
                    val created = stripeBillingClient.createCustomer(organizationId, organization.name, owner.email)
                    subscriptionRepository.saveCustomerId(local.id, created)
                    created
                }
            val generation = local.checkoutGeneration + 1
            val checkout =
                stripeBillingClient.createSubscriptionCheckout(
                    localSubscriptionId = local.id,
                    organizationId = organizationId,
                    customerId = customerId,
                    priceId = assets.priceId,
                    planCode = plan.code,
                    successUrl = "${frontendProperties.baseUrl}/app/onboarding/review?checkout=success&session_id={CHECKOUT_SESSION_ID}",
                    cancelUrl = "${frontendProperties.baseUrl}/app/onboarding/review?checkout=cancelled",
                    generation = generation,
                )
            subscriptionRepository.saveCheckoutSession(local.id, checkout.sessionId, generation)
            auditService.record(
                actorUserId = currentUser.userId,
                organizationId = organizationId,
                action = "organization_subscription.checkout_started",
                entityType = "organization_subscription",
                entityId = local.id,
                metadataJson = "{\"planCode\":\"${plan.code}\"}",
            )
            return checkout
        } catch (e: StripeException) {
            log.warn("Stripe subscription checkout failed for organization {}: {}", organizationId, e.message, e)
            throw ServiceUnavailableException(
                "SUBSCRIPTION_PROVIDER_UNAVAILABLE",
                "Payments provider is not available right now. If this is local/staging, confirm STRIPE_SECRET_KEY is set.",
            )
        }
    }

    @Transactional
    fun createBillingPortal(
        organizationId: UUID,
        currentUser: CurrentUser,
    ): String {
        membershipService.requireOwnerRole(organizationId, currentUser)
        val subscription =
            subscriptionRepository.findByOrganizationId(organizationId)
                ?: throw NotFoundException("SUBSCRIPTION_NOT_FOUND", "No organization subscription exists yet.")
        val customerId =
            subscription.stripeCustomerId
                ?: throw ConflictException("SUBSCRIPTION_CUSTOMER_MISSING", "The Stripe customer has not been created yet.")
        return stripeBillingClient.createBillingPortalSession(
            customerId = customerId,
            returnUrl = "${frontendProperties.baseUrl}/app/organizations/$organizationId/billing",
        )
    }

    /** Checkout completion only records provider identifiers. It never activates the org. */
    @Transactional
    fun handleCheckoutCompleted(session: Session): UUID? {
        val localId = session.metadata?.get("organizationSubscriptionId")?.let(::parseUuid) ?: return null
        val local = subscriptionRepository.findById(localId) ?: return null
        // Stripe does not guarantee delivery order across event types. If a
        // customer.subscription.* event already advanced the authoritative state, a
        // later checkout.session.completed event must only link provider identifiers
        // and must never regress that state back to CHECKOUT_PENDING.
        subscriptionRepository.syncExternalState(
            id = local.id,
            status = local.status,
            customerId = session.customer,
            subscriptionId = session.subscription,
        )
        auditService.record(
            actorUserId = null,
            organizationId = local.organizationId,
            action = "organization_subscription.checkout_completed",
            entityType = "organization_subscription",
            entityId = local.id,
        )
        return local.id
    }

    @Transactional
    fun handleSubscriptionChanged(subscription: Subscription): UUID? {
        val customerId = subscription.customer
        val local =
            subscriptionRepository.findByStripeSubscriptionId(subscription.id)
                ?: customerId?.let(subscriptionRepository::findByStripeCustomerId)
                ?: return null
        val mapped = stripeSubscriptionStatus(subscription.status)
        subscriptionRepository.syncExternalState(
            local.id,
            mapped,
            customerId,
            subscription.id,
            subscription.cancelAtPeriodEnd,
        )
        applyOrganizationAccess(local.organizationId, mapped)
        auditService.record(
            actorUserId = null,
            organizationId = local.organizationId,
            action = "organization_subscription.status_changed",
            entityType = "organization_subscription",
            entityId = local.id,
            metadataJson = "{\"status\":\"${mapped.name}\"}",
        )
        return local.id
    }

    fun listForPlatformAdmin(
        query: String?,
        status: String?,
        page: Int,
        size: Int,
        currentUser: CurrentUser,
    ): PageResponse<PlatformOrganizationSubscription> {
        if (!currentUser.platformAdministrator) {
            throw ForbiddenException(
                code = "PLATFORM_SUBSCRIPTION_ACCESS_DENIED",
                message = "Platform subscription visibility requires Platform Administrator access.",
            )
        }
        val request = PageRequest(page = page, size = size)
        val normalizedQuery = query?.trim().orEmpty()
        val normalizedStatus = status?.trim()?.uppercase().orEmpty()
        val validStatuses = OrganizationSubscriptionStatus.entries.map { it.name }.toSet() + "NONE"
        if (normalizedStatus.isNotEmpty() && normalizedStatus !in validStatuses) {
            throw ValidationException("Unsupported subscription status filter: $normalizedStatus")
        }
        return PageResponse(
            items =
                subscriptionRepository.listForPlatformAdmin(
                    query = normalizedQuery,
                    status = normalizedStatus,
                    offset = request.offset,
                    limit = request.size,
                ),
            page = request.page,
            size = request.size,
            totalElements = subscriptionRepository.countForPlatformAdmin(normalizedQuery, normalizedStatus),
        )
    }

    @Transactional
    fun handleInvoicePaid(invoice: Invoice): UUID? {
        val customerId = invoice.customer ?: return null
        val local = subscriptionRepository.findByStripeCustomerId(customerId) ?: return null
        subscriptionRepository.markPaymentSuccess(local.id)
        auditService.record(
            actorUserId = null,
            organizationId = local.organizationId,
            action = "organization_subscription.payment_succeeded",
            entityType = "organization_subscription",
            entityId = local.id,
        )
        return local.id
    }

    @Transactional
    fun handleInvoicePaymentFailed(invoice: Invoice): UUID? {
        val customerId = invoice.customer ?: return null
        val local = subscriptionRepository.findByStripeCustomerId(customerId) ?: return null
        subscriptionRepository.markPaymentFailure(local.id)
        // The founder has not selected a grace-period policy. Record PAST_DUE and keep
        // an already-active organization accessible so the owner can recover billing;
        // definitive CANCELED/INCOMPLETE subscription states suspend access below.
        auditService.record(
            actorUserId = null,
            organizationId = local.organizationId,
            action = "organization_subscription.payment_failed",
            entityType = "organization_subscription",
            entityId = local.id,
        )
        return local.id
    }

    private fun applyOrganizationAccess(
        organizationId: UUID,
        status: OrganizationSubscriptionStatus,
    ) {
        when (organizationAccessDecision(status)) {
            OrganizationAccessDecision.ACTIVATE -> {
                onboardingRepository.activateOrganization(organizationId)
                onboardingRepository.markCompleteForOrganization(organizationId)
            }
            OrganizationAccessDecision.SUSPEND -> onboardingRepository.suspendActivatedOrganization(organizationId)
            OrganizationAccessDecision.KEEP_CURRENT -> Unit
        }
    }

    private fun requireOnboardingOwner(
        organizationId: UUID,
        currentUser: CurrentUser,
    ) {
        if (currentUser.platformAdministrator) return
        val onboarding = onboardingRepository.findByOwnerUserId(currentUser.userId)
        if (onboarding?.organizationId != organizationId) {
            throw com.rally26.common.error.ForbiddenException(
                code = "OWNER_ONBOARDING_ACCESS_DENIED",
                message = "You do not have access to this organization onboarding flow.",
            )
        }
    }

    private fun parseUuid(value: String): UUID? = runCatching { UUID.fromString(value) }.getOrNull()
}
