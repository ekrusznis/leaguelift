package com.rally26.onboarding.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.rally26.audit.application.AuditService
import com.rally26.common.web.CurrentUser
import com.rally26.document.application.DocumentService
import com.rally26.fee.application.FeeService
import com.rally26.fee.persistence.FeeRepository
import com.rally26.household.persistence.HouseholdRepository
import com.rally26.invitation.application.InvitationService
import com.rally26.invitation.domain.Invitation
import com.rally26.invitation.persistence.InvitationRepository
import com.rally26.media.persistence.MediaAssignmentRepository
import com.rally26.membership.application.MembershipService
import com.rally26.membership.domain.MembershipRole
import com.rally26.membership.domain.OrganizationMembership
import com.rally26.onboarding.domain.BulkActionStatus
import com.rally26.participant.domain.Participant
import com.rally26.participant.domain.ParticipantStatus
import com.rally26.participant.persistence.ParticipantRepository
import com.rally26.team.domain.Sport
import com.rally26.team.domain.Team
import com.rally26.team.domain.TeamStatus
import com.rally26.team.persistence.TeamRepository
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BulkOnboardingServiceTest {
    private val invitationService = mockk<InvitationService>()
    private val invitationRepository = mockk<InvitationRepository>()
    private val participantRepository = mockk<ParticipantRepository>()
    private val teamRepository = mockk<TeamRepository>()
    private val feeService = mockk<FeeService>()
    private val feeRepository = mockk<FeeRepository>()
    private val documentService = mockk<DocumentService>()
    private val mediaAssignmentRepository = mockk<MediaAssignmentRepository>()
    private val householdRepository = mockk<HouseholdRepository>()
    private val membershipService = mockk<MembershipService>()
    private val auditService = mockk<AuditService>()
    private val service =
        BulkOnboardingService(
            invitationService,
            invitationRepository,
            participantRepository,
            teamRepository,
            feeService,
            feeRepository,
            documentService,
            mediaAssignmentRepository,
            householdRepository,
            membershipService,
            auditService,
            ObjectMapper(),
        )

    private val organizationId = UUID.randomUUID()
    private val user = CurrentUser(UUID.randomUUID(), "owner@example.com", "Owner")

    private fun allowManager() {
        every { membershipService.requireManagerRole(organizationId, user) } returns mockk<OrganizationMembership>()
        every { auditService.record(any(), any(), any(), any(), any(), any()) } just runs
    }

    @Test
    fun `bulk staff invitations skip duplicate request emails and never return raw tokens`() {
        allowManager()
        every { invitationRepository.findPendingForOrganizationAndEmail(organizationId, "coach@example.com") } returns null
        val invitation = mockk<Invitation>()
        every { invitation.id } returns UUID.randomUUID()
        every {
            invitationService.invite(organizationId, "coach@example.com", MembershipRole.TEAM_ADMINISTRATOR, user)
        } returns InvitationService.CreatedInvitation(invitation, "raw-secret-token")

        val result =
            service.inviteStaff(
                organizationId,
                listOf(
                    BulkInvitationCommand("Coach@example.com", MembershipRole.TEAM_ADMINISTRATOR),
                    BulkInvitationCommand("coach@example.com", MembershipRole.TEAM_ADMINISTRATOR),
                ),
                user,
            )

        assertEquals(1, result.succeededCount)
        assertEquals(1, result.skippedCount)
        assertNull(result.items.first().message)
        verify(exactly = 1) {
            invitationService.invite(organizationId, "coach@example.com", MembershipRole.TEAM_ADMINISTRATOR, user)
        }
    }

    @Test
    fun `bulk team assignment is idempotent per participant`() {
        allowManager()
        val now = Instant.now()
        val teamId = UUID.randomUUID()
        val team = Team(teamId, organizationId, "U14 Blue", Sport.VOLLEYBALL, null, TeamStatus.ACTIVE, null, now, now)
        val first =
            Participant(
                UUID.randomUUID(),
                UUID.randomUUID(),
                organizationId,
                "Riley",
                "Smith",
                null,
                null,
                ParticipantStatus.ACTIVE,
                now,
                now,
            )
        val second =
            Participant(
                UUID.randomUUID(),
                UUID.randomUUID(),
                organizationId,
                "Avery",
                "Jones",
                null,
                null,
                ParticipantStatus.ACTIVE,
                now,
                now,
            )
        every { teamRepository.findById(teamId, organizationId) } returns team
        every { participantRepository.findById(first.id, organizationId) } returns first
        every { participantRepository.findById(second.id, organizationId) } returns second
        every { participantRepository.ensureTeamAssignment(first.id, teamId, organizationId, null) } returns true
        every { participantRepository.ensureTeamAssignment(second.id, teamId, organizationId, null) } returns false

        val result = service.assignParticipantsToTeam(organizationId, teamId, listOf(first.id, second.id), user)

        assertEquals(1, result.succeededCount)
        assertEquals(1, result.skippedCount)
        assertEquals(BulkActionStatus.SUCCEEDED, result.items.first().status)
        assertEquals(BulkActionStatus.SKIPPED, result.items.last().status)
    }
}
