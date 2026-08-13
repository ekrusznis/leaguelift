package com.rally26.platformadmin.domain

import java.time.Instant
import java.util.UUID

data class PlatformOrganizationListItem(
    val organizationId: UUID,
    val name: String,
    val slug: String,
    val organizationType: String,
    val status: String,
    val contactEmail: String?,
    val primaryOwnerName: String?,
    val primaryOwnerEmail: String?,
    val createdAt: Instant,
    val activeMembers: Long,
    val teams: Long,
    val households: Long,
    val participants: Long,
    val grossVolumeMinor: Long,
)

data class PlatformOrganizationDetail(
    val organizationId: UUID,
    val name: String,
    val slug: String,
    val organizationType: String,
    val status: String,
    val contactEmail: String?,
    val contactPhone: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val ownerNames: List<String>,
    val ownerEmails: List<String>,
    val activeMembers: Long,
    val invitedMembers: Long,
    val teams: Long,
    val tournaments: Long,
    val households: Long,
    val guardians: Long,
    val participants: Long,
    val events: Long,
    val stores: Long,
    val orders: Long,
    val campaigns: Long,
    val contributions: Long,
    val sponsorships: Long,
    val documents: Long,
    val activeEventConnections: Long,
    val grossVolumeMinor: Long,
    val refundedMinor: Long,
    val organizationEarningsMinor: Long,
)

data class PlatformUserOrganizationMembership(
    val organizationId: UUID,
    val organizationName: String,
    val role: String,
)

data class PlatformUserListItem(
    val userId: UUID,
    val email: String,
    val displayName: String,
    val status: String,
    val createdAt: Instant,
    val platformAdmin: Boolean,
    val activeMemberships: Long,
    val organizationMemberships: List<PlatformUserOrganizationMembership>,
)

enum class PlatformSupportAccessStatus { ACTIVE, ENDED, EXPIRED }

data class PlatformSupportAccessListItem(
    val id: UUID,
    val platformAdminUserId: UUID,
    val platformAdminName: String,
    val platformAdminEmail: String,
    val organizationId: UUID,
    val organizationName: String,
    val reason: String,
    val status: PlatformSupportAccessStatus,
    val expiresAt: Instant,
    val endedAt: Instant?,
    val createdAt: Instant,
)

data class PlatformSupportAccess(
    val id: UUID,
    val platformAdminUserId: UUID,
    val organizationId: UUID,
    val organizationName: String,
    val reason: String,
    val status: PlatformSupportAccessStatus,
    val expiresAt: Instant,
    val endedAt: Instant?,
    val createdAt: Instant,
)

/**
 * Swag Shop support triage (DESIGN-DOC.md section 13): a read-only cross-org row so
 * an employee can find which organization owns a stuck product before drilling into
 * that org's existing Swag Shop console section (no separate mutation UI here).
 */
data class PlatformSwagShopProductListItem(
    val productId: UUID,
    val productName: String,
    val status: String,
    val catalogSource: String,
    val storeId: UUID,
    val storeName: String,
    val teamId: UUID?,
    val teamName: String?,
    val organizationId: UUID,
    val organizationName: String,
    val variantCount: Long,
    val hasSwagLogo: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
)

enum class PlatformPaymentType { ORDER, FEE, CONTRIBUTION, SPONSORSHIP }

/** Platform Admin cross-organization payments list (see PlatformAdminConsoleRepository.listPayments) — one normalized row per attempted payment across all four payment-taking domains. [parentId] is null for ORDER (its refund route only needs [id]); it's the campaign id for CONTRIBUTION, the package id for SPONSORSHIP, and the fee assignment id for FEE — whatever the existing per-domain refund/void route needs beyond [id] itself. */
data class PlatformPaymentListItem(
    val type: PlatformPaymentType,
    val id: UUID,
    val organizationId: UUID,
    val organizationName: String,
    val teamId: UUID?,
    val teamName: String?,
    val parentId: UUID?,
    val payerName: String?,
    val payerEmail: String?,
    val amountMinor: Long,
    val currency: String,
    val status: String,
    val createdAt: Instant,
    val confirmedAt: Instant?,
    val closedAt: Instant?,
    val canRefundOrVoid: Boolean,
)
