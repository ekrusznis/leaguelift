package com.rally26.fee.application

import com.rally26.authorization.application.AuthorizationService
import com.rally26.authorization.domain.Capabilities
import com.rally26.common.error.ForbiddenException
import com.rally26.common.error.NotFoundException
import com.rally26.common.util.CsvUtil
import com.rally26.common.web.CurrentUser
import com.rally26.fee.domain.FeeAssignmentSearchCriteria
import com.rally26.fee.domain.FeeAssignmentSummary
import com.rally26.fee.domain.FeeAssignmentWithBalance
import com.rally26.fee.domain.FeeTemplate
import com.rally26.fee.domain.FeeTemplateSearchCriteria
import com.rally26.fee.persistence.FeeSearchRepository
import com.rally26.household.persistence.HouseholdRepository
import com.rally26.membership.application.MembershipService
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class FeeSearchService(
    private val repository: FeeSearchRepository,
    private val membershipService: MembershipService,
    private val authorizationService: AuthorizationService,
    private val householdRepository: HouseholdRepository,
) {
    /**
     * Fee templates are an organization-management surface. This also allows an
     * Owner/Admin to explicitly search archived templates without changing the
     * legacy active-only list endpoint.
     */
    fun searchTemplates(
        organizationId: UUID,
        criteria: FeeTemplateSearchCriteria,
        currentUser: CurrentUser,
        offset: Int,
        limit: Int,
    ): List<FeeTemplate> {
        membershipService.requireManagerRole(organizationId, currentUser)
        return repository.searchTemplates(organizationId, criteria, offset, limit)
    }

    fun countTemplates(
        organizationId: UUID,
        criteria: FeeTemplateSearchCriteria,
        currentUser: CurrentUser,
    ): Long {
        membershipService.requireManagerRole(organizationId, currentUser)
        return repository.countTemplates(organizationId, criteria)
    }

    fun searchHouseholdAssignments(
        organizationId: UUID,
        householdId: UUID,
        criteria: FeeAssignmentSearchCriteria,
        currentUser: CurrentUser,
        offset: Int,
        limit: Int,
    ): List<FeeAssignmentWithBalance> {
        requireHouseholdFeeAccess(organizationId, householdId, currentUser)
        return repository.searchHouseholdAssignments(organizationId, householdId, criteria, offset, limit)
    }

    fun countHouseholdAssignments(
        organizationId: UUID,
        householdId: UUID,
        criteria: FeeAssignmentSearchCriteria,
        currentUser: CurrentUser,
    ): Long {
        requireHouseholdFeeAccess(organizationId, householdId, currentUser)
        return repository.countHouseholdAssignments(organizationId, householdId, criteria)
    }

    fun searchOrganizationAssignments(
        organizationId: UUID,
        criteria: FeeAssignmentSearchCriteria,
        currentUser: CurrentUser,
        offset: Int,
        limit: Int,
    ): List<FeeAssignmentSummary> {
        membershipService.requireManagerRole(organizationId, currentUser)
        return repository.searchOrganizationAssignments(organizationId, criteria, offset, limit)
    }

    fun countOrganizationAssignments(
        organizationId: UUID,
        criteria: FeeAssignmentSearchCriteria,
        currentUser: CurrentUser,
    ): Long {
        membershipService.requireManagerRole(organizationId, currentUser)
        return repository.countOrganizationAssignments(organizationId, criteria)
    }

    /**
     * Search-aware export so what the manager exports matches the current
     * Collections search/filter/sort state. Kept separate from the legacy export
     * endpoint to avoid changing existing service/controller signatures.
     */
    fun exportOrganizationAssignmentsCsv(
        organizationId: UUID,
        criteria: FeeAssignmentSearchCriteria,
        currentUser: CurrentUser,
    ): String {
        membershipService.requireManagerRole(organizationId, currentUser)
        val rows = repository.searchOrganizationAssignments(organizationId, criteria, 0, 5000)
        return buildCsv(rows)
    }

    private fun requireHouseholdFeeAccess(
        organizationId: UUID,
        householdId: UUID,
        currentUser: CurrentUser,
    ) {
        householdRepository.findById(householdId, organizationId)
            ?: throw NotFoundException("HOUSEHOLD_NOT_FOUND", "The household could not be found.")
        if (!authorizationService.hasHouseholdCapability(
                organizationId,
                householdId,
                currentUser,
                Capabilities.HOUSEHOLD_FEE_VIEW,
            )
        ) {
            throw ForbiddenException("HOUSEHOLD_ACCESS_DENIED", "You do not have access to this household's fees.")
        }
    }

    private fun buildCsv(rows: List<FeeAssignmentSummary>): String {
        val header =
            listOf(
                "Household",
                "Participant",
                "Description",
                "Original",
                "Paid",
                "Adjusted",
                "Balance",
                "Currency",
                "Due Date",
                "Status",
            )
        val lines = mutableListOf(header.joinToString(",") { CsvUtil.escape(it) })
        rows.forEach { row ->
            lines +=
                listOf(
                    row.householdName,
                    row.participantName ?: "",
                    row.description,
                    CsvUtil.formatMinor(row.originalAmountMinor),
                    CsvUtil.formatMinor(row.paidMinor),
                    CsvUtil.formatMinor(row.adjustedMinor),
                    CsvUtil.formatMinor(row.balance.balanceMinor),
                    row.currency,
                    row.dueDate?.toString() ?: "",
                    row.status.name,
                ).joinToString(",") { CsvUtil.escape(it) }
        }
        return lines.joinToString("\r\n")
    }
}
