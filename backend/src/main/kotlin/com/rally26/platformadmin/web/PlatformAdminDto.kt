package com.rally26.platformadmin.web

import com.rally26.platformadmin.domain.PlatformAthleteListItem
import com.rally26.platformadmin.domain.PlatformCoachListItem
import com.rally26.platformadmin.domain.PlatformOrganizationDetail
import com.rally26.platformadmin.domain.PlatformOrganizationListItem
import com.rally26.platformadmin.domain.PlatformPaymentListItem
import com.rally26.platformadmin.domain.PlatformSupportAccess
import com.rally26.platformadmin.domain.PlatformSupportAccessListItem
import com.rally26.platformadmin.domain.PlatformSwagShopProductListItem
import com.rally26.platformadmin.domain.PlatformUserListItem
import com.rally26.platformadmin.domain.PlatformUserOrganizationMembership
import jakarta.validation.constraints.Size
import java.time.Instant
import java.time.LocalDate
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

data class PlatformSwagShopProductListItemResponse(
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

data class PlatformPaymentListItemResponse(
    val type: String,
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

fun PlatformPaymentListItem.toResponse() =
    PlatformPaymentListItemResponse(
        type.name,
        id,
        organizationId,
        organizationName,
        teamId,
        teamName,
        parentId,
        payerName,
        payerEmail,
        amountMinor,
        currency,
        status,
        createdAt,
        confirmedAt,
        closedAt,
        canRefundOrVoid,
    )

data class PlatformAthleteListItemResponse(
    val participantId: UUID,
    val firstName: String,
    val lastName: String,
    val dateOfBirth: LocalDate?,
    val householdId: UUID,
    val householdName: String,
    val organizationId: UUID,
    val organizationName: String,
    val teamNames: List<String>,
    val eligibilityStatus: String?,
)

fun PlatformAthleteListItem.toResponse() =
    PlatformAthleteListItemResponse(
        participantId,
        firstName,
        lastName,
        dateOfBirth,
        householdId,
        householdName,
        organizationId,
        organizationName,
        teamNames,
        eligibilityStatus?.name,
    )

data class PlatformCoachListItemResponse(
    val roleAssignmentId: UUID,
    val userId: UUID,
    val displayName: String,
    val email: String,
    val role: String,
    val teamId: UUID,
    val teamName: String,
    val organizationId: UUID,
    val organizationName: String,
)

fun PlatformCoachListItem.toResponse() =
    PlatformCoachListItemResponse(
        roleAssignmentId,
        userId,
        displayName,
        email,
        role,
        teamId,
        teamName,
        organizationId,
        organizationName,
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

fun PlatformSwagShopProductListItem.toResponse() =
    PlatformSwagShopProductListItemResponse(
        productId,
        productName,
        status,
        catalogSource,
        storeId,
        storeName,
        teamId,
        teamName,
        organizationId,
        organizationName,
        variantCount,
        hasSwagLogo,
        createdAt,
        updatedAt,
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
