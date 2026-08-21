package com.rally26.invitation.application

import com.rally26.audit.application.AuditService
import com.rally26.authorization.application.AuthorizationService
import com.rally26.authorization.domain.GuardianRelationship
import com.rally26.authorization.domain.GuardianRelationshipStatus
import com.rally26.authorization.domain.ResourceRole
import com.rally26.authorization.domain.RoleAssignment
import com.rally26.authorization.domain.RoleAssignmentContextType
import com.rally26.authorization.domain.RoleAssignmentStatus
import com.rally26.authorization.persistence.GuardianRelationshipRepository
import com.rally26.authorization.persistence.RoleAssignmentRepository
import com.rally26.common.error.ForbiddenException
import com.rally26.common.error.ValidationException
import com.rally26.common.web.CurrentUser
import com.rally26.household.domain.AdultStatus
import com.rally26.household.domain.Household
import com.rally26.household.domain.HouseholdAdult
import com.rally26.household.domain.HouseholdStatus
import com.rally26.household.persistence.HouseholdRepository
import com.rally26.invitation.domain.HouseholdInvitationKind
import com.rally26.invitation.domain.HouseholdInvitationStatus
import com.rally26.invitation.persistence.HouseholdInvitationRepository
import com.rally26.membership.application.MembershipService
import com.rally26.outbox.application.OutboxWriter
import com.rally26.participant.domain.Participant
import com.rally26.participant.domain.ParticipantStatus
import com.rally26.participant.domain.ParticipantTeamAssignment
import com.rally26.participant.persistence.ParticipantRepository
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class HouseholdInvitationServiceTest {
    private val householdInvitationRepository = mockk<HouseholdInvitationRepository>()
    private val householdRepository = mockk<HouseholdRepository>()
    private val participantRepository = mockk<ParticipantRepository>()
    private val guardianRelationshipRepository = mockk<GuardianRelationshipRepository>()
    private val roleAssignmentRepository = mockk<RoleAssignmentRepository>()
    private val membershipService = mockk<MembershipService>()
    private val authorizationService = mockk<AuthorizationService>()
    private val auditService =
        mockk<AuditService> {
            every { record(any(), any(), any(), any(), any()) } just runs
        }
    private val outboxWriter =
        mockk<OutboxWriter> {
            every { write(any(), any(), any(), any(), any()) } just runs
        }
    private val service =
        HouseholdInvitationService(
            householdInvitationRepository,
            householdRepository,
            participantRepository,
            guardianRelationshipRepository,
            roleAssignmentRepository,
            membershipService,
            authorizationService,
            auditService,
            outboxWriter,
        )

    private val orgId = UUID.randomUUID()
    private val householdId = UUID.randomUUID()
    private val participantId = UUID.randomUUID()
    private val teamId = UUID.randomUUID()
    private val currentUser = CurrentUser(UUID.randomUUID(), "coach@example.com", "Coach")

    private fun sampleParticipant(dob: LocalDate? = LocalDate.now().minusYears(15)) =
        Participant(participantId, householdId, orgId, "Alex", "Athlete", dob, null, ParticipantStatus.ACTIVE, Instant.now(), Instant.now())

    private fun sampleHousehold() =
        Household(
            householdId,
            orgId,
            "The Athlete Family",
            null,
            null,
            null,
            false,
            false,
            HouseholdStatus.ACTIVE,
            Instant.now(),
            Instant.now(),
        )

    // --- inviteGuardian ---

    @Test
    fun `inviteGuardian succeeds for a coach with TEAM_ROSTER_MANAGE on the athlete's team`() {
        every { participantRepository.findById(participantId, orgId) } returns sampleParticipant()
        every { membershipService.hasManagerRole(orgId, currentUser) } returns false
        every { participantRepository.listTeamAssignments(participantId, orgId) } returns
            listOf(ParticipantTeamAssignment(UUID.randomUUID(), participantId, teamId, orgId, "ACTIVE", null, Instant.now(), Instant.now()))
        every { authorizationService.hasTeamCapability(orgId, teamId, currentUser, any()) } returns true
        every { householdRepository.findById(householdId, orgId) } returns sampleHousehold()
        every { householdRepository.listAdults(householdId, orgId) } returns emptyList()
        val adult =
            HouseholdAdult(
                UUID.randomUUID(),
                householdId,
                orgId,
                "Pat",
                "Parent",
                "pat@example.com",
                null,
                "Parent",
                false,
                AdultStatus.ACTIVE,
                Instant.now(),
                Instant.now(),
            )
        every {
            householdRepository.insertAdult(householdId, orgId, "Pat", "Parent", "pat@example.com", null, "Parent", false)
        } returns adult
        every { guardianRelationshipRepository.isAdultLinkedToAnyUser(adult.id) } returns false
        every { householdInvitationRepository.listPendingForHousehold(householdId, orgId) } returns emptyList()
        every {
            householdInvitationRepository.insert(
                orgId,
                householdId,
                HouseholdInvitationKind.GUARDIAN,
                adult.id,
                participantId,
                "pat@example.com",
                currentUser.userId,
                any(),
                any(),
            )
        } returns sampleInvitation(HouseholdInvitationKind.GUARDIAN, adult.id)

        val result = service.inviteGuardian(orgId, participantId, "Pat", "Parent", "pat@example.com", "Parent", currentUser)

        assertEquals(HouseholdInvitationKind.GUARDIAN, result.invitation.kind)
        verify(exactly = 1) { auditService.record(currentUser.userId, orgId, "household.guardian_invited", "household_invitation", any()) }
    }

    @Test
    fun `inviteGuardian throws ForbiddenException for a coach with no roster-manage capability on any of the athlete's teams`() {
        every { participantRepository.findById(participantId, orgId) } returns sampleParticipant()
        every { membershipService.hasManagerRole(orgId, currentUser) } returns false
        every { participantRepository.listTeamAssignments(participantId, orgId) } returns
            listOf(ParticipantTeamAssignment(UUID.randomUUID(), participantId, teamId, orgId, "ACTIVE", null, Instant.now(), Instant.now()))
        every { authorizationService.hasTeamCapability(orgId, teamId, currentUser, any()) } returns false

        assertFailsWith<ForbiddenException> {
            service.inviteGuardian(orgId, participantId, "Pat", "Parent", "pat@example.com", "Parent", currentUser)
        }
    }

    @Test
    fun `inviteGuardian throws ValidationException when the contact is already linked to an account`() {
        every { participantRepository.findById(participantId, orgId) } returns sampleParticipant()
        every { membershipService.hasManagerRole(orgId, currentUser) } returns true
        every { householdRepository.findById(householdId, orgId) } returns sampleHousehold()
        val adult =
            HouseholdAdult(
                UUID.randomUUID(),
                householdId,
                orgId,
                "Pat",
                "Parent",
                "pat@example.com",
                null,
                "Parent",
                false,
                AdultStatus.ACTIVE,
                Instant.now(),
                Instant.now(),
            )
        every { householdRepository.listAdults(householdId, orgId) } returns listOf(adult)
        every { guardianRelationshipRepository.isAdultLinkedToAnyUser(adult.id) } returns true

        assertFailsWith<ValidationException> {
            service.inviteGuardian(orgId, participantId, "Pat", "Parent", "pat@example.com", "Parent", currentUser)
        }
    }

    // --- inviteAthlete ---

    @Test
    fun `inviteAthlete throws ForbiddenException when the caller is not an active guardian of the household`() {
        every { participantRepository.findById(participantId, orgId) } returns sampleParticipant()
        every { guardianRelationshipRepository.findActiveForHousehold(currentUser.userId, householdId) } returns null

        assertFailsWith<ForbiddenException> {
            service.inviteAthlete(orgId, participantId, "athlete@example.com", currentUser)
        }
    }

    @Test
    fun `inviteAthlete throws ValidationException when the athlete has no recorded date of birth`() {
        every { participantRepository.findById(participantId, orgId) } returns sampleParticipant(dob = null)
        every { guardianRelationshipRepository.findActiveForHousehold(currentUser.userId, householdId) } returns
            sampleGuardianRelationship()

        assertFailsWith<ValidationException> {
            service.inviteAthlete(orgId, participantId, "athlete@example.com", currentUser)
        }
    }

    @Test
    fun `inviteAthlete throws ValidationException when the athlete is under the minimum age`() {
        every { participantRepository.findById(participantId, orgId) } returns sampleParticipant(dob = LocalDate.now().minusYears(10))
        every { guardianRelationshipRepository.findActiveForHousehold(currentUser.userId, householdId) } returns
            sampleGuardianRelationship()

        assertFailsWith<ValidationException> {
            service.inviteAthlete(orgId, participantId, "athlete@example.com", currentUser)
        }
    }

    @Test
    fun `inviteAthlete succeeds for an active guardian inviting an old-enough athlete`() {
        every { participantRepository.findById(participantId, orgId) } returns sampleParticipant(dob = LocalDate.now().minusYears(15))
        every { guardianRelationshipRepository.findActiveForHousehold(currentUser.userId, householdId) } returns
            sampleGuardianRelationship()
        every {
            roleAssignmentRepository.hasAnyActiveForResourceAndRole(
                RoleAssignmentContextType.PARTICIPANT,
                participantId,
                ResourceRole.ATHLETE_SELF,
            )
        } returns false
        every { householdInvitationRepository.listPendingForHousehold(householdId, orgId) } returns emptyList()
        every {
            householdInvitationRepository.insert(
                orgId,
                householdId,
                HouseholdInvitationKind.ATHLETE,
                null,
                participantId,
                "athlete@example.com",
                currentUser.userId,
                any(),
                any(),
            )
        } returns sampleInvitation(HouseholdInvitationKind.ATHLETE, null)

        val result = service.inviteAthlete(orgId, participantId, "athlete@example.com", currentUser)

        assertEquals(HouseholdInvitationKind.ATHLETE, result.invitation.kind)
        verify(exactly = 1) { auditService.record(currentUser.userId, orgId, "household.athlete_invited", "household_invitation", any()) }
    }

    // --- accept ---

    @Test
    fun `accept for a GUARDIAN invitation inserts a guardian_relationship`() {
        val token = "raw-token"
        val tokenHash = sha256HexForTest(token)
        val adultId = UUID.randomUUID()
        val invitation = sampleInvitation(HouseholdInvitationKind.GUARDIAN, adultId).copy(email = currentUser.email)
        every { householdInvitationRepository.findByTokenHash(tokenHash) } returns invitation
        every { guardianRelationshipRepository.insert(orgId, householdId, adultId, currentUser.userId) } returns
            sampleGuardianRelationship()
        every { householdInvitationRepository.markStatus(invitation.id, HouseholdInvitationStatus.ACCEPTED, any()) } returns 1
        every { householdInvitationRepository.findById(invitation.id) } returns invitation.copy(status = HouseholdInvitationStatus.ACCEPTED)

        service.accept(token, currentUser)

        verify(exactly = 1) { guardianRelationshipRepository.insert(orgId, householdId, adultId, currentUser.userId) }
    }

    @Test
    fun `accept for an ATHLETE invitation calls linkAthleteSelf`() {
        val token = "raw-token-athlete"
        val tokenHash = sha256HexForTest(token)
        val invitation = sampleInvitation(HouseholdInvitationKind.ATHLETE, null).copy(email = currentUser.email)
        every { householdInvitationRepository.findByTokenHash(tokenHash) } returns invitation
        every {
            authorizationService.linkAthleteSelf(orgId, householdId, participantId, currentUser.userId, invitation.invitedByUserId)
        } returns sampleRoleAssignment()
        every { householdInvitationRepository.markStatus(invitation.id, HouseholdInvitationStatus.ACCEPTED, any()) } returns 1
        every { householdInvitationRepository.findById(invitation.id) } returns invitation.copy(status = HouseholdInvitationStatus.ACCEPTED)

        service.accept(token, currentUser)

        verify(exactly = 1) {
            authorizationService.linkAthleteSelf(orgId, householdId, participantId, currentUser.userId, invitation.invitedByUserId)
        }
    }

    @Test
    fun `accept throws ForbiddenException when the invitation email does not match the caller`() {
        val token = "raw-token-mismatch"
        val tokenHash = sha256HexForTest(token)
        val invitation = sampleInvitation(HouseholdInvitationKind.GUARDIAN, UUID.randomUUID()).copy(email = "someone-else@example.com")
        every { householdInvitationRepository.findByTokenHash(tokenHash) } returns invitation

        assertFailsWith<ForbiddenException> {
            service.accept(token, currentUser)
        }
    }

    private fun sampleInvitation(
        kind: HouseholdInvitationKind,
        adultId: UUID?,
    ) = com.rally26.invitation.domain.HouseholdInvitation(
        id = UUID.randomUUID(),
        organizationId = orgId,
        householdId = householdId,
        kind = kind,
        householdAdultId = adultId,
        participantId = participantId,
        email = "invitee@example.com",
        status = HouseholdInvitationStatus.PENDING,
        invitedByUserId = UUID.randomUUID(),
        expiresAt = Instant.now().plusSeconds(3600),
        acceptedAt = null,
        createdAt = Instant.now(),
        updatedAt = Instant.now(),
    )

    private fun sampleGuardianRelationship() =
        GuardianRelationship(
            UUID.randomUUID(),
            orgId,
            householdId,
            UUID.randomUUID(),
            currentUser.userId,
            GuardianRelationshipStatus.ACTIVE,
            Instant.now(),
            Instant.now(),
        )

    private fun sampleRoleAssignment() =
        RoleAssignment(
            UUID.randomUUID(),
            orgId,
            currentUser.userId,
            RoleAssignmentContextType.PARTICIPANT,
            participantId,
            ResourceRole.ATHLETE_SELF,
            RoleAssignmentStatus.ACTIVE,
            null,
            Instant.now(),
            Instant.now(),
        )

    private fun sha256HexForTest(value: String): String =
        java.security.MessageDigest
            .getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
