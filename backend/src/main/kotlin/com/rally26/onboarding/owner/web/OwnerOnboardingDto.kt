package com.rally26.onboarding.owner.web

import com.rally26.onboarding.owner.application.OwnerOnboardingSnapshot
import com.rally26.organization.domain.Organization
import com.rally26.organization.domain.OrganizationType
import com.rally26.subscription.domain.SubscriptionPlan
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

data class SaveOwnerOrganizationRequest(
    @field:NotBlank @field:Size(max = 160) val name: String,
    @field:NotBlank @field:Size(max = 63) val slug: String,
    val organizationType: OrganizationType,
    @field:NotEmpty val sports: List<String>,
    @field:NotBlank @field:Email val contactEmail: String,
    @field:Size(max = 40) val contactPhone: String? = null,
    @field:NotBlank @field:Size(max = 160) val addressLine1: String,
    @field:Size(max = 160) val addressLine2: String? = null,
    @field:NotBlank @field:Size(max = 100) val addressCity: String,
    @field:NotBlank @field:Size(max = 100) val addressState: String,
    @field:NotBlank @field:Size(max = 30) val addressPostalCode: String,
    @field:NotBlank @field:Size(min = 2, max = 2) val addressCountry: String,
    @field:NotBlank @field:Size(max = 100) val timezone: String,
)

data class SelectOwnerPlanRequest(
    @field:NotBlank val planCode: String,
    @field:NotBlank val billingFrequency: String,
)

data class SubscriptionPlanResponse(
    val code: String,
    val name: String,
    val description: String,
    val amountMinor: Long?,
    val currency: String?,
    val billingInterval: String?,
    val contactOnly: Boolean,
    val requiresCheckout: Boolean,
)

data class OwnerOnboardingResponse(
    val id: UUID,
    val currentStep: String,
    val organization: OnboardingOrganizationResponse?,
    val selectedPlanCode: String?,
    val selectedBillingFrequency: String?,
    val checkoutSessionId: String?,
    val subscriptionStatus: String?,
    val completedAt: Instant?,
)

data class OnboardingOrganizationResponse(
    val id: UUID,
    val name: String,
    val slug: String,
    val organizationType: OrganizationType,
    val status: String,
    val sports: List<String>,
    val contactEmail: String?,
    val contactPhone: String?,
    val addressLine1: String?,
    val addressLine2: String?,
    val addressCity: String?,
    val addressState: String?,
    val addressPostalCode: String?,
    val addressCountry: String?,
    val timezone: String?,
)

data class TimezoneSuggestionResponse(
    val timezone: String?,
)

data class SubscriptionCheckoutResponse(
    val sessionId: String,
    val checkoutUrl: String,
)

fun SubscriptionPlan.toResponse() =
    SubscriptionPlanResponse(code, name, description, amountMinor, currency, billingInterval, contactOnly, requiresCheckout)

fun OwnerOnboardingSnapshot.toResponse(): OwnerOnboardingResponse =
    OwnerOnboardingResponse(
        id = onboarding.id,
        currentStep = onboarding.currentStep.name,
        organization = organization?.toOnboardingResponse(),
        selectedPlanCode = onboarding.selectedPlanCode,
        selectedBillingFrequency = onboarding.selectedBillingFrequency,
        checkoutSessionId = onboarding.checkoutSessionId,
        subscriptionStatus = subscription?.status?.name,
        completedAt = onboarding.completedAt,
    )

private fun Organization.toOnboardingResponse() =
    OnboardingOrganizationResponse(
        id = id,
        name = name,
        slug = slug,
        organizationType = organizationType,
        status = status.name,
        sports = sports,
        contactEmail = contactEmail,
        contactPhone = contactPhone,
        addressLine1 = addressLine1,
        addressLine2 = addressLine2,
        addressCity = addressCity,
        addressState = addressState,
        addressPostalCode = addressPostalCode,
        addressCountry = addressCountry,
        timezone = timezone,
    )
