package com.rally26.onboarding.owner.domain

import java.time.Instant
import java.util.UUID

enum class OwnerOnboardingStep {
    ORGANIZATION,
    PLAN,
    REVIEW,
    CHECKOUT,
    COMPLETE,
}

data class OwnerOnboarding(
    val id: UUID,
    val ownerUserId: UUID,
    val organizationId: UUID?,
    val currentStep: OwnerOnboardingStep,
    val selectedPlanCode: String?,
    val selectedBillingFrequency: String?,
    val checkoutSessionId: String?,
    val checkoutStartedAt: Instant?,
    val completedAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant,
)
