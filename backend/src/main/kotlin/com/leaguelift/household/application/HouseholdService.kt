package com.leaguelift.household.application

import com.leaguelift.audit.application.AuditService
import com.leaguelift.common.error.NotFoundException
import com.leaguelift.common.web.CurrentUser
import com.leaguelift.household.domain.Household
import com.leaguelift.household.domain.HouseholdAdult
import com.leaguelift.household.persistence.HouseholdRepository
import com.leaguelift.membership.application.MembershipService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class HouseholdService(
    private val householdRepository: HouseholdRepository,
    private val membershipService: MembershipService,
    private val auditService: AuditService,
) {

    fun list(organizationId: UUID, currentUser: CurrentUser, offset: Int, limit: Int): List<Household> {
        membershipService.requireActiveMembership(organizationId, currentUser)
        return householdRepository.findAll(organizationId, offset, limit)
    }

    fun count(organizationId: UUID, currentUser: CurrentUser): Long {
        membershipService.requireActiveMembership(organizationId, currentUser)
        return householdRepository.countAll(organizationId)
    }

    fun get(organizationId: UUID, householdId: UUID, currentUser: CurrentUser): Household {
        membershipService.requireActiveMembership(organizationId, currentUser)
        return householdRepository.findById(householdId, organizationId)
            ?: throw NotFoundException("HOUSEHOLD_NOT_FOUND", "The household could not be found.")
    }

    @Transactional
    fun create(
        organizationId: UUID,
        displayName: String,
        contactEmail: String?,
        contactPhone: String?,
        notes: String?,
        currentUser: CurrentUser,
    ): Household {
        membershipService.requireManagerRole(organizationId, currentUser)
        val household = householdRepository.insert(organizationId, displayName, contactEmail, contactPhone, notes)
        auditService.record(currentUser.userId, organizationId, "household.created", "household", household.id)
        return household
    }

    @Transactional
    fun update(
        organizationId: UUID,
        householdId: UUID,
        displayName: String?,
        contactEmail: String?,
        contactPhone: String?,
        notes: String?,
        currentUser: CurrentUser,
        emailRemindersOptOut: Boolean? = null,
        smsRemindersOptIn: Boolean? = null,
    ): Household {
        membershipService.requireManagerRole(organizationId, currentUser)
        householdRepository.findById(householdId, organizationId)
            ?: throw NotFoundException("HOUSEHOLD_NOT_FOUND", "The household could not be found.")
        householdRepository.update(householdId, organizationId, displayName, contactEmail, contactPhone, notes, emailRemindersOptOut, smsRemindersOptIn)
        auditService.record(currentUser.userId, organizationId, "household.updated", "household", householdId)
        return householdRepository.findById(householdId, organizationId)!!
    }

    fun listAdults(organizationId: UUID, householdId: UUID, currentUser: CurrentUser): List<HouseholdAdult> {
        membershipService.requireActiveMembership(organizationId, currentUser)
        householdRepository.findById(householdId, organizationId)
            ?: throw NotFoundException("HOUSEHOLD_NOT_FOUND", "The household could not be found.")
        return householdRepository.listAdults(householdId, organizationId)
    }

    @Transactional
    fun addAdult(
        organizationId: UUID,
        householdId: UUID,
        firstName: String,
        lastName: String,
        email: String?,
        phone: String?,
        relationship: String?,
        isPrimary: Boolean,
        currentUser: CurrentUser,
    ): HouseholdAdult {
        membershipService.requireManagerRole(organizationId, currentUser)
        householdRepository.findById(householdId, organizationId)
            ?: throw NotFoundException("HOUSEHOLD_NOT_FOUND", "The household could not be found.")
        val adult = householdRepository.insertAdult(householdId, organizationId, firstName, lastName, email, phone, relationship, isPrimary)
        auditService.record(currentUser.userId, organizationId, "household.adult.added", "household_adult", adult.id)
        return adult
    }

    @Transactional
    fun removeAdult(organizationId: UUID, householdId: UUID, adultId: UUID, currentUser: CurrentUser) {
        membershipService.requireManagerRole(organizationId, currentUser)
        val rows = householdRepository.archiveAdult(adultId, householdId, organizationId)
        if (rows == 0) throw NotFoundException("ADULT_NOT_FOUND", "The household adult could not be found.")
        auditService.record(currentUser.userId, organizationId, "household.adult.removed", "household_adult", adultId)
    }
}
