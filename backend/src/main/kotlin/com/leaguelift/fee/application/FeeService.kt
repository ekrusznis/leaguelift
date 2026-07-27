package com.leaguelift.fee.application

import com.leaguelift.audit.application.AuditService
import com.leaguelift.common.error.NotFoundException
import com.leaguelift.common.web.CurrentUser
import com.leaguelift.fee.domain.FeeAssignment
import com.leaguelift.fee.domain.FeeAssignmentStatus
import com.leaguelift.fee.domain.FeeTemplate
import com.leaguelift.fee.persistence.FeeRepository
import com.leaguelift.household.persistence.HouseholdRepository
import com.leaguelift.membership.application.MembershipService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.util.UUID

@Service
class FeeService(
    private val feeRepository: FeeRepository,
    private val householdRepository: HouseholdRepository,
    private val membershipService: MembershipService,
    private val auditService: AuditService,
) {

    // --- Fee Templates ---

    fun listTemplates(organizationId: UUID, currentUser: CurrentUser, offset: Int, limit: Int): List<FeeTemplate> {
        membershipService.requireActiveMembership(organizationId, currentUser)
        return feeRepository.findAllTemplates(organizationId, offset, limit)
    }

    fun countTemplates(organizationId: UUID, currentUser: CurrentUser): Long {
        membershipService.requireActiveMembership(organizationId, currentUser)
        return feeRepository.countAllTemplates(organizationId)
    }

    fun getTemplate(organizationId: UUID, templateId: UUID, currentUser: CurrentUser): FeeTemplate {
        membershipService.requireActiveMembership(organizationId, currentUser)
        return feeRepository.findTemplateById(templateId, organizationId)
            ?: throw NotFoundException("FEE_TEMPLATE_NOT_FOUND", "The fee template could not be found.")
    }

    @Transactional
    fun createTemplate(
        organizationId: UUID,
        name: String,
        description: String?,
        amountMinor: Long,
        currency: String,
        currentUser: CurrentUser,
    ): FeeTemplate {
        membershipService.requireManagerRole(organizationId, currentUser)
        val template = feeRepository.insertTemplate(organizationId, name, description, amountMinor, currency)
        auditService.record(currentUser.userId, organizationId, "fee_template.created", "fee_template", template.id)
        return template
    }

    @Transactional
    fun updateTemplate(
        organizationId: UUID,
        templateId: UUID,
        name: String?,
        description: String?,
        amountMinor: Long?,
        currentUser: CurrentUser,
    ): FeeTemplate {
        membershipService.requireManagerRole(organizationId, currentUser)
        feeRepository.findTemplateById(templateId, organizationId)
            ?: throw NotFoundException("FEE_TEMPLATE_NOT_FOUND", "The fee template could not be found.")
        feeRepository.updateTemplate(templateId, organizationId, name, description, amountMinor)
        auditService.record(currentUser.userId, organizationId, "fee_template.updated", "fee_template", templateId)
        return feeRepository.findTemplateById(templateId, organizationId)!!
    }

    @Transactional
    fun archiveTemplate(organizationId: UUID, templateId: UUID, currentUser: CurrentUser) {
        membershipService.requireManagerRole(organizationId, currentUser)
        val rows = feeRepository.archiveTemplate(templateId, organizationId)
        if (rows == 0) throw NotFoundException("FEE_TEMPLATE_NOT_FOUND", "The fee template could not be found.")
        auditService.record(currentUser.userId, organizationId, "fee_template.archived", "fee_template", templateId)
    }

    // --- Fee Assignments ---

    fun listForHousehold(organizationId: UUID, householdId: UUID, currentUser: CurrentUser, offset: Int, limit: Int): List<FeeAssignment> {
        membershipService.requireActiveMembership(organizationId, currentUser)
        householdRepository.findById(householdId, organizationId)
            ?: throw NotFoundException("HOUSEHOLD_NOT_FOUND", "The household could not be found.")
        return feeRepository.findByHousehold(householdId, organizationId, offset, limit)
    }

    fun countForHousehold(organizationId: UUID, householdId: UUID, currentUser: CurrentUser): Long {
        membershipService.requireActiveMembership(organizationId, currentUser)
        householdRepository.findById(householdId, organizationId)
            ?: throw NotFoundException("HOUSEHOLD_NOT_FOUND", "The household could not be found.")
        return feeRepository.countByHousehold(householdId, organizationId)
    }

    fun getAssignment(organizationId: UUID, assignmentId: UUID, currentUser: CurrentUser): FeeAssignment {
        membershipService.requireActiveMembership(organizationId, currentUser)
        return feeRepository.findAssignmentById(assignmentId, organizationId)
            ?: throw NotFoundException("FEE_ASSIGNMENT_NOT_FOUND", "The fee assignment could not be found.")
    }

    @Transactional
    fun createAssignment(
        organizationId: UUID,
        householdId: UUID,
        participantId: UUID?,
        feeTemplateId: UUID?,
        description: String,
        originalAmountMinor: Long,
        currency: String,
        dueDate: LocalDate?,
        currentUser: CurrentUser,
    ): FeeAssignment {
        membershipService.requireManagerRole(organizationId, currentUser)
        householdRepository.findById(householdId, organizationId)
            ?: throw NotFoundException("HOUSEHOLD_NOT_FOUND", "The household could not be found.")
        val assignment = feeRepository.insertAssignment(organizationId, householdId, participantId, feeTemplateId, description, originalAmountMinor, currency, dueDate)
        auditService.record(currentUser.userId, organizationId, "fee_assignment.created", "fee_assignment", assignment.id)
        return assignment
    }

    @Transactional
    fun updateAssignmentStatus(
        organizationId: UUID,
        assignmentId: UUID,
        status: FeeAssignmentStatus,
        currentUser: CurrentUser,
    ): FeeAssignment {
        membershipService.requireManagerRole(organizationId, currentUser)
        feeRepository.findAssignmentById(assignmentId, organizationId)
            ?: throw NotFoundException("FEE_ASSIGNMENT_NOT_FOUND", "The fee assignment could not be found.")
        feeRepository.updateAssignmentStatus(assignmentId, organizationId, status)
        auditService.record(currentUser.userId, organizationId, "fee_assignment.status_updated", "fee_assignment", assignmentId)
        return feeRepository.findAssignmentById(assignmentId, organizationId)!!
    }
}
