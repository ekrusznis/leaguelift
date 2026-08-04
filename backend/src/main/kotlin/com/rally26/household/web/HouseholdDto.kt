package com.rally26.household.web

import com.rally26.common.web.PageResponse
import com.rally26.household.domain.Household
import com.rally26.household.domain.HouseholdAdult
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

data class CreateHouseholdRequest(
    @field:NotBlank @field:Size(min = 1, max = 120) val displayName: String,
    @field:Email val contactEmail: String? = null,
    @field:Size(max = 40) val contactPhone: String? = null,
    @field:Size(max = 1000) val notes: String? = null,
)

data class UpdateHouseholdRequest(
    @field:Size(min = 1, max = 120) val displayName: String? = null,
    @field:Email val contactEmail: String? = null,
    @field:Size(max = 40) val contactPhone: String? = null,
    @field:Size(max = 1000) val notes: String? = null,
    /** Phase 8 slice 2 (ADR-023) — opt out of transactional reminder emails (e.g. fee-payment reminders). */
    val emailRemindersOptOut: Boolean? = null,
    /** Phase 8 slice 3 (ADR-024) — opt IN to one-way SMS reminders via contactPhone. */
    val smsRemindersOptIn: Boolean? = null,
)

data class AddAdultRequest(
    @field:NotBlank @field:Size(min = 1, max = 60) val firstName: String,
    @field:NotBlank @field:Size(min = 1, max = 60) val lastName: String,
    @field:Email val email: String? = null,
    @field:Size(max = 40) val phone: String? = null,
    @field:Size(max = 60) val relationship: String? = null,
    val isPrimary: Boolean = false,
)

data class HouseholdResponse(
    val id: UUID,
    val organizationId: UUID,
    val displayName: String,
    val contactEmail: String?,
    val contactPhone: String?,
    val notes: String?,
    val emailRemindersOptOut: Boolean,
    val smsRemindersOptIn: Boolean,
    val status: String,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class HouseholdAdultResponse(
    val id: UUID,
    val householdId: UUID,
    val firstName: String,
    val lastName: String,
    val email: String?,
    val phone: String?,
    val relationship: String?,
    val isPrimary: Boolean,
    val status: String,
    val createdAt: Instant,
)

fun Household.toResponse() = HouseholdResponse(
    id, organizationId, displayName, contactEmail, contactPhone, notes, emailRemindersOptOut, smsRemindersOptIn, status.name, createdAt, updatedAt,
)

fun HouseholdAdult.toResponse() = HouseholdAdultResponse(
    id, householdId, firstName, lastName, email, phone, relationship, isPrimary, status.name, createdAt,
)

typealias HouseholdPageResponse = PageResponse<HouseholdResponse>
