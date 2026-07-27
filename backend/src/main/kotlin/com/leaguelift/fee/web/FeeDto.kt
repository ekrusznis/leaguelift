package com.leaguelift.fee.web

import com.leaguelift.fee.domain.FeeAssignment
import com.leaguelift.fee.domain.FeeAssignmentStatus
import com.leaguelift.fee.domain.FeeTemplate
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class CreateFeeTemplateRequest(
    @field:NotBlank @field:Size(min = 1, max = 120) val name: String,
    @field:Size(max = 500) val description: String? = null,
    @field:NotNull @field:Min(0) val amountMinor: Long,
    @field:Size(min = 3, max = 3) val currency: String = "USD",
)

data class UpdateFeeTemplateRequest(
    @field:Size(min = 1, max = 120) val name: String? = null,
    @field:Size(max = 500) val description: String? = null,
    @field:Min(0) val amountMinor: Long? = null,
)

data class FeeTemplateResponse(
    val id: UUID,
    val organizationId: UUID,
    val name: String,
    val description: String?,
    val amountMinor: Long,
    val currency: String,
    val status: String,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class CreateFeeAssignmentRequest(
    val participantId: UUID? = null,
    val feeTemplateId: UUID? = null,
    @field:NotBlank @field:Size(min = 1, max = 255) val description: String,
    @field:NotNull @field:Min(0) val originalAmountMinor: Long,
    @field:Size(min = 3, max = 3) val currency: String = "USD",
    val dueDate: LocalDate? = null,
)

data class UpdateFeeAssignmentStatusRequest(
    @field:NotNull val status: FeeAssignmentStatus,
)

data class FeeAssignmentResponse(
    val id: UUID,
    val organizationId: UUID,
    val householdId: UUID,
    val participantId: UUID?,
    val feeTemplateId: UUID?,
    val description: String,
    val originalAmountMinor: Long,
    val currency: String,
    val dueDate: LocalDate?,
    val status: String,
    val createdAt: Instant,
    val updatedAt: Instant,
)

fun FeeTemplate.toResponse() = FeeTemplateResponse(
    id, organizationId, name, description, amountMinor, currency, status.name, createdAt, updatedAt,
)

fun FeeAssignment.toResponse() = FeeAssignmentResponse(
    id, organizationId, householdId, participantId, feeTemplateId, description, originalAmountMinor, currency, dueDate, status.name, createdAt, updatedAt,
)
