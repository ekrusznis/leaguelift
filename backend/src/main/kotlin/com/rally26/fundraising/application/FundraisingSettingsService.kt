package com.rally26.fundraising.application

import com.rally26.audit.application.AuditService
import com.rally26.common.web.CurrentUser
import com.rally26.fundraising.domain.FundraisingSettings
import com.rally26.fundraising.persistence.FundraisingSettingsRepository
import com.rally26.membership.application.MembershipService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class FundraisingSettingsService(
    private val repository: FundraisingSettingsRepository,
    private val membershipService: MembershipService,
    private val auditService: AuditService,
) {
    fun get(
        organizationId: UUID,
        currentUser: CurrentUser,
    ): FundraisingSettings {
        membershipService.requireActiveMembership(organizationId, currentUser)
        return getInternal(organizationId)
    }

    /** Internal read used by CampaignService after it has already authorized the actor. */
    fun getInternal(organizationId: UUID): FundraisingSettings =
        repository.findByOrganizationId(organizationId) ?: FundraisingSettings.defaultFor(organizationId)

    @Transactional
    fun update(
        organizationId: UUID,
        requireOwnerApproval: Boolean,
        currentUser: CurrentUser,
    ): FundraisingSettings {
        membershipService.requireOwnerRole(organizationId, currentUser)
        val before = getInternal(organizationId)
        val updated = repository.upsert(organizationId, requireOwnerApproval, currentUser.userId)
        auditService.record(
            actorUserId = currentUser.userId,
            organizationId = organizationId,
            action = "fundraising.settings.updated",
            entityType = "organization_fundraising_settings",
            entityId = organizationId,
            metadataJson =
                """{"requireOwnerApprovalBefore":${before.requireOwnerApproval},"requireOwnerApprovalAfter":$requireOwnerApproval}""",
            summary = "Fundraising approval policy updated",
        )
        return updated
    }
}
