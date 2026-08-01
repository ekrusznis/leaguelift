package com.leaguelift.actioncenter.application

import com.leaguelift.actioncenter.domain.ActionCenter
import com.leaguelift.actioncenter.domain.ActionCenterItem
import com.leaguelift.actioncenter.domain.ActionCenterPriority
import com.leaguelift.actioncenter.domain.ActionCenterType
import com.leaguelift.actioncenter.persistence.ActionCenterRepository
import com.leaguelift.authorization.application.AuthorizationService
import com.leaguelift.authorization.domain.Capabilities
import com.leaguelift.authorization.domain.ContextType
import com.leaguelift.common.web.CurrentUser
import org.springframework.stereotype.Service
import java.text.NumberFormat
import java.time.Clock
import java.time.LocalDate
import java.util.*

private const val ACTION_LIMIT = 50

@Service
class ActionCenterService(
    private val repository: ActionCenterRepository,
    private val authorizationService: AuthorizationService,
    private val clock: Clock,
) {
    fun get(currentUser: CurrentUser): ActionCenter {
        val contexts = authorizationService.listContexts(currentUser)
        val managerOrganizationIds = contexts
            .filter { it.contextType == ContextType.ORGANIZATION && Capabilities.ORG_MANAGE in it.capabilities }
            .mapNotNull { it.organizationId }.toSet()
        val items = mutableListOf<ActionCenterItem>()

        managerOrganizationIds.forEach { organizationId ->
            val corrections = repository.countPendingCorrections(organizationId)
            if (corrections > 0) items += aggregate(
                "org:$organizationId:corrections", ActionCenterType.PROFILE_CORRECTION_REVIEW, ActionCenterPriority.HIGH,
                "Review profile corrections", "$corrections pending request${plural(corrections)} need an owner or administrator decision.",
                "/app/organizations/$organizationId/corrections", organizationId,
            )
            val overdueFees = repository.countOverdueFees(organizationId)
            if (overdueFees > 0) items += aggregate(
                "org:$organizationId:overdue-fees", ActionCenterType.OVERDUE_FEES, ActionCenterPriority.HIGH,
                "Follow up on overdue fees", "$overdueFees fee assignment${plural(overdueFees)} are past due.",
                "/app/organizations/$organizationId/fees", organizationId,
            )
            val events = repository.countReviewableEvents(organizationId)
            if (events > 0) items += aggregate(
                "org:$organizationId:event-review", ActionCenterType.EVENT_REVIEW, ActionCenterPriority.NORMAL,
                "Review unpublished events", "$events draft or tentative event${plural(events)} still need review.",
                "/app/organizations/$organizationId/events", organizationId,
            )
        }

        contexts.filter { it.contextType == ContextType.TEAM && it.organizationId !in managerOrganizationIds }
            .filter { Capabilities.EVENT_PUBLISH in it.capabilities || Capabilities.EVENT_UPDATE in it.capabilities }
            .forEach { context ->
                val organizationId = context.organizationId ?: return@forEach
                val teamId = context.resourceId ?: return@forEach
                val count = repository.countReviewableEvents(organizationId, teamId = teamId)
                if (count > 0) items += aggregate(
                    "team:$teamId:event-review", ActionCenterType.EVENT_REVIEW, ActionCenterPriority.NORMAL,
                    "Review ${context.label} events", "$count draft or tentative event${plural(count)} still need review.",
                    "/app/organizations/$organizationId/teams/$teamId/events", organizationId, "TEAM", teamId,
                )
            }

        contexts.filter { it.contextType == ContextType.TOURNAMENT && it.organizationId !in managerOrganizationIds }
            .filter { Capabilities.EVENT_PUBLISH in it.capabilities || Capabilities.EVENT_UPDATE in it.capabilities }
            .forEach { context ->
                val organizationId = context.organizationId ?: return@forEach
                val tournamentId = context.resourceId ?: return@forEach
                val count = repository.countReviewableEvents(organizationId, tournamentId = tournamentId)
                if (count > 0) items += aggregate(
                    "tournament:$tournamentId:event-review", ActionCenterType.EVENT_REVIEW, ActionCenterPriority.NORMAL,
                    "Review ${context.label} events", "$count draft or tentative event${plural(count)} still need review.",
                    "/app/organizations/$organizationId/tournaments/$tournamentId/events", organizationId, "TOURNAMENT", tournamentId,
                )
            }

        repository.listGuardianFeeActions(currentUser.userId, ACTION_LIMIT).forEach { fee ->
            val overdue = fee.dueDate.isBefore(LocalDate.now(clock))
            val amount = formatMoney(fee.balanceMinor, fee.currency)
            items += ActionCenterItem(
                id = "fee:${fee.id}", type = ActionCenterType.FEE_PAYMENT,
                priority = if (overdue) ActionCenterPriority.URGENT else ActionCenterPriority.HIGH,
                title = if (overdue) "Overdue payment" else "Payment due soon",
                description = "$amount remaining for ${fee.description}; due ${fee.dueDate}.",
                actionPath = "/app/organizations/${fee.organizationId}/households/${fee.householdId}/fees",
                organizationId = fee.organizationId, contextType = "HOUSEHOLD", contextId = fee.householdId,
                dueAt = fee.dueAt, createdAt = fee.createdAt,
            )
        }

        repository.listGuardianDocumentActions(currentUser.userId, ACTION_LIMIT).forEach { document ->
            items += ActionCenterItem(
                id = "document:${document.assignmentId}", type = ActionCenterType.DOCUMENT_ACKNOWLEDGMENT,
                priority = ActionCenterPriority.HIGH, title = "Document acknowledgment needed",
                description = "Review and acknowledge ${document.title}.",
                actionPath = "/app/organizations/${document.organizationId}/households/${document.householdId}/documents",
                organizationId = document.organizationId, contextType = "HOUSEHOLD", contextId = document.householdId,
                createdAt = document.createdAt,
            )
        }

        val seenRsvp = mutableSetOf<String>()
        (repository.listGuardianRsvpActions(currentUser.userId, ACTION_LIMIT) + repository.listAthleteRsvpActions(currentUser.userId, ACTION_LIMIT))
            .forEach { rsvp ->
                val id = "rsvp:${rsvp.eventId}:${rsvp.participantId}"
                if (!seenRsvp.add(id)) return@forEach
                items += ActionCenterItem(
                    id = id, type = ActionCenterType.EVENT_RSVP, priority = ActionCenterPriority.NORMAL,
                    title = "RSVP for ${rsvp.eventTitle}",
                    description = "${rsvp.participantName} has not responded for this upcoming event.",
                    actionPath = "/app/organizations/${rsvp.organizationId}/events/${rsvp.eventId}",
                    organizationId = rsvp.organizationId, contextType = "PARTICIPANT", contextId = rsvp.participantId,
                    dueAt = rsvp.startAt, createdAt = rsvp.createdAt,
                )
            }

        repository.listSupportCaseActions(currentUser.userId, ACTION_LIMIT).forEach { supportCase ->
            items += ActionCenterItem(
                id = "support:${supportCase.id}", type = ActionCenterType.SUPPORT_CASE_RESPONSE,
                priority = ActionCenterPriority.HIGH, title = "Support is waiting for you",
                description = supportCase.subject, actionPath = "/app/help/support",
                createdAt = supportCase.createdAt,
            )
        }

        if (currentUser.platformAdministrator) {
            val supportCount = repository.countOpenPrioritySupportCases()
            if (supportCount > 0) items += aggregate(
                "platform:support-queue", ActionCenterType.PLATFORM_SUPPORT_QUEUE, ActionCenterPriority.URGENT,
                "Triage priority support cases", "$supportCount high-priority open case${plural(supportCount)} need attention.",
                "/app/platform/support-cases",
            )
            val failures = repository.countFailedDeliveries()
            if (failures > 0) items += aggregate(
                "platform:delivery-failures", ActionCenterType.PLATFORM_DELIVERY_FAILURES, ActionCenterPriority.URGENT,
                "Review delivery failures", "$failures failed or dead-letter delivery item${plural(failures)} need operational review.",
                "/app/platform/operations",
            )
        }

        val sorted = items.distinctBy { it.id }.sortedWith(
            compareBy<ActionCenterItem> { priorityRank(it.priority) }
                .thenBy { it.dueAt ?: java.time.Instant.MAX }
                .thenBy { it.title },
        )
        return ActionCenter(sorted, sorted.size, sorted.count { it.priority in setOf(ActionCenterPriority.HIGH, ActionCenterPriority.URGENT) })
    }

    private fun aggregate(
        id: String, type: ActionCenterType, priority: ActionCenterPriority, title: String, description: String,
        path: String, organizationId: UUID? = null, contextType: String? = null, contextId: UUID? = null,
    ) = ActionCenterItem(id, type, priority, title, description, path, organizationId, contextType, contextId)

    private fun priorityRank(priority: ActionCenterPriority) = when (priority) {
        ActionCenterPriority.URGENT -> 0
        ActionCenterPriority.HIGH -> 1
        ActionCenterPriority.NORMAL -> 2
        ActionCenterPriority.INFO -> 3
    }

    private fun formatMoney(amountMinor: Long, currency: String): String = NumberFormat.getCurrencyInstance(Locale.US).apply {
        this.currency = java.util.Currency.getInstance(currency)
    }.format(java.math.BigDecimal.valueOf(amountMinor, 2))

    private fun plural(count: Long) = if (count == 1L) "" else "s"
}
