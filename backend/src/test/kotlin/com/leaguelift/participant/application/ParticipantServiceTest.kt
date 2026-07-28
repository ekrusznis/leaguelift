package com.leaguelift.participant.application

import com.leaguelift.audit.application.AuditService
import com.leaguelift.common.error.ConflictException
import com.leaguelift.common.error.NotFoundException
import com.leaguelift.common.web.CurrentUser
import com.leaguelift.household.persistence.HouseholdRepository
import com.leaguelift.membership.application.MembershipService
import com.leaguelift.membership.domain.MembershipRole
import com.leaguelift.membership.domain.MembershipStatus
import com.leaguelift.membership.domain.OrganizationMembership
import com.leaguelift.participant.domain.Participant
import com.leaguelift.participant.domain.ParticipantStatus
import com.leaguelift.participant.domain.ParticipantTeamAssignment
import com.leaguelift.participant.persistence.ParticipantRepository
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.springframework.dao.DuplicateKeyException
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ParticipantServiceTest {

    private val participantRepository = mockk<ParticipantRepository>()
    private val householdRepository = mockk<HouseholdRepository>()
    private val membershipService = mockk<MembershipService>()
    private val auditService = mockk<AuditService>()
    private val service = ParticipantService(participantRepository, householdRepository, membershipService, auditService)

    private val orgId = UUID.randomUUID()
    private val householdId = UUID.randomUUID()
    private val currentUser = CurrentUser(UUID.randomUUID(), "manager@example.com", "Manager")

    @Test
    fun `listForHousehold requires active membership`() {
        every { membershipService.requireActiveMembership(orgId, currentUser) } returns managerMembership()
        every { householdRepository.findById(householdId, orgId) } returns mockk()
        every { participantRepository.findByHousehold(householdId, orgId) } returns emptyList()

        service.listForHousehold(orgId, householdId, currentUser)

        verify(exactly = 1) { membershipService.requireActiveMembership(orgId, currentUser) }
    }

    @Test
    fun `listForHousehold throws NotFoundException when household does not exist`() {
        every { membershipService.requireActiveMembership(orgId, currentUser) } returns managerMembership()
        every { householdRepository.findById(householdId, orgId) } returns null

        assertFailsWith<NotFoundException> {
            service.listForHousehold(orgId, householdId, currentUser)
        }
    }

    @Test
    fun `create requires manager role and records audit`() {
        every { membershipService.requireManagerRole(orgId, currentUser) } returns managerMembership()
        every { householdRepository.findById(householdId, orgId) } returns mockk()
        val participant = sampleParticipant()
        every { participantRepository.insert(organizationId = orgId, householdId = householdId, firstName = participant.firstName, lastName = participant.lastName, dateOfBirth = participant.dateOfBirth, notes = participant.notes) } returns participant
        every { auditService.record(any(), any(), any(), any(), any(), any()) } just runs

        val result = service.create(orgId, householdId, participant.firstName, participant.lastName, participant.dateOfBirth, participant.notes, currentUser)

        assertEquals(participant.id, result.id)
        verify(exactly = 1) { membershipService.requireManagerRole(orgId, currentUser) }
        verify(exactly = 1) { auditService.record(currentUser.userId, orgId, "participant.created", "participant", participant.id, any()) }
    }

    @Test
    fun `create throws NotFoundException when household does not exist`() {
        every { membershipService.requireManagerRole(orgId, currentUser) } returns managerMembership()
        every { householdRepository.findById(householdId, orgId) } returns null

        assertFailsWith<NotFoundException> {
            service.create(orgId, householdId, "Jane", "Doe", null, null, currentUser)
        }
    }

    @Test
    fun `update throws NotFoundException when participant does not exist`() {
        every { membershipService.requireManagerRole(orgId, currentUser) } returns managerMembership()
        every { participantRepository.findById(any(), orgId) } returns null

        assertFailsWith<NotFoundException> {
            service.update(orgId, UUID.randomUUID(), null, null, null, null, currentUser)
        }
    }

    @Test
    fun `update records audit and returns updated participant`() {
        val participant = sampleParticipant()
        every { membershipService.requireManagerRole(orgId, currentUser) } returns managerMembership()
        every { participantRepository.findById(participant.id, orgId) } returns participant
        every { participantRepository.update(participant.id, orgId, any(), any(), any(), any()) } returns 1
        every { auditService.record(any(), any(), any(), any(), any(), any()) } just runs

        service.update(orgId, participant.id, "Updated", null, null, null, currentUser)

        verify(exactly = 1) { auditService.record(currentUser.userId, orgId, "participant.updated", "participant", participant.id, any()) }
    }

    @Test
    fun `listTeams throws NotFoundException when participant does not exist`() {
        every { membershipService.requireActiveMembership(orgId, currentUser) } returns managerMembership()
        every { participantRepository.findById(any(), orgId) } returns null

        assertFailsWith<NotFoundException> {
            service.listTeams(orgId, UUID.randomUUID(), currentUser)
        }
    }

    @Test
    fun `assignToTeam throws ConflictException on duplicate assignment`() {
        val participant = sampleParticipant()
        val teamId = UUID.randomUUID()
        every { membershipService.requireManagerRole(orgId, currentUser) } returns managerMembership()
        every { participantRepository.findById(participant.id, orgId) } returns participant
        every { participantRepository.assignToTeam(participant.id, teamId, orgId, any()) } throws DuplicateKeyException("unique violation")

        assertFailsWith<ConflictException> {
            service.assignToTeam(orgId, participant.id, teamId, null, currentUser)
        }
    }

    @Test
    fun `assignToTeam records audit on success`() {
        val participant = sampleParticipant()
        val teamId = UUID.randomUUID()
        val assignment = sampleAssignment(participant.id, teamId)
        every { membershipService.requireManagerRole(orgId, currentUser) } returns managerMembership()
        every { participantRepository.findById(participant.id, orgId) } returns participant
        every { participantRepository.assignToTeam(participant.id, teamId, orgId, any()) } returns assignment
        every { auditService.record(any(), any(), any(), any(), any(), any()) } just runs

        service.assignToTeam(orgId, participant.id, teamId, null, currentUser)

        verify(exactly = 1) { auditService.record(currentUser.userId, orgId, "participant.team.assigned", "participant_team", assignment.id, any()) }
    }

    @Test
    fun `removeFromTeam throws NotFoundException when assignment does not exist`() {
        every { membershipService.requireManagerRole(orgId, currentUser) } returns managerMembership()
        every { participantRepository.removeFromTeam(any(), any(), orgId) } returns 0

        assertFailsWith<NotFoundException> {
            service.removeFromTeam(orgId, UUID.randomUUID(), UUID.randomUUID(), currentUser)
        }
    }

    private fun sampleParticipant() = Participant(
        id = UUID.randomUUID(),
        householdId = householdId,
        organizationId = orgId,
        firstName = "Emma",
        lastName = "Smith",
        dateOfBirth = LocalDate.of(2014, 5, 10),
        notes = null,
        status = ParticipantStatus.ACTIVE,
        createdAt = Instant.now(),
        updatedAt = Instant.now(),
    )

    private fun sampleAssignment(participantId: UUID, teamId: UUID) = ParticipantTeamAssignment(
        id = UUID.randomUUID(),
        participantId = participantId,
        teamId = teamId,
        organizationId = orgId,
        status = "ACTIVE",
        joinedAt = null,
        createdAt = Instant.now(),
        updatedAt = Instant.now(),
    )

    private fun managerMembership() = OrganizationMembership(
        id = UUID.randomUUID(),
        organizationId = orgId,
        userId = currentUser.userId,
        role = MembershipRole.ADMINISTRATOR,
        status = MembershipStatus.ACTIVE,
        createdAt = Instant.now(),
        updatedAt = Instant.now(),
    )
}
