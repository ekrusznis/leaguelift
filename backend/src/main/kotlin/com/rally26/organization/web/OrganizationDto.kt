package com.rally26.organization.web

import com.rally26.common.web.PageResponse
import com.rally26.organization.application.OnboardingProgress
import com.rally26.organization.domain.Organization
import com.rally26.organization.domain.OrganizationType
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

data class CreateOrganizationRequest(
    @field:NotBlank
    @field:Size(min = 2, max = 120)
    val name: String,
    @field:NotBlank
    @field:Pattern(regexp = "^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?$")
    val slug: String,
    @field:NotNull
    val organizationType: OrganizationType,
)

data class UpdateOrganizationRequest(
    @field:Size(min = 2, max = 120)
    val name: String? = null,
    val organizationType: OrganizationType? = null,
    val sports: List<@NotBlank String>? = null,
    @field:Email
    val contactEmail: String? = null,
    @field:Size(max = 40)
    val contactPhone: String? = null,
    @field:Size(max = 200)
    val addressLine1: String? = null,
    @field:Size(max = 200)
    val addressLine2: String? = null,
    @field:Size(max = 100)
    val addressCity: String? = null,
    @field:Size(max = 100)
    val addressState: String? = null,
    @field:Size(max = 20)
    val addressPostalCode: String? = null,
    @field:Size(max = 100)
    val addressCountry: String? = null,
    /** Phase 24 slice 24.5 (ADR-071): submitting this field IS the owner's confirmation act — the frontend never auto-submits a suggested value without an explicit click. */
    val timezone: String? = null,
    /** Phase 32 scaffold: displayed to guardians as Zelle payment instructions. */
    @field:Size(max = 200)
    val zelleHandle: String? = null,
)

data class OrganizationResponse(
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
    val zelleHandle: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

fun Organization.toResponse() =
    OrganizationResponse(
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
        zelleHandle = zelleHandle,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

data class OnboardingProgressResponse(
    val profileComplete: Boolean,
    val hasAdditionalAdministrator: Boolean,
    val payoutsConnected: Boolean,
    val timezoneConfirmed: Boolean,
)

fun OnboardingProgress.toResponse() =
    OnboardingProgressResponse(
        profileComplete = profileComplete,
        hasAdditionalAdministrator = hasAdditionalAdministrator,
        payoutsConnected = payoutsConnected,
        timezoneConfirmed = timezoneConfirmed,
    )

data class TimezoneSuggestionResponse(
    val timezone: String?,
)

typealias OrganizationPageResponse = PageResponse<OrganizationResponse>
