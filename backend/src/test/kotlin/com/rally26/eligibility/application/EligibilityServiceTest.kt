package com.rally26.eligibility.application

import com.rally26.audit.application.AuditService
import com.rally26.authorization.application.AuthorizationService
import com.rally26.authorization.domain.Capabilities
import com.rally26.common.error.ForbiddenException
import com.rally26.common.error.NotFoundException
import com.rally26.common.error.ValidationException
import com.rally26.common.web.CurrentUser
import com.rally26.eligibility.domain.AcceptanceMethod
import com.rally26.eligibility.domain.ClearanceStatus
import com.rally26.eligibility.domain.EligibilityClearance
import com.rally26.eligibility.domain.EligibilityEvidence
import com.rally26.eligibility.domain.EligibilityRequirement
import com.rally26.eligibility.domain.EvidenceClassification
import com.rally26.eligibility.domain.EvidenceStatus
import com.rally26.eligibility.domain.ExpirationRule
import com.rally26.eligibility.domain.GuardianActionMode
import com.rally26.eligibility.domain.RequirementMode
import com.rally26.eligibility.domain.RequirementStatus
import com.rally26.eligibility.persistence.EligibilityClearanceRepository
import com.rally26.eligibility.persistence.EligibilityEvidenceRepository
import com.rally26.eligibility.persistence.EligibilityRequirementRepository
import com.rally26.membership.application.MembershipService
import com.rally26.membership.domain.OrganizationMembership
import com.rally26.participant.domain.Participant
import com.rally26.participant.domain.ParticipantStatus
import com.rally26.participant.domain.ParticipantTeamAssignment
import com.rally26.participant.persistence.ParticipantRepository
import com.rally26.team.domain.Team
import com.rally26.team.domain.TeamStatus
import com.rally26.team.persistence.TeamRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class EligibilityServiceTest {
    private val requirementRepository = mockk<EligibilityRequirementRepository>()
    private val evidenceRepository = mockk<EligibilityEvidenceRepository>()
    private val clearanceRepository = mockk<EligibilityClearanceRepository>()
    private val teamRepository = mockk<TeamRepository>()
    private val participantRepository = mockk<ParticipantRepository>()
    private val membershipService = mockk<MembershipService>()
    private val authorizationService = mockk<AuthorizationService>()
    private val auditService = mockk<AuditService>()
    private val service =
        EligibilityService(
            requirementRepository,
            evidenceRepository,
            clearanceRepository,
            teamRepository,
            participantRepository,
            membershipService,
            authorizationService,
            auditService,
        )

    private val organizationId = UUID.randomUUID()
    private val teamId = UUID.randomUUID()
    private val participantId = UUID.randomUUID()
    private val user = CurrentUser(UUID.randomUUID(), "owner@example.com", "Owner")

    private fun team(
        sport: String = "Soccer",
        season: String? = "Fall 2026",
    ) = Team(
        id = teamId,
        organizationId = organizationId,
        name = "Thunder U12",
        sport = sport,
        season = season,
        status = TeamStatus.ACTIVE,
        contactEmail = null,
        createdAt = Instant.now(),
        updatedAt = Instant.now(),
    )

    private fun participant() =
        Participant(
            id = participantId,
            householdId = UUID.randomUUID(),
            organizationId = organizationId,
            firstName = "Jordan",
            lastName = "Smith",
            dateOfBirth = LocalDate.of(2014, 5, 12),
            notes = null,
            status = ParticipantStatus.ACTIVE,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
        )

    private fun requirement(
        mode: RequirementMode = RequirementMode.GUARDIAN_ESIGN_ACKNOWLEDGMENT,
        sport: String? = null,
        season: String? = null,
    ) = EligibilityRequirement(
        id = UUID.randomUUID(),
        organizationId = organizationId,
        requirementGroupId = UUID.randomUUID(),
        version = 1,
        title = "Season Waiver",
        mode = mode,
        content = "I agree...",
        contentHash = "abc123",
        sport = sport,
        program = null,
        season = season,
        teamId = null,
        guardianActionMode = GuardianActionMode.ANY_ONE_GUARDIAN,
        athleteSelfSignAllowed = false,
        externalEvidenceAccepted = false,
        acceptedEvidenceLevels = emptySet(),
        effectiveStart = LocalDate.of(2026, 1, 1),
        effectiveEnd = null,
        expirationRule = ExpirationRule.NEVER,
        expirationDays = null,
        status = RequirementStatus.ACTIVE,
        createdAt = Instant.now(),
        createdByUserId = user.userId,
    )

    private fun evidence(
        requirementId: UUID,
        classification: EvidenceClassification = EvidenceClassification.MANUALLY_VERIFIED,
        expiresAt: Instant? = null,
        reviewedByUserId: UUID? = UUID.randomUUID(),
    ) = EligibilityEvidence(
        id = UUID.randomUUID(),
        organizationId = organizationId,
        participantId = participantId,
        requirementId = requirementId,
        classification = classification,
        acceptanceMethod = AcceptanceMethod.ESIGN_LEGAL_NAME,
        signerUserId = UUID.randomUUID(),
        signerGuardianRelationship = "Parent",
        enteredLegalName = "Dana Smith",
        documentAssetId = null,
        provider = null,
        externalIdentifiers = null,
        contentHash = null,
        status = EvidenceStatus.ACTIVE,
        supersededByEvidenceId = null,
        acceptedAt = Instant.now(),
        expiresAt = expiresAt,
        ipAddress = null,
        userAgent = null,
        reviewedByUserId = reviewedByUserId,
        reviewNote = null,
        createdAt = Instant.now(),
        updatedAt = Instant.now(),
    )

    // --- createRequirement ---

    @Test
    fun `createRequirement requires manager role`() {
        every { membershipService.requireManagerRole(organizationId, user) } returns mockk<OrganizationMembership>()
        every {
            requirementRepository.insertFirstVersion(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
            )
        } answers {
            requirement()
        }
        every {
            auditService.record(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
            )
        } returns
            Unit

        service.createRequirement(
            organizationId,
            "Season Waiver",
            RequirementMode.GUARDIAN_ESIGN_ACKNOWLEDGMENT,
            "content",
            null,
            null,
            null,
            null,
            GuardianActionMode.ANY_ONE_GUARDIAN,
            false,
            false,
            emptySet(),
            LocalDate.of(2026, 1, 1),
            null,
            ExpirationRule.NEVER,
            null,
            user,
        )

        verify { membershipService.requireManagerRole(organizationId, user) }
    }

    @Test
    fun `createRequirement rejects a team that does not exist`() {
        every { membershipService.requireManagerRole(organizationId, user) } returns mockk<OrganizationMembership>()
        every { teamRepository.findById(teamId, organizationId) } returns null

        assertFailsWith<NotFoundException> {
            service.createRequirement(
                organizationId,
                "Season Waiver",
                RequirementMode.GUARDIAN_ESIGN_ACKNOWLEDGMENT,
                "content",
                null,
                null,
                null,
                teamId,
                GuardianActionMode.ANY_ONE_GUARDIAN,
                false,
                false,
                emptySet(),
                LocalDate.of(2026, 1, 1),
                null,
                ExpirationRule.NEVER,
                null,
                user,
            )
        }
    }

    @Test
    fun `createRequirement rejects FIXED_DAYS_FROM_ACCEPTANCE without expirationDays`() {
        every { membershipService.requireManagerRole(organizationId, user) } returns mockk<OrganizationMembership>()

        assertFailsWith<ValidationException> {
            service.createRequirement(
                organizationId,
                "Season Waiver",
                RequirementMode.GUARDIAN_ESIGN_ACKNOWLEDGMENT,
                "content",
                null,
                null,
                null,
                null,
                GuardianActionMode.ANY_ONE_GUARDIAN,
                false,
                false,
                emptySet(),
                LocalDate.of(2026, 1, 1),
                null,
                ExpirationRule.FIXED_DAYS_FROM_ACCEPTANCE,
                null,
                user,
            )
        }
    }

    // --- createNewVersion ---

    @Test
    fun `createNewVersion archives the previous version before inserting the next`() {
        val previous = requirement()
        every { membershipService.requireManagerRole(organizationId, user) } returns mockk<OrganizationMembership>()
        every { requirementRepository.findById(previous.id, organizationId) } returns previous
        val archivedId = slot<UUID>()
        every { requirementRepository.archive(capture(archivedId), organizationId) } returns 1
        every { requirementRepository.insertNewVersion(previous, "New title", "new content", any(), any(), any(), user.userId) } returns
            previous.copy(id = UUID.randomUUID(), version = 2, title = "New title", content = "new content")
        every {
            auditService.record(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
            )
        } returns
            Unit

        val next = service.createNewVersion(organizationId, previous.id, "New title", "new content", LocalDate.of(2026, 2, 1), null, user)

        assertEquals(previous.id, archivedId.captured)
        assertEquals(2, next.version)
        assertEquals(previous.requirementGroupId, next.requirementGroupId)
    }

    // --- recomputeClearance ---

    @Test
    fun `recomputeClearance clears a participant with no applicable requirements`() {
        every { teamRepository.findById(teamId, organizationId) } returns team()
        every { participantRepository.findById(participantId, organizationId) } returns participant()
        every { requirementRepository.listActiveForScope(organizationId, teamId) } returns emptyList()
        every { clearanceRepository.upsert(organizationId, participantId, teamId, ClearanceStatus.CLEARED, 0) } returns
            clearance(ClearanceStatus.CLEARED, 0)

        val result = service.recomputeClearance(organizationId, participantId, teamId)

        assertEquals(ClearanceStatus.CLEARED, result.status)
    }

    @Test
    fun `recomputeClearance clears a participant whose requirements are all satisfied`() {
        val req = requirement()
        every { teamRepository.findById(teamId, organizationId) } returns team()
        every { participantRepository.findById(participantId, organizationId) } returns participant()
        every { requirementRepository.listActiveForScope(organizationId, teamId) } returns listOf(req)
        every { evidenceRepository.findActiveForRequirement(participantId, req.id, organizationId) } returns evidence(req.id)
        every { clearanceRepository.upsert(organizationId, participantId, teamId, ClearanceStatus.CLEARED, 0) } returns
            clearance(ClearanceStatus.CLEARED, 0)

        val result = service.recomputeClearance(organizationId, participantId, teamId)

        assertEquals(ClearanceStatus.CLEARED, result.status)
    }

    @Test
    fun `recomputeClearance requires documents when evidence is entirely missing`() {
        val req = requirement()
        every { teamRepository.findById(teamId, organizationId) } returns team()
        every { participantRepository.findById(participantId, organizationId) } returns participant()
        every { requirementRepository.listActiveForScope(organizationId, teamId) } returns listOf(req)
        every { evidenceRepository.findActiveForRequirement(participantId, req.id, organizationId) } returns null
        every { clearanceRepository.upsert(organizationId, participantId, teamId, ClearanceStatus.DOCUMENTS_REQUIRED, 1) } returns
            clearance(ClearanceStatus.DOCUMENTS_REQUIRED, 1)

        val result = service.recomputeClearance(organizationId, participantId, teamId)

        assertEquals(ClearanceStatus.DOCUMENTS_REQUIRED, result.status)
        assertEquals(1, result.unmetRequirementCount)
    }

    @Test
    fun `recomputeClearance marks a participant ineligible on rejected evidence`() {
        val req = requirement()
        every { teamRepository.findById(teamId, organizationId) } returns team()
        every { participantRepository.findById(participantId, organizationId) } returns participant()
        every { requirementRepository.listActiveForScope(organizationId, teamId) } returns listOf(req)
        every { evidenceRepository.findActiveForRequirement(participantId, req.id, organizationId) } returns
            evidence(req.id, classification = EvidenceClassification.REJECTED)
        every { clearanceRepository.upsert(organizationId, participantId, teamId, ClearanceStatus.INELIGIBLE, 1) } returns
            clearance(ClearanceStatus.INELIGIBLE, 1)

        val result = service.recomputeClearance(organizationId, participantId, teamId)

        assertEquals(ClearanceStatus.INELIGIBLE, result.status)
    }

    @Test
    fun `recomputeClearance marks expired when the only unmet items are expired evidence`() {
        val req = requirement()
        every { teamRepository.findById(teamId, organizationId) } returns team()
        every { participantRepository.findById(participantId, organizationId) } returns participant()
        every { requirementRepository.listActiveForScope(organizationId, teamId) } returns listOf(req)
        every { evidenceRepository.findActiveForRequirement(participantId, req.id, organizationId) } returns
            evidence(req.id, expiresAt = Instant.now().minusSeconds(60))
        every { clearanceRepository.upsert(organizationId, participantId, teamId, ClearanceStatus.EXPIRED, 1) } returns
            clearance(ClearanceStatus.EXPIRED, 1)

        val result = service.recomputeClearance(organizationId, participantId, teamId)

        assertEquals(ClearanceStatus.EXPIRED, result.status)
    }

    @Test
    fun `recomputeClearance marks under review when staff-reviewed evidence has no reviewer yet`() {
        val req = requirement(mode = RequirementMode.STAFF_REVIEWED_EXTERNAL)
        every { teamRepository.findById(teamId, organizationId) } returns team()
        every { participantRepository.findById(participantId, organizationId) } returns participant()
        every { requirementRepository.listActiveForScope(organizationId, teamId) } returns listOf(req)
        every { evidenceRepository.findActiveForRequirement(participantId, req.id, organizationId) } returns
            evidence(req.id, reviewedByUserId = null)
        every { clearanceRepository.upsert(organizationId, participantId, teamId, ClearanceStatus.UNDER_REVIEW, 1) } returns
            clearance(ClearanceStatus.UNDER_REVIEW, 1)

        val result = service.recomputeClearance(organizationId, participantId, teamId)

        assertEquals(ClearanceStatus.UNDER_REVIEW, result.status)
    }

    @Test
    fun `recomputeClearance excludes requirements scoped to a different sport`() {
        val req = requirement(sport = "Basketball")
        every { teamRepository.findById(teamId, organizationId) } returns team(sport = "Soccer")
        every { participantRepository.findById(participantId, organizationId) } returns participant()
        every { requirementRepository.listActiveForScope(organizationId, teamId) } returns listOf(req)
        every { clearanceRepository.upsert(organizationId, participantId, teamId, ClearanceStatus.CLEARED, 0) } returns
            clearance(ClearanceStatus.CLEARED, 0)

        val result = service.recomputeClearance(organizationId, participantId, teamId)

        assertEquals(ClearanceStatus.CLEARED, result.status)
        verify(exactly = 0) { evidenceRepository.findActiveForRequirement(any(), req.id, any()) }
    }

    @Test
    fun `recomputeClearance fails for a team that does not exist`() {
        every { teamRepository.findById(teamId, organizationId) } returns null

        assertFailsWith<NotFoundException> {
            service.recomputeClearance(organizationId, participantId, teamId)
        }
    }

    // --- listRequirementsForParticipant (slice 31.2) ---

    @Test
    fun `listRequirementsForParticipant denies a caller with no guardian, athlete-self, or staff access`() {
        every { participantRepository.findById(participantId, organizationId) } returns participant()
        every { membershipService.hasManagerRole(organizationId, user) } returns false
        every { authorizationService.hasGuardianRelationship(organizationId, any(), user) } returns false
        every { authorizationService.hasParticipantCapability(user, participantId, Capabilities.ATHLETE_PROFILE_VIEW) } returns false

        assertFailsWith<ForbiddenException> {
            service.listRequirementsForParticipant(organizationId, participantId, user)
        }
    }

    @Test
    fun `listRequirementsForParticipant pairs applicable requirements with the participant's current evidence`() {
        val req = requirement()
        val assignment = teamAssignment()
        every { participantRepository.findById(participantId, organizationId) } returns participant()
        every { membershipService.hasManagerRole(organizationId, user) } returns true
        every { authorizationService.hasGuardianRelationship(organizationId, any(), user) } returns false
        every { authorizationService.hasParticipantCapability(user, participantId, Capabilities.ATHLETE_PROFILE_VIEW) } returns false
        every { participantRepository.listTeamAssignments(participantId, organizationId) } returns listOf(assignment)
        every { teamRepository.findById(teamId, organizationId) } returns team()
        every { requirementRepository.listActiveForScope(organizationId, teamId) } returns listOf(req)
        every { evidenceRepository.findActiveForRequirement(participantId, req.id, organizationId) } returns null

        val result = service.listRequirementsForParticipant(organizationId, participantId, user)

        assertEquals(1, result.size)
        assertEquals(req.id, result.single().first.id)
        assertEquals(null, result.single().second)
    }

    // --- submitGuardianEvidence (slice 31.2) ---

    @Test
    fun `submitGuardianEvidence rejects staff-only acceptance methods`() {
        assertFailsWith<ValidationException> {
            service.submitGuardianEvidence(
                organizationId,
                participantId,
                UUID.randomUUID(),
                AcceptanceMethod.STAFF_MANUAL_VERIFICATION,
                null,
                null,
                null,
                null,
                user,
            )
        }
    }

    @Test
    fun `submitGuardianEvidence denies a caller with no guardian or athlete-self relationship`() {
        val req = requirement()
        every { participantRepository.findById(participantId, organizationId) } returns participant()
        every { requirementRepository.findById(req.id, organizationId) } returns req
        every { authorizationService.hasGuardianRelationship(organizationId, any(), user) } returns false
        every { authorizationService.hasParticipantCapability(user, participantId, Capabilities.ATHLETE_PROFILE_UPDATE) } returns false

        assertFailsWith<ForbiddenException> {
            service.submitGuardianEvidence(
                organizationId,
                participantId,
                req.id,
                AcceptanceMethod.ESIGN_LEGAL_NAME,
                "Dana Smith",
                null,
                null,
                null,
                user,
            )
        }
    }

    @Test
    fun `submitGuardianEvidence rejects FILE_UPLOAD without a documentAssetId`() {
        val req = requirement()
        every { participantRepository.findById(participantId, organizationId) } returns participant()
        every { requirementRepository.findById(req.id, organizationId) } returns req
        every { authorizationService.hasGuardianRelationship(organizationId, any(), user) } returns true

        assertFailsWith<ValidationException> {
            service.submitGuardianEvidence(
                organizationId,
                participantId,
                req.id,
                AcceptanceMethod.FILE_UPLOAD,
                null,
                null,
                null,
                null,
                user,
            )
        }
    }

    @Test
    fun `submitGuardianEvidence records evidence and recomputes clearance for every active team assignment`() {
        val req = requirement()
        val assignment = teamAssignment()
        every { participantRepository.findById(participantId, organizationId) } returns participant()
        every { requirementRepository.findById(req.id, organizationId) } returns req
        every { authorizationService.hasGuardianRelationship(organizationId, any(), user) } returns true
        every { evidenceRepository.findActiveForRequirement(participantId, req.id, organizationId) } returns null
        every {
            evidenceRepository.insert(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
            )
        } returns evidence(req.id)
        every {
            auditService.record(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns Unit
        every { participantRepository.listTeamAssignments(participantId, organizationId) } returns listOf(assignment)
        every { teamRepository.findById(teamId, organizationId) } returns team()
        every { requirementRepository.listActiveForScope(organizationId, teamId) } returns listOf(req)
        every { clearanceRepository.upsert(organizationId, participantId, teamId, any(), any()) } returns
            clearance(ClearanceStatus.CLEARED, 0)

        val result =
            service.submitGuardianEvidence(
                organizationId,
                participantId,
                req.id,
                AcceptanceMethod.ESIGN_LEGAL_NAME,
                "Dana Smith",
                null,
                "203.0.113.5",
                "Mozilla/5.0",
                user,
            )

        assertEquals(req.id, result.requirementId)
        verify { clearanceRepository.upsert(organizationId, participantId, teamId, any(), any()) }
    }

    private fun teamAssignment() =
        ParticipantTeamAssignment(
            id = UUID.randomUUID(),
            participantId = participantId,
            teamId = teamId,
            organizationId = organizationId,
            status = "ACTIVE",
            joinedAt = LocalDate.of(2026, 1, 1),
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
        )

    private fun clearance(
        status: ClearanceStatus,
        unmetCount: Int,
    ) = EligibilityClearance(
        id = UUID.randomUUID(),
        organizationId = organizationId,
        participantId = participantId,
        teamId = teamId,
        status = status,
        unmetRequirementCount = unmetCount,
        computedAt = Instant.now(),
    )
}
