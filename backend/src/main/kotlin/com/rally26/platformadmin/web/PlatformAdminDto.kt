package com.rally26.platformadmin.web

import com.rally26.platformadmin.domain.PlatformOrganizationDetail
import com.rally26.platformadmin.domain.PlatformOrganizationListItem
import com.rally26.platformadmin.domain.PlatformSupportAccess
import com.rally26.platformadmin.domain.PlatformSupportAccessListItem
import com.rally26.platformadmin.domain.PlatformUserListItem
import com.rally26.platformadmin.domain.PlatformUserOrganizationMembership
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

data class PlatformOrganizationListItemResponse(
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

data class PlatformOrganizationDetailResponse(
    val organizationId: UUID,
    val name: String,
    val slug: String,
    val organizationType: String,
    val status: String,
    val contactEmail: String?,
    val primaryOwnerName: String?,
    val primaryOwnerEmail: String?,
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

data class PlatformUserOrganizationMembershipResponse(
    val organizationId: UUID,
    val organizationName: String,
    val role: String,
)

data class PlatformUserListItemResponse(
    val userId: UUID,
    val email: String,
    val displayName: String,
    val status: String,
    val createdAt: Instant,
    val platformAdmin: Boolean,
    val activeMemberships: Long,
    val organizationMemberships: List<PlatformUserOrganizationMembershipResponse>,
)

data class PlatformSupportAccessListItemResponse(
    val id: UUID,
    val platformAdminUserId: UUID,
    val platformAdminName: String,
    val platformAdminEmail: String,
    val organizationId: UUID,
    val organizationName: String,
    val reason: String,
    val status: String,
    val expiresAt: Instant,
    val endedAt: Instant?,
    val createdAt: Instant,
)

data class StartPlatformSupportAccessRequest(
    @field:Size(min = 10, max = 500)
    val reason: String,
)

data class PlatformSupportAccessResponse(
    val id: UUID,
    val organizationId: UUID,
    val organizationName: String,
    val reason: String,
    val status: String,
    val expiresAt: Instant,
    val endedAt: Instant?,
    val createdAt: Instant,
)

fun PlatformOrganizationListItem.toResponse() =
    PlatformOrganizationListItemResponse(
        organizationId,
        name,
        slug,
        organizationType,
        status,
        contactEmail,
        primaryOwnerName,
        primaryOwnerEmail,
        createdAt,
        activeMembers,
        teams,
        households,
        participants,
        grossVolumeMinor,
    )

fun PlatformOrganizationDetail.toResponse() =
    PlatformOrganizationDetailResponse(
        organizationId,
        name,
        slug,
        organizationType,
        status,
        contactEmail,
        ownerNames.firstOrNull(),
        ownerEmails.firstOrNull(),
        contactPhone,
        createdAt,
        updatedAt,
        ownerNames,
        ownerEmails,
        activeMembers,
        invitedMembers,
        teams,
        tournaments,
        households,
        guardians,
        participants,
        events,
        stores,
        orders,
        campaigns,
        contributions,
        sponsorships,
        documents,
        activeEventConnections,
        grossVolumeMinor,
        refundedMinor,
        organizationEarningsMinor,
    )

fun PlatformUserListItem.toResponse() =
    PlatformUserListItemResponse(
        userId,
        email,
        displayName,
        status,
        createdAt,
        platformAdmin,
        activeMemberships,
        organizationMemberships.map { it.toResponse() },
    )

fun PlatformUserOrganizationMembership.toResponse() = PlatformUserOrganizationMembershipResponse(organizationId, organizationName, role)

fun PlatformSupportAccessListItem.toResponse() =
    PlatformSupportAccessListItemResponse(
        id,
        platformAdminUserId,
        platformAdminName,
        platformAdminEmail,
        organizationId,
        organizationName,
        reason,
        status.name,
        expiresAt,
        endedAt,
        createdAt,
    )

fun PlatformSupportAccess.toResponse() =
    PlatformSupportAccessResponse(
        id,
        organizationId,
        organizationName,
        reason,
        status.name,
        expiresAt,
        endedAt,
        createdAt,
    )
