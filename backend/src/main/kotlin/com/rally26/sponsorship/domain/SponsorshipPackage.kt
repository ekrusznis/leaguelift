package com.rally26.sponsorship.domain

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

enum class SponsorshipPackageStatus { DRAFT, PUBLISHED, ARCHIVED }

/**
 * An org-defined sponsorship tier a public visitor can purchase (DESIGN-DOC.md section
 * 14.1 Phase 6 slice 1). Mirrors `fundraising/domain/Campaign.kt`'s DRAFT/PUBLISHED
 * lifecycle shape, but the sellable unit is a fixed-price package rather than an
 * open-ended goal — closer in spirit to `store/domain/ProductVariant.kt`'s fixed
 * `priceMinor`. Approval workflow, renewal reminders, and exclusivity mechanics beyond
 * "sold out once maxQuantity is reached" are explicitly deferred to a later slice.
 */
data class SponsorshipPackage(
    val id: UUID,
    val organizationId: UUID,
    val name: String,
    val description: String?,
    val priceMinor: Long,
    val currency: String,
    val maxQuantity: Int?,
    val exclusive: Boolean,
    val placementStartDate: LocalDate?,
    val placementEndDate: LocalDate?,
    val status: SponsorshipPackageStatus,
    val createdAt: Instant,
    val updatedAt: Instant,
)

/**
 * `exclusive` caps availability at exactly one sponsor regardless of a separately
 * configured [SponsorshipPackage.maxQuantity] — a package can't simultaneously claim to
 * be the single exclusive holder of a slot and allow multiple purchasers.
 */
fun SponsorshipPackage.effectiveMaxQuantity(): Int? = if (exclusive) 1 else maxQuantity
