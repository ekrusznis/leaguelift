package com.rally26.profilecorrection.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.rally26.audit.application.AuditService
import com.rally26.authorization.application.AuthorizationService
import com.rally26.common.error.ConflictException
import com.rally26.common.web.CurrentUser
import com.rally26.household.application.HouseholdService
import com.rally26.household.persistence.HouseholdRepository
import com.rally26.membership.application.MembershipService
import com.rally26.membership.domain.OrganizationMembership
import com.rally26.participant.application.ParticipantService
import com.rally26.participant.domain.Participant
import com.rally26.participant.domain.ParticipantStatus
import com.rally26.participant.persistence.ParticipantRepository
import com.rally26.profilecorrection.domain.ProfileCorrectionField
import com.rally26.profilecorrection.domain.ProfileCorrectionRequest
import com.rally26.profilecorrection.domain.ProfileCorrectionStatus
import com.rally26.profilecorrection.domain.ProfileCorrectionTargetType
import com.rally26.profilecorrection.persistence.ProfileCorrectionRepository
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

class ProfileCorrectionServiceTest {
    private val repository = mockk<ProfileCorrectionRepository>()
    private val householdRepository = mockk<HouseholdRepository>()
    private val participantRepository = mockk<ParticipantRepository>()
    private val householdService = mockk<HouseholdService>()
    private val participantService = mockk<ParticipantService>()
    private val membershipService = mockk<MembershipService>()
    private val authorizationService = mockk<AuthorizationService>()
    private val auditService = mockk<AuditService>()
    private val service =
        ProfileCorrectionService(
            repository,
            householdRepository,
            participantRepository,
            householdService,
            participantService,
            membershipService,
            authorizationService,
            auditService,
            ObjectMapper(),
        )

    private val organizationId = UUID.randomUUID()
    private val householdId = UUID.randomUUID()
    private val user = CurrentUser(UUID.randomUUID(), "guardian@example.com", "Guardian")

    @Test
    fun `guardian can request a correction for a linked participant without changing the profile`() {
        val participant = participant(firstName = "Avery")
        val created =
            request(
                participant = participant,
                currentValue = "Avery",
                proposedValue = "Averie",
            )
        every { participantRepository.findById(participant.id, organizationId) } returns participant
        every { membershipService.hasManagerRole(organizationId, user) } returns false
        every { authorizationService.hasGuardianRelationship(organizationId, householdId, user) } returns true
        every {
            repository.hasPending(
                organizationId,
                ProfileCorrectionTargetType.PARTICIPANT,
                participant.id,
                ProfileCorrectionField.PARTICIPANT_FIRST_NAME,
            )
        } returns false
        every {
            repository.insert(
                organizationId,
                householdId,
                ProfileCorrectionTargetType.PARTICIPANT,
                participant.id,
                ProfileCorrectionField.PARTICIPANT_FIRST_NAME,
                "Avery Morgan",
                "Avery",
                "Averie",
                "Name is misspelled",
                user.userId,
            )
        } returns created
        every { auditService.record(any(), any(), any(), any(), any(), any()) } just runs

        val result =
            service.create(
                organizationId,
                ProfileCorrectionTargetType.PARTICIPANT,
                participant.id,
                ProfileCorrectionField.PARTICIPANT_FIRST_NAME,
                " Averie ",
                "Name is misspelled",
                user,
            )

        assertEquals(ProfileCorrectionStatus.PENDING, result.status)
        verify(exactly = 0) { participantService.update(any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `approval refuses to overwrite a profile that changed after submission`() {
        val participant = participant(firstName = "Avery")
        val pending =
            request(
                participant = participant,
                currentValue = "Old spelling",
                proposedValue = "Averie",
            )
        every { membershipService.requireManagerRole(organizationId, user) } returns mockk<OrganizationMembership>()
        every { repository.findById(pending.id, organizationId) } returns pending
        every { participantRepository.findById(participant.id, organizationId) } returns participant

        assertFailsWith<ConflictException> {
            service.approve(organizationId, pending.id, null, user)
        }

        verify(exactly = 0) { participantService.update(any(), any(), any(), any(), any(), any(), any()) }
        verify(exactly = 0) { repository.review(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `approval applies the typed participant change before closing the request`() {
        val participant = participant(firstName = "Avery")
        val pending =
            request(
                participant = participant,
                currentValue = "Avery",
                proposedValue = "Averie",
            )
        val approved =
            pending.copy(
                status = ProfileCorrectionStatus.APPROVED,
                reviewedBy = user.userId,
                reviewerName = user.displayName,
                reviewedAt = Instant.now(),
            )
        every { membershipService.requireManagerRole(organizationId, user) } returns mockk<OrganizationMembership>()
        every { repository.findById(pending.id, organizationId) } returnsMany listOf(pending, approved)
        every { participantRepository.findById(participant.id, organizationId) } returns participant
        every {
            participantService.update(
                organizationId,
                participant.id,
                "Averie",
                null,
                null,
                null,
                user,
            )
        } returns participant.copy(firstName = "Averie")
        every {
            repository.review(
                pending.id,
                organizationId,
                ProfileCorrectionStatus.APPROVED,
                user.userId,
                "Verified with guardian",
            )
        } returns 1
        every { auditService.record(any(), any(), any(), any(), any(), any()) } just runs

        val result = service.approve(organizationId, pending.id, "Verified with guardian", user)

        assertEquals(ProfileCorrectionStatus.APPROVED, result.status)
        verify(exactly = 1) {
            participantService.update(organizationId, participant.id, "Averie", null, null, null, user)
        }
    }

    private fun participant(firstName: String) =
        Participant(
            id = UUID.randomUUID(),
            householdId = householdId,
            organizationId = organizationId,
            firstName = firstName,
            lastName = "Morgan",
            dateOfBirth = LocalDate.of(2012, 9, 15),
            notes = null,
            status = ParticipantStatus.ACTIVE,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
        )

    private fun request(
        participant: Participant,
        currentValue: String,
        proposedValue: String,
    ) = ProfileCorrectionRequest(
        id = UUID.randomUUID(),
        organizationId = organizationId,
        householdId = householdId,
        targetType = ProfileCorrectionTargetType.PARTICIPANT,
        targetId = participant.id,
        field = ProfileCorrectionField.PARTICIPANT_FIRST_NAME,
        targetLabel = "${participant.firstName} ${participant.lastName}",
        currentValue = currentValue,
        proposedValue = proposedValue,
        reason = "Name is misspelled",
        status = ProfileCorrectionStatus.PENDING,
        requestedBy = user.userId,
        requesterName = user.displayName,
        requesterEmail = user.email,
        reviewedBy = null,
        reviewerName = null,
        reviewNote = null,
        requestedAt = Instant.now(),
        reviewedAt = null,
        updatedAt = Instant.now(),
    )
}
