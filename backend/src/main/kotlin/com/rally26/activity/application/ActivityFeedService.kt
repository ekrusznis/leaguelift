package com.rally26.activity.application

import com.rally26.activity.web.ActivityFeedItem
import com.rally26.audit.persistence.AuditEventRepository
import com.rally26.common.web.CurrentUser
import com.rally26.membership.persistence.MembershipRepository
import com.rally26.organization.persistence.OrganizationRepository
import org.springframework.stereotype.Service

private const val ACTIVITY_FEED_LIMIT = 50

/**
 * Cross-org activity feed (DESIGN-DOC.md section 13, Phase 7 completion) — distinct
 * from OwnerDashboardService.getRecentActivity, which is already real but scoped to
 * one organization. Reuses the existing audit_event table/AuditService rather than a
 * new event log: every write this app already makes calls AuditService.record, so
 * that table is already the authoritative activity history.
 */
@Service
class ActivityFeedService(
    private val auditEventRepository: AuditEventRepository,
    private val membershipRepository: MembershipRepository,
    private val organizationRepository: OrganizationRepository,
) {
    fun getFeed(currentUser: CurrentUser): List<ActivityFeedItem> {
        val events =
            if (currentUser.platformAdministrator) {
                auditEventRepository.listRecentAcrossAllOrganizations(ACTIVITY_FEED_LIMIT)
            } else {
                val organizationIds = membershipRepository.listActiveForUser(currentUser.userId).map { it.organizationId }.toSet()
                auditEventRepository.listRecentForOrganizations(organizationIds, ACTIVITY_FEED_LIMIT)
            }

        val organizationNames =
            events
                .mapNotNull { it.organizationId }
                .toSet()
                .associateWith { organizationRepository.findById(it)?.name }

        return events.map {
            ActivityFeedItem(
                id = it.id,
                organizationId = it.organizationId,
                organizationName = it.organizationId?.let { orgId -> organizationNames[orgId] },
                action = it.action,
                entityType = it.entityType,
                entityId = it.entityId,
                occurredAt = it.createdAt,
            )
        }
    }
}
