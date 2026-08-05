package com.rally26.store.web

import com.rally26.store.domain.Store
import com.rally26.store.domain.StoreStatus
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

data class CreateStoreRequest(
    val teamId: UUID? = null,
    @field:NotBlank @field:Size(min = 1, max = 120) val name: String,
    @field:NotBlank @field:Size(min = 1, max = 63) val slug: String,
)

data class UpdateStoreStatusRequest(
    @field:NotNull val status: StoreStatus,
)

data class StoreResponse(
    val id: UUID,
    val organizationId: UUID,
    val teamId: UUID?,
    val name: String,
    val slug: String,
    val status: String,
    val createdAt: Instant,
    val updatedAt: Instant,
)

fun Store.toResponse() = StoreResponse(id, organizationId, teamId, name, slug, status.name, createdAt, updatedAt)

data class PublicProductVariantResponse(
    val id: UUID,
    val label: String,
    val priceMinor: Long,
    val currency: String,
)

data class PublicProductResponse(
    val id: UUID,
    val name: String,
    val description: String?,
    /** Null until the admin has assigned a design and the product is ACTIVE (private/unpublished designs never leak here). */
    val designUrl: String?,
    val variants: List<PublicProductVariantResponse>,
)

data class PublicStoreResponse(
    val id: UUID,
    val name: String,
    val slug: String,
    val products: List<PublicProductResponse>,
)
