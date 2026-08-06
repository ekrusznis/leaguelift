package com.rally26.store.application

import com.rally26.audit.application.AuditService
import com.rally26.common.error.ValidationException
import com.rally26.common.web.CurrentUser
import com.rally26.membership.application.MembershipService
import com.rally26.store.domain.MarkupType
import com.rally26.store.domain.OrganizationMarkupRule
import com.rally26.store.persistence.OrganizationMarkupRuleRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Swag Shop markup rule engine (Phase 23, DESIGN-DOC.md section 13/14.1,
 * resolving section 19.3 #6). Computes a variant's sale price from Printify's
 * real per-variant cost using an org-configured rule — per-blueprint override,
 * else org default, else a documented system starter value — rather than an
 * admin manually typing a price with no markup logic at all (the prior state).
 */
@Service
class MarkupRuleService(
    private val markupRuleRepository: OrganizationMarkupRuleRepository,
    private val membershipService: MembershipService,
    private val auditService: AuditService,
) {
    fun computePrice(
        organizationId: UUID,
        printifyBlueprintId: Long,
        costMinor: Long,
    ): Long {
        val rule =
            markupRuleRepository.findRule(organizationId, printifyBlueprintId)
                ?: markupRuleRepository.findRule(organizationId, null)
        return if (rule != null) {
            applyMarkup(costMinor, rule.markupType, rule.markupValue)
        } else {
            applyMarkup(costMinor, MarkupType.PERCENTAGE, OrganizationMarkupRule.SYSTEM_DEFAULT_MARKUP_PERCENT_BASIS_POINTS)
        }
    }

    private fun applyMarkup(
        costMinor: Long,
        markupType: MarkupType,
        markupValue: Int,
    ): Long =
        when (markupType) {
            MarkupType.PERCENTAGE -> costMinor + (costMinor * markupValue) / 10_000
            MarkupType.FLAT -> costMinor + markupValue
        }

    fun listRules(
        organizationId: UUID,
        currentUser: CurrentUser,
    ): List<OrganizationMarkupRule> {
        membershipService.requireActiveMembership(organizationId, currentUser)
        return markupRuleRepository.listForOrganization(organizationId)
    }

    @Transactional
    fun upsertRule(
        organizationId: UUID,
        printifyBlueprintId: Long?,
        markupType: MarkupType,
        markupValue: Int,
        currentUser: CurrentUser,
    ): OrganizationMarkupRule {
        membershipService.requireManagerRole(organizationId, currentUser)
        if (markupValue < 0) throw ValidationException("Markup value must be zero or greater.")
        if (markupType == MarkupType.PERCENTAGE && markupValue > 100_000) {
            throw ValidationException("Percentage markup must be a realistic value (under 1000%).")
        }
        val rule = markupRuleRepository.upsert(organizationId, printifyBlueprintId, markupType, markupValue)
        auditService.record(currentUser.userId, organizationId, "organization_markup_rule.upserted", "organization_markup_rule", rule.id)
        return rule
    }

    @Transactional
    fun deleteRule(
        organizationId: UUID,
        ruleId: UUID,
        currentUser: CurrentUser,
    ) {
        membershipService.requireManagerRole(organizationId, currentUser)
        val rows = markupRuleRepository.delete(organizationId, ruleId)
        if (rows > 0) {
            auditService.record(currentUser.userId, organizationId, "organization_markup_rule.deleted", "organization_markup_rule", ruleId)
        }
    }
}
