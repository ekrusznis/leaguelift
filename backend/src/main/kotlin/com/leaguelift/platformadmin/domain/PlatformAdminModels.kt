package com.leaguelift.platformadmin.domain

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
