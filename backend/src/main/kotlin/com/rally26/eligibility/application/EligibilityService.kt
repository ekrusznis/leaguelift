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
import com.rally26.eligibility.persistence.EligibilityClearanceRepository
import com.rally26.eligibility.persistence.EligibilityEvidenceRepository
import com.rally26.eligibility.persistence.EligibilityRequirementRepository
import com.rally26.membership.application.MembershipService
import com.rally26.participant.persistence.ParticipantRepository
import com.rally26.team.persistence.TeamRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Phase 31 slice 31.1 (DESIGN-DOC.md section 14.1L) — requirement-set definition and
 * deterministic clearance computation. Deliberately does not yet include: native
 * guardian e-sign/upload submission (slice 31.2, which will be what actually calls
 * [recordEvidence] for real from a guardian-facing endpoint), external provider
 * evidence import (slice 31.3), or a hook into [com.rally26.participant.persistence.ParticipantRepository.ensureTeamAssignment]'s
 * existing call sites (CSV/bulk onboarding import) — that wiring belongs with whichever
 * slice first gives a guardian something to actually do about a newly-created
 * DOCUMENTS_REQUIRED assignment, not this one.
 */
@Service
class EligibilityService(
    private val requirementRepository: EligibilityRequirementRepository,
    private val evidenceRepository: EligibilityEvidenceRepository,
    private val clearanceRepository: EligibilityClearanceRepository,
    private val teamRepository: TeamRepository,
    private val participantRepository: ParticipantRepository,
    private val membershipService: MembershipService,
    private val authorizationService: AuthorizationService,
    private val auditService: AuditService,
) {
    @Transactional
    fun createRequirement(
        organizationId: UUID,
        title: String,
        mode: RequirementMode,
        content: String,
        sport: String?,
        program: String?,
        season: String?,
        teamId: UUID?,
        guardianActionMode: GuardianActionMode,
        athleteSelfSignAllowed: Boolean,
        externalEvidenceAccepted: Boolean,
        acceptedEvidenceLevels: Set<EvidenceClassification>,
        effectiveStart: LocalDate,
        effectiveEnd: LocalDate?,
        expirationRule: ExpirationRule,
        expirationDays: Int?,
        currentUser: CurrentUser,
    ): EligibilityRequirement {
        membershipService.requireManagerRole(organizationId, currentUser)
        if (teamId != null && teamRepository.findById(teamId, organizationId) == null) {
            throw NotFoundException("TEAM_NOT_FOUND", "The team could not be found.")
        }
        if (expirationRule == ExpirationRule.FIXED_DAYS_FROM_ACCEPTANCE && (expirationDays == null || expirationDays <= 0)) {
            throw ValidationException("expirationDays must be a positive number when expirationRule is FIXED_DAYS_FROM_ACCEPTANCE.")
        }
        if (effectiveEnd != null && effectiveEnd.isBefore(effectiveStart)) {
            throw ValidationException("effectiveEnd cannot be before effectiveStart.")
        }
        val requirement =
            requirementRepository.insertFirstVersion(
                organizationId = organizationId,
                title = title,
                mode = mode,
                content = content,
                contentHash = sha256Hex(content),
                sport = sport,
                program = program,
                season = season,
                teamId = teamId,
                guardianActionMode = guardianActionMode,
                athleteSelfSignAllowed = athleteSelfSignAllowed,
                externalEvidenceAccepted = externalEvidenceAccepted,
                acceptedEvidenceLevels = acceptedEvidenceLevels,
                effectiveStart = effectiveStart,
                effectiveEnd = effectiveEnd,
                expirationRule = expirationRule,
                expirationDays = expirationDays,
                createdByUserId = currentUser.userId,
            )
        auditService.record(
            currentUser.userId,
            organizationId,
            "eligibility.requirement.created",
            "eligibility_requirement",
            requirement.id,
        )
        return requirement
    }

    /**
     * A new version of an existing requirement family. Per DESIGN-DOC.md 14.1L §30.1,
     * "material document-language changes create a new version and require
     * re-acceptance; editing a future version never rewrites prior signatures or
     * historical clearance" — this never updates [requirementId]'s row, it archives it
     * and inserts a new one sharing the same requirement_group_id.
     */
    @Transactional
    fun createNewVersion(
        organizationId: UUID,
        requirementId: UUID,
        title: String,
        content: String,
        effectiveStart: LocalDate,
        effectiveEnd: LocalDate?,
        currentUser: CurrentUser,
    ): EligibilityRequirement {
        membershipService.requireManagerRole(organizationId, currentUser)
        val previous =
            requirementRepository.findById(requirementId, organizationId)
                ?: throw NotFoundException("REQUIREMENT_NOT_FOUND", "The requirement could not be found.")
        if (effectiveEnd != null && effectiveEnd.isBefore(effectiveStart)) {
            throw ValidationException("effectiveEnd cannot be before effectiveStart.")
        }
        requirementRepository.archive(previous.id, organizationId)
        val next =
            requirementRepository.insertNewVersion(
                previous = previous,
                title = title,
                content = content,
                contentHash = sha256Hex(content),
                effectiveStart = effectiveStart,
                effectiveEnd = effectiveEnd,
                createdByUserId = currentUser.userId,
            )
        auditService.record(
            currentUser.userId,
            organizationId,
            "eligibility.requirement.versioned",
            "eligibility_requirement",
            next.id,
        )
        return next
    }

    @Transactional
    fun archiveRequirement(
        organizationId: UUID,
        requirementId: UUID,
        currentUser: CurrentUser,
    ) {
        membershipService.requireManagerRole(organizationId, currentUser)
        val requirement =
            requirementRepository.findById(requirementId, organizationId)
                ?: throw NotFoundException("REQUIREMENT_NOT_FOUND", "The requirement could not be found.")
        requirementRepository.archive(requirement.id, organizationId)
        auditService.record(
            currentUser.userId,
            organizationId,
            "eligibility.requirement.archived",
            "eligibility_requirement",
            requirement.id,
        )
    }

    fun listRequirements(
        organizationId: UUID,
        currentUser: CurrentUser,
    ): List<EligibilityRequirement> {
        membershipService.requireManagerRole(organizationId, currentUser)
        return requirementRepository.listActiveForOrganization(organizationId)
    }

    /**
     * Records one accepted/rejected/manually-verified/externally-imported evidence
     * instance. Not exposed via any controller endpoint yet — slice 31.2's guardian
     * e-sign/upload flow and slice 31.3's provider adapters are the real callers; this
     * exists now so recomputation has something real to test against.
     */
    @Transactional
    fun recordEvidence(
        organizationId: UUID,
        participantId: UUID,
        requirementId: UUID,
        classification: EvidenceClassification,
        acceptanceMethod: AcceptanceMethod,
        signerUserId: UUID?,
        signerGuardianRelationship: String?,
        enteredLegalName: String?,
        documentAssetId: UUID?,
        provider: String?,
        externalIdentifiersJson: String?,
        contentHash: String?,
        expiresAt: Instant?,
        ipAddress: String?,
        userAgent: String?,
        reviewedByUserId: UUID?,
        reviewNote: String?,
        currentUser: CurrentUser,
    ): EligibilityEvidence {
        val requirement =
            requirementRepository.findById(requirementId, organizationId)
                ?: throw NotFoundException("REQUIREMENT_NOT_FOUND", "The requirement could not be found.")
        // Superseding, not overwriting — the prior ACTIVE row (if any) for this exact
        // participant/requirement pair is marked SUPERSEDED and linked forward, never
        // deleted or mutated in place (14.1L §30.3: "Original evidence is append-only").
        evidenceRepository.findActiveForRequirement(participantId, requirementId, organizationId)?.let { previous ->
            evidenceRepository.updateStatus(previous.id, organizationId, EvidenceStatus.SUPERSEDED, null)
        }
        val evidence =
            evidenceRepository.insert(
                organizationId = organizationId,
                participantId = participantId,
                requirementId = requirement.id,
                classification = classification,
                acceptanceMethod = acceptanceMethod,
                signerUserId = signerUserId,
                signerGuardianRelationship = signerGuardianRelationship,
                enteredLegalName = enteredLegalName,
                documentAssetId = documentAssetId,
                provider = provider,
                externalIdentifiersJson = externalIdentifiersJson,
                contentHash = contentHash,
                expiresAt = expiresAt,
                ipAddress = ipAddress,
                userAgent = userAgent,
                reviewedByUserId = reviewedByUserId,
                reviewNote = reviewNote,
            )
        auditService.record(
            currentUser.userId,
            organizationId,
            "eligibility.evidence.recorded",
            "eligibility_evidence",
            evidence.id,
            participantId = participantId,
        )
        return evidence
    }

    /**
     * The deterministic recomputation acceptance criterion #8 requires. Absence of a
     * stored [EligibilityClearance] row means ROSTER_PENDING (not-yet-evaluated) —
     * every OTHER status is always the freshly-computed output of this method, never a
     * hand-set field, so this table can never drift out of sync with the real
     * evidence/requirement ledger it's derived from.
     */
    @Transactional
    fun recomputeClearance(
        organizationId: UUID,
        participantId: UUID,
        teamId: UUID,
    ): EligibilityClearance {
        val team =
            teamRepository.findById(teamId, organizationId)
                ?: throw NotFoundException("TEAM_NOT_FOUND", "The team could not be found.")
        if (participantRepository.findById(participantId, organizationId) == null) {
            throw NotFoundException("PARTICIPANT_NOT_FOUND", "The participant could not be found.")
        }
        val applicable =
            requirementRepository
                .listActiveForScope(organizationId, teamId)
                .filter { it.sport == null || it.sport.equals(team.sport.name, ignoreCase = true) }
                .filter { it.season == null || it.season.equals(team.season, ignoreCase = true) }

        if (applicable.isEmpty()) {
            return clearanceRepository.upsert(organizationId, participantId, teamId, ClearanceStatus.CLEARED, 0)
        }

        val now = Instant.now()
        var unmet = 0
        var missingEntirely = 0
        var expiredOnly = 0
        var rejectedOrRevoked = 0
        var pendingReview = 0

        for (requirement in applicable) {
            val evidence = evidenceRepository.findActiveForRequirement(participantId, requirement.id, organizationId)
            when {
                evidence == null -> {
                    unmet++
                    missingEntirely++
                }

                evidence.classification == EvidenceClassification.REJECTED || evidence.classification == EvidenceClassification.REVOKED -> {
                    unmet++
                    rejectedOrRevoked++
                }

                evidence.expiresAt != null && evidence.expiresAt.isBefore(now) -> {
                    unmet++
                    expiredOnly++
                }

                requirement.mode == RequirementMode.STAFF_REVIEWED_EXTERNAL && evidence.reviewedByUserId == null -> {
                    unmet++
                    pendingReview++
                }

                else -> Unit // satisfied
            }
        }

        val status =
            when {
                unmet == 0 -> ClearanceStatus.CLEARED
                rejectedOrRevoked > 0 -> ClearanceStatus.INELIGIBLE
                pendingReview > 0 -> ClearanceStatus.UNDER_REVIEW
                missingEntirely == 0 && expiredOnly > 0 -> ClearanceStatus.EXPIRED
                else -> ClearanceStatus.DOCUMENTS_REQUIRED
            }

        return clearanceRepository.upsert(organizationId, participantId, teamId, status, unmet)
    }

    fun getClearance(
        organizationId: UUID,
        participantId: UUID,
        teamId: UUID,
        currentUser: CurrentUser,
    ): EligibilityClearance? {
        authorizationService.requireTeamCapability(organizationId, teamId, currentUser, Capabilities.TEAM_ELIGIBILITY_VIEW)
        return clearanceRepository.find(participantId, teamId, organizationId)
    }

    /** Coach roster view — operational status only for every athlete on the team, never evidence contents. [statusFilter] lets a coach view e.g. just INELIGIBLE players. */
    fun listClearanceForTeam(
        organizationId: UUID,
        teamId: UUID,
        statusFilter: ClearanceStatus?,
        currentUser: CurrentUser,
    ): List<EligibilityClearance> {
        authorizationService.requireTeamCapability(organizationId, teamId, currentUser, Capabilities.TEAM_ELIGIBILITY_VIEW)
        return clearanceRepository.listForTeam(teamId, organizationId, statusFilter)
    }

    /**
     * Guardian- (or staff-) facing "eligibility at a glance" for a whole household —
     * unlike [listClearanceForTeam], a household's participants aren't scoped to one
     * team, so this fans out to every team each participant is actually rostered on.
     * Same operational-status-only posture as the coach roster view, just reached from
     * the household side instead.
     */
    fun listClearanceForHousehold(
        organizationId: UUID,
        householdId: UUID,
        currentUser: CurrentUser,
    ): List<EligibilityClearance> {
        val staffAccess = membershipService.hasManagerRole(organizationId, currentUser)
        val guardianAccess = authorizationService.hasGuardianRelationship(organizationId, householdId, currentUser)
        if (!staffAccess && !guardianAccess) {
            throw ForbiddenException("ELIGIBILITY_ACCESS_DENIED", "You do not have access to this household's eligibility records.")
        }
        val participantIds = participantRepository.findByHousehold(householdId, organizationId).map { it.id }
        return clearanceRepository.listForParticipants(participantIds, organizationId)
    }

    // --- Guardian/athlete-self facing (slice 31.2) -----------------------------------

    private fun requireParticipantAccess(
        organizationId: UUID,
        participantId: UUID,
        currentUser: CurrentUser,
    ) {
        val participant =
            participantRepository.findById(participantId, organizationId)
                ?: throw NotFoundException("PARTICIPANT_NOT_FOUND", "The participant could not be found.")
        val staffAccess = membershipService.hasManagerRole(organizationId, currentUser)
        val guardianAccess = authorizationService.hasGuardianRelationship(organizationId, participant.householdId, currentUser)
        val athleteAccess =
            authorizationService.hasParticipantCapability(currentUser, participantId, Capabilities.ATHLETE_PROFILE_VIEW)
        if (!staffAccess && !guardianAccess && !athleteAccess) {
            throw ForbiddenException("ELIGIBILITY_ACCESS_DENIED", "You do not have access to this participant's eligibility records.")
        }
    }

    /**
     * Every requirement applicable to any of [participantId]'s currently active team
     * assignments, paired with the current evidence status (or null — no evidence
     * submitted yet). This is a guardian/athlete-self/staff read, never staff-only, so
     * a guardian can actually see what's outstanding for their own athlete.
     */
    fun listRequirementsForParticipant(
        organizationId: UUID,
        participantId: UUID,
        currentUser: CurrentUser,
    ): List<Pair<EligibilityRequirement, EligibilityEvidence?>> {
        requireParticipantAccess(organizationId, participantId, currentUser)
        val teamIds = participantRepository.listTeamAssignments(participantId, organizationId).map { it.teamId }.toSet()
        val teams = teamIds.mapNotNull { teamRepository.findById(it, organizationId) }
        val applicable =
            teams
                .flatMap { team ->
                    requirementRepository
                        .listActiveForScope(organizationId, team.id)
                        .filter { it.sport == null || it.sport.equals(team.sport.name, ignoreCase = true) }
                        .filter { it.season == null || it.season.equals(team.season, ignoreCase = true) }
                }.distinctBy { it.requirementGroupId }
        return applicable.map { requirement ->
            requirement to
                evidenceRepository.findActiveForRequirement(participantId, requirement.id, organizationId)
        }
    }

    /**
     * A guardian's own e-sign acknowledgment/legal-name-signature submission, or a
     * guardian/athlete-self file upload (the [documentAssetId] must already be a READY
     * asset uploaded through the existing media pipeline — see
     * MediaEntityAccessService's PARTICIPANT+DOCUMENT slot, widened for this slice).
     * Staff-reviewed external evidence and provider-imported evidence go through
     * [recordEvidence] directly instead — this method is deliberately guardian/athlete-
     * only, never callable with STAFF_MANUAL_VERIFICATION or EXTERNAL_IMPORT.
     */
    @Transactional
    fun submitGuardianEvidence(
        organizationId: UUID,
        participantId: UUID,
        requirementId: UUID,
        acceptanceMethod: AcceptanceMethod,
        enteredLegalName: String?,
        documentAssetId: UUID?,
        ipAddress: String?,
        userAgent: String?,
        currentUser: CurrentUser,
    ): EligibilityEvidence {
        if (acceptanceMethod == AcceptanceMethod.STAFF_MANUAL_VERIFICATION || acceptanceMethod == AcceptanceMethod.EXTERNAL_IMPORT) {
            throw ValidationException("Guardians cannot submit $acceptanceMethod evidence directly.")
        }
        val participant =
            participantRepository.findById(participantId, organizationId)
                ?: throw NotFoundException("PARTICIPANT_NOT_FOUND", "The participant could not be found.")
        val requirement =
            requirementRepository.findById(requirementId, organizationId)
                ?: throw NotFoundException("REQUIREMENT_NOT_FOUND", "The requirement could not be found.")
        val guardianAccess = authorizationService.hasGuardianRelationship(organizationId, participant.householdId, currentUser)
        val athleteAccess =
            requirement.athleteSelfSignAllowed &&
                authorizationService.hasParticipantCapability(currentUser, participantId, Capabilities.ATHLETE_PROFILE_UPDATE)
        if (!guardianAccess && !athleteAccess) {
            throw ForbiddenException("ELIGIBILITY_SUBMISSION_DENIED", "You cannot submit evidence for this participant.")
        }
        if (acceptanceMethod == AcceptanceMethod.FILE_UPLOAD && documentAssetId == null) {
            throw ValidationException("documentAssetId is required for a FILE_UPLOAD submission.")
        }
        val expiresAt =
            when (requirement.expirationRule) {
                ExpirationRule.FIXED_DAYS_FROM_ACCEPTANCE ->
                    Instant.now().plusSeconds((requirement.expirationDays ?: 0) * 86_400L)
                // SEASON_END/ORG_CONFIGURED resolution is a later slice's concern once a
                // season-calendar concept exists to resolve against.
                else -> null
            }
        val evidence =
            recordEvidence(
                organizationId = organizationId,
                participantId = participantId,
                requirementId = requirementId,
                // §30.4's classification tiers (VERIFIED_EXTERNAL_DOCUMENT etc.) are
                // scoped for *imported* evidence strength — a native guardian
                // e-sign/upload is the strongest form Rally26 has (a legally binding
                // action taken directly in-app), so MANUALLY_VERIFIED is the closest
                // fit rather than adding a redundant NATIVE_* tier this slice.
                classification = EvidenceClassification.MANUALLY_VERIFIED,
                acceptanceMethod = acceptanceMethod,
                signerUserId = currentUser.userId,
                signerGuardianRelationship = if (guardianAccess) "Guardian" else "Athlete (self)",
                enteredLegalName = enteredLegalName,
                documentAssetId = documentAssetId,
                provider = null,
                externalIdentifiersJson = null,
                contentHash = null,
                expiresAt = expiresAt,
                ipAddress = ipAddress,
                userAgent = userAgent,
                reviewedByUserId = null,
                reviewNote = null,
                currentUser = currentUser,
            )
        // Recompute every active team assignment this requirement could affect, not
        // just one — a household-wide/org-wide requirement can satisfy the same
        // participant's clearance on more than one team at once.
        participantRepository.listTeamAssignments(participantId, organizationId).forEach { assignment ->
            recomputeClearance(organizationId, participantId, assignment.teamId)
        }
        return evidence
    }

    private fun sha256Hex(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
