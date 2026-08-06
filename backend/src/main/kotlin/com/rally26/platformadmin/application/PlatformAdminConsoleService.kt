package com.rally26.platformadmin.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.rally26.audit.application.AuditService
import com.rally26.authorization.application.AuthorizationService
import com.rally26.authorization.domain.Capabilities
import com.rally26.common.error.ForbiddenException
import com.rally26.common.error.NotFoundException
import com.rally26.common.error.ValidationException
import com.rally26.common.web.CurrentUser
import com.rally26.common.web.PageRequest
import com.rally26.common.web.PageResponse
import com.rally26.platformadmin.domain.PlatformOrganizationDetail
import com.rally26.platformadmin.domain.PlatformOrganizationListItem
import com.rally26.platformadmin.domain.PlatformSupportAccess
import com.rally26.platformadmin.domain.PlatformSupportAccessListItem
import com.rally26.platformadmin.domain.PlatformSupportAccessStatus
import com.rally26.platformadmin.domain.PlatformSwagShopProductListItem
import com.rally26.platformadmin.domain.PlatformUserListItem
import com.rally26.platformadmin.persistence.PlatformAdminConsoleRepository
import com.rally26.platformadmin.persistence.PlatformSupportAccessRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID

private val ORGANIZATION_STATUSES = setOf("ACTIVE", "SUSPENDED", "ARCHIVED")
private val USER_STATUSES = setOf("ACTIVE", "SUSPENDED")
private val SUPPORT_ACCESS_STATUSES = setOf("ACTIVE", "ENDED", "EXPIRED")
private val SWAG_SHOP_PRODUCT_STATUSES = setOf("DRAFT", "ACTIVE", "ARCHIVED")
private val SUPPORT_ACCESS_DURATION: Duration = Duration.ofHours(2)

/**
 * Rally26 employee console. This is deliberately separate from an organization
 * ADMINISTRATOR membership: every entry point requires a PLATFORM_ADMIN capability.
 * Cross-organization work happens under a reasoned, time-bounded support-access
 * session while the employee remains the authenticated actor (not impersonation).
 */
@Service
class PlatformAdminConsoleService(
    private val authorizationService: AuthorizationService,
    private val consoleRepository: PlatformAdminConsoleRepository,
    private val supportAccessRepository: PlatformSupportAccessRepository,
    private val auditService: AuditService,
    private val objectMapper: ObjectMapper,
    private val clock: Clock,
) {
    fun listOrganizations(
        currentUser: CurrentUser,
        query: String?,
        status: String?,
        pageRequest: PageRequest,
    ): PageResponse<PlatformOrganizationListItem> {
        authorizationService.requirePlatformCapability(currentUser, Capabilities.PLATFORM_ORG_VIEW)
        val normalizedStatus = normalizeStatus(status, ORGANIZATION_STATUSES, "organization status")
        return PageResponse(
            items = consoleRepository.listOrganizations(query?.trim(), normalizedStatus, pageRequest),
            page = pageRequest.page,
            size = pageRequest.size,
            totalElements = consoleRepository.countOrganizations(query?.trim(), normalizedStatus),
        )
    }

    fun getOrganization(
        currentUser: CurrentUser,
        organizationId: UUID,
    ): PlatformOrganizationDetail {
        authorizationService.requirePlatformCapability(currentUser, Capabilities.PLATFORM_ORG_VIEW)
        return consoleRepository.findOrganization(organizationId)
            ?: throw NotFoundException("ORGANIZATION_NOT_FOUND", "Organization not found.")
    }

    fun listUsers(
        currentUser: CurrentUser,
        query: String?,
        status: String?,
        pageRequest: PageRequest,
    ): PageResponse<PlatformUserListItem> {
        authorizationService.requirePlatformCapability(currentUser, Capabilities.PLATFORM_USER_VIEW)
        val normalizedStatus = normalizeStatus(status, USER_STATUSES, "user status")
        return PageResponse(
            items = consoleRepository.listUsers(query?.trim(), normalizedStatus, pageRequest),
            page = pageRequest.page,
            size = pageRequest.size,
            totalElements = consoleRepository.countUsers(query?.trim(), normalizedStatus),
        )
    }

    fun listSupportAccess(
        currentUser: CurrentUser,
        status: String?,
        pageRequest: PageRequest,
    ): PageResponse<PlatformSupportAccessListItem> {
        authorizationService.requirePlatformCapability(currentUser, Capabilities.PLATFORM_SUPPORT_ACCESS)
        val normalizedStatus = normalizeStatus(status, SUPPORT_ACCESS_STATUSES, "support access status")
        return PageResponse(
            items = consoleRepository.listSupportAccess(normalizedStatus, pageRequest),
            page = pageRequest.page,
            size = pageRequest.size,
            totalElements = consoleRepository.countSupportAccess(normalizedStatus),
        )
    }

    fun listSwagShopProducts(
        currentUser: CurrentUser,
        query: String?,
        status: String?,
        organizationId: UUID?,
        pageRequest: PageRequest,
    ): PageResponse<PlatformSwagShopProductListItem> {
        authorizationService.requirePlatformCapability(currentUser, Capabilities.PLATFORM_SWAG_SHOP_VIEW)
        val normalizedStatus = normalizeStatus(status, SWAG_SHOP_PRODUCT_STATUSES, "product status")
        return PageResponse(
            items = consoleRepository.listSwagShopProducts(query?.trim(), normalizedStatus, organizationId, pageRequest),
            page = pageRequest.page,
            size = pageRequest.size,
            totalElements = consoleRepository.countSwagShopProducts(query?.trim(), normalizedStatus, organizationId),
        )
    }

    @Transactional
    fun startSupportAccess(
        currentUser: CurrentUser,
        organizationId: UUID,
        reason: String,
    ): PlatformSupportAccess {
        authorizationService.requirePlatformCapability(currentUser, Capabilities.PLATFORM_SUPPORT_ACCESS)
        val normalizedReason = reason.trim()
        if (normalizedReason.length !in 10..500) {
            throw ValidationException("Support access requires a reason between 10 and 500 characters.")
        }
        val organization =
            consoleRepository.findOrganization(organizationId)
                ?: throw NotFoundException("ORGANIZATION_NOT_FOUND", "Organization not found.")
        val now = Instant.now(clock)
        val prior = supportAccessRepository.findActiveForAdmin(currentUser.userId)
        if (prior != null && supportAccessRepository.end(prior.id, currentUser.userId, now) == 1) {
            recordAccessEvent(currentUser, prior, "platform.support_access.ended", mapOf("reason" to "replaced_by_new_session"))
        }
        val access =
            supportAccessRepository.create(
                currentUser.userId,
                organizationId,
                normalizedReason,
                now,
                now.plus(SUPPORT_ACCESS_DURATION),
            )
        recordAccessEvent(
            currentUser,
            access,
            "platform.support_access.started",
            mapOf(
                "reason" to normalizedReason,
                "organizationName" to organization.name,
                "expiresAt" to access.expiresAt.toString(),
            ),
        )
        return access
    }

    @Transactional
    fun currentSupportAccess(currentUser: CurrentUser): PlatformSupportAccess? {
        authorizationService.requirePlatformCapability(currentUser, Capabilities.PLATFORM_SUPPORT_ACCESS)
        val access = supportAccessRepository.findActiveForAdmin(currentUser.userId) ?: return null
        return validateExpiration(currentUser, access)
    }

    @Transactional
    fun endSupportAccess(
        currentUser: CurrentUser,
        accessId: UUID,
    ) {
        authorizationService.requirePlatformCapability(currentUser, Capabilities.PLATFORM_SUPPORT_ACCESS)
        val access =
            supportAccessRepository.findById(accessId)
                ?: throw NotFoundException("SUPPORT_ACCESS_NOT_FOUND", "Support access session not found.")
        if (access.platformAdminUserId != currentUser.userId) {
            throw ForbiddenException("SUPPORT_ACCESS_FORBIDDEN", "This support access session belongs to another administrator.")
        }
        if (access.status != PlatformSupportAccessStatus.ACTIVE) return
        val now = Instant.now(clock)
        if (supportAccessRepository.end(access.id, currentUser.userId, now) == 1) {
            recordAccessEvent(currentUser, access, "platform.support_access.ended", mapOf("endedAt" to now.toString()))
        }
    }

    /** Called by the organization-request interceptor for every Platform Admin org API call. */
    @Transactional
    fun requireActiveSupportAccess(
        currentUser: CurrentUser,
        accessId: UUID,
        organizationId: UUID,
    ): PlatformSupportAccess {
        authorizationService.requirePlatformCapability(currentUser, Capabilities.PLATFORM_SUPPORT_ACCESS)
        val access =
            supportAccessRepository.findById(accessId)
                ?: throw ForbiddenException("SUPPORT_ACCESS_REQUIRED", "Start a support access session before opening organization data.")
        if (access.platformAdminUserId != currentUser.userId || access.organizationId != organizationId) {
            throw ForbiddenException("SUPPORT_ACCESS_SCOPE_MISMATCH", "The support access session does not match this organization.")
        }
        if (access.status != PlatformSupportAccessStatus.ACTIVE) {
            throw ForbiddenException("SUPPORT_ACCESS_INACTIVE", "The support access session is no longer active.")
        }
        return validateExpiration(currentUser, access)
            ?: throw ForbiddenException("SUPPORT_ACCESS_EXPIRED", "The support access session has expired.")
    }

    private fun validateExpiration(
        currentUser: CurrentUser,
        access: PlatformSupportAccess,
    ): PlatformSupportAccess? {
        val now = Instant.now(clock)
        if (access.expiresAt.isAfter(now)) return access
        if (supportAccessRepository.expire(access.id, now) == 1) {
            recordAccessEvent(currentUser, access, "platform.support_access.expired", mapOf("expiredAt" to now.toString()))
        }
        return null
    }

    private fun recordAccessEvent(
        currentUser: CurrentUser,
        access: PlatformSupportAccess,
        action: String,
        metadata: Map<String, String>,
    ) {
        auditService.record(
            actorUserId = currentUser.userId,
            organizationId = access.organizationId,
            action = action,
            entityType = "PLATFORM_SUPPORT_ACCESS",
            entityId = access.id,
            metadataJson = objectMapper.writeValueAsString(metadata + mapOf("platformAdminUserId" to currentUser.userId.toString())),
        )
    }

    private fun normalizeStatus(
        value: String?,
        allowed: Set<String>,
        label: String,
    ): String? {
        if (value.isNullOrBlank()) return null
        val normalized = value.trim().uppercase()
        if (normalized !in allowed) throw ValidationException("Unknown $label.")
        return normalized
    }
}
