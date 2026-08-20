package com.rally26.onboarding.owner.application

import com.rally26.audit.application.AuditService
import com.rally26.common.error.ConflictException
import com.rally26.common.error.FieldError
import com.rally26.common.error.NotFoundException
import com.rally26.common.error.ValidationException
import com.rally26.common.web.CurrentUser
import com.rally26.membership.application.MembershipService
import com.rally26.onboarding.owner.domain.OwnerOnboarding
import com.rally26.onboarding.owner.persistence.OwnerOnboardingRepository
import com.rally26.organization.domain.Organization
import com.rally26.organization.domain.OrganizationStatus
import com.rally26.organization.domain.OrganizationType
import com.rally26.organization.domain.isValidContactEmail
import com.rally26.organization.domain.isValidSlug
import com.rally26.organization.persistence.OrganizationRepository
import com.rally26.subscription.application.OrganizationSubscriptionService
import com.rally26.subscription.domain.OrganizationSubscription
import com.rally26.subscription.domain.SubscriptionPlan
import com.rally26.subscription.infra.StripeSubscriptionCheckout
import com.rally26.subscription.persistence.OrganizationSubscriptionRepository
import com.rally26.timezone.application.TimeZoneService
import com.rally26.timezone.application.TimezoneSuggestionService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

data class OwnerOnboardingSnapshot(
    val onboarding: OwnerOnboarding,
    val organization: Organization?,
    val subscription: OrganizationSubscription?,
)

@Service
class OwnerOnboardingService(
    private val onboardingRepository: OwnerOnboardingRepository,
    private val organizationRepository: OrganizationRepository,
    private val membershipService: MembershipService,
    private val subscriptionService: OrganizationSubscriptionService,
    private val subscriptionRepository: OrganizationSubscriptionRepository,
    private val timeZoneService: TimeZoneService,
    private val timezoneSuggestionService: TimezoneSuggestionService,
    private val auditService: AuditService,
) {
    fun get(currentUser: CurrentUser): OwnerOnboardingSnapshot {
        val onboarding =
            onboardingRepository.findByOwnerUserId(currentUser.userId)
                ?: throw NotFoundException("OWNER_ONBOARDING_NOT_FOUND", "No owner onboarding record exists for this account.")
        return snapshot(onboarding)
    }

    fun listPlans(): List<SubscriptionPlan> = subscriptionService.listPlans()

    fun suggestTimezone(
        country: String?,
        state: String?,
    ): String? = timezoneSuggestionService.suggest(country, state)

    @Transactional
    fun saveOrganization(
        name: String,
        slug: String,
        organizationType: OrganizationType,
        sports: List<String>,
        contactEmail: String,
        contactPhone: String?,
        addressLine1: String,
        addressLine2: String?,
        addressCity: String,
        addressState: String,
        addressPostalCode: String,
        addressCountry: String,
        timezone: String,
        currentUser: CurrentUser,
    ): OwnerOnboardingSnapshot {
        val onboarding =
            onboardingRepository.findByOwnerUserIdForUpdate(currentUser.userId)
                ?: throw NotFoundException("OWNER_ONBOARDING_NOT_FOUND", "No owner onboarding record exists for this account.")
        val cleanSlug = slug.trim().lowercase()
        if (!isValidSlug(cleanSlug)) {
            throw ValidationException(
                "Slug must be lowercase alphanumeric with optional hyphens.",
                listOf(FieldError("slug", "Invalid slug format.")),
            )
        }
        val normalizedEmail = contactEmail.trim().lowercase()
        if (!isValidContactEmail(normalizedEmail)) {
            throw ValidationException(
                "Contact email is not a valid email address.",
                listOf(FieldError("contactEmail", "Invalid email format.")),
            )
        }
        timeZoneService.requireValid(timezone)
        val normalizedSports = sports.map(String::trim).filter(String::isNotBlank).distinct()
        if (normalizedSports.isEmpty()) {
            throw ValidationException("Choose at least one sport.", listOf(FieldError("sports", "At least one sport is required.")))
        }

        val organization =
            if (onboarding.organizationId == null) {
                organizationRepository.findBySlug(cleanSlug)?.let {
                    throw ConflictException("ORGANIZATION_SLUG_TAKEN", "This organization slug is already in use.")
                }
                val created =
                    onboardingRepository.insertDraftOrganization(
                        name = name.trim(),
                        slug = cleanSlug,
                        organizationType = organizationType,
                        sports = normalizedSports,
                        contactEmail = normalizedEmail,
                        contactPhone = contactPhone?.trim()?.takeIf(String::isNotBlank),
                        addressLine1 = addressLine1.trim(),
                        addressLine2 = addressLine2?.trim()?.takeIf(String::isNotBlank),
                        addressCity = addressCity.trim(),
                        addressState = addressState.trim(),
                        addressPostalCode = addressPostalCode.trim(),
                        addressCountry = addressCountry.trim().uppercase(),
                        timezone = timezone,
                    )
                membershipService.grantOwner(created.id, currentUser.userId)
                onboardingRepository.attachOrganization(onboarding.id, created.id)
                auditService.record(currentUser.userId, created.id, "owner_onboarding.organization_created", "organization", created.id)
                created
            } else {
                val existing =
                    organizationRepository.findById(onboarding.organizationId)
                        ?: throw NotFoundException("ORGANIZATION_NOT_FOUND", "The draft organization could not be found.")
                if (existing.status != OrganizationStatus.DRAFT) {
                    throw ConflictException(
                        "ONBOARDING_ORGANIZATION_LOCKED",
                        "Organization details can no longer be changed through onboarding.",
                    )
                }
                if (existing.slug != cleanSlug) {
                    throw ValidationException("The organization URL cannot be changed after the draft is created.")
                }
                organizationRepository.updateProfile(
                    id = existing.id,
                    name = name.trim(),
                    organizationType = organizationType,
                    sports = normalizedSports,
                    contactEmail = normalizedEmail,
                    contactPhone = contactPhone?.trim()?.takeIf(String::isNotBlank),
                    addressLine1 = addressLine1.trim(),
                    addressLine2 = addressLine2?.trim()?.takeIf(String::isNotBlank),
                    addressCity = addressCity.trim(),
                    addressState = addressState.trim(),
                    addressPostalCode = addressPostalCode.trim(),
                    addressCountry = addressCountry.trim().uppercase(),
                    timezone = timezone,
                )
                auditService.record(currentUser.userId, existing.id, "owner_onboarding.organization_updated", "organization", existing.id)
                organizationRepository.findById(existing.id)!!
            }
        return snapshot(onboardingRepository.findByOwnerUserId(currentUser.userId)!!, organization)
    }

    @Transactional
    fun selectPlan(
        planCode: String,
        billingFrequency: String,
        currentUser: CurrentUser,
    ): OwnerOnboardingSnapshot {
        val onboarding =
            onboardingRepository.findByOwnerUserIdForUpdate(currentUser.userId)
                ?: throw NotFoundException("OWNER_ONBOARDING_NOT_FOUND", "No owner onboarding record exists for this account.")
        val organizationId =
            onboarding.organizationId
                ?: throw ConflictException("ONBOARDING_ORGANIZATION_REQUIRED", "Complete the organization step before selecting a plan.")
        val plan = subscriptionService.getPlan(planCode)
        if (plan.contactOnly) {
            throw ValidationException("Contact Rally26 plans are not selected through self-service checkout.")
        }
        if (!plan.requiresCheckout) {
            throw ValidationException("This plan does not use Stripe Checkout. Use the free-plan activation endpoint instead.")
        }
        if (billingFrequency.trim().uppercase() != "MONTHLY" || plan.billingInterval != "MONTHLY") {
            throw ValidationException("Only monthly billing is available for this plan.")
        }
        onboardingRepository.selectPlan(onboarding.id, plan.code, "MONTHLY")
        auditService.record(
            currentUser.userId,
            organizationId,
            "owner_onboarding.plan_selected",
            "owner_onboarding",
            onboarding.id,
            "{\"planCode\":\"${plan.code}\"}",
        )
        return snapshot(onboardingRepository.findByOwnerUserId(currentUser.userId)!!)
    }

    /** FREE-tier bypass of [selectPlan]/[startCheckout] — collapses plan-selection and activation into one step since there's no Stripe Checkout to wait on. */
    @Transactional
    fun activateFreePlan(currentUser: CurrentUser): OwnerOnboardingSnapshot {
        val onboarding =
            onboardingRepository.findByOwnerUserIdForUpdate(currentUser.userId)
                ?: throw NotFoundException("OWNER_ONBOARDING_NOT_FOUND", "No owner onboarding record exists for this account.")
        val organizationId =
            onboarding.organizationId
                ?: throw ConflictException("ONBOARDING_ORGANIZATION_REQUIRED", "Complete the organization step before selecting a plan.")
        val plan = subscriptionService.getPlan("FREE")
        if (plan.requiresCheckout || plan.contactOnly) {
            throw ValidationException("The Free plan is not available for self-service activation.")
        }
        onboardingRepository.activateFreeOnboarding(onboarding.id, plan.code)
        subscriptionService.activateFreeSubscription(organizationId, currentUser)
        auditService.record(
            currentUser.userId,
            organizationId,
            "owner_onboarding.free_plan_activated",
            "owner_onboarding",
            onboarding.id,
        )
        return snapshot(onboardingRepository.findByOwnerUserId(currentUser.userId)!!)
    }

    @Transactional
    fun startCheckout(currentUser: CurrentUser): StripeSubscriptionCheckout {
        val onboarding =
            onboardingRepository.findByOwnerUserIdForUpdate(currentUser.userId)
                ?: throw NotFoundException("OWNER_ONBOARDING_NOT_FOUND", "No owner onboarding record exists for this account.")
        val organizationId =
            onboarding.organizationId
                ?: throw ConflictException("ONBOARDING_ORGANIZATION_REQUIRED", "Complete the organization step before checkout.")
        val planCode =
            onboarding.selectedPlanCode
                ?: throw ConflictException("ONBOARDING_PLAN_REQUIRED", "Select a plan before checkout.")
        val checkout = subscriptionService.createCheckout(organizationId, planCode, currentUser)
        onboardingRepository.markCheckout(onboarding.id, checkout.sessionId)
        return checkout
    }

    private fun snapshot(
        onboarding: OwnerOnboarding,
        knownOrganization: Organization? = null,
    ): OwnerOnboardingSnapshot =
        OwnerOnboardingSnapshot(
            onboarding = onboarding,
            organization = knownOrganization ?: onboarding.organizationId?.let(organizationRepository::findById),
            subscription = onboarding.organizationId?.let(subscriptionRepository::findByOrganizationId),
        )
}
