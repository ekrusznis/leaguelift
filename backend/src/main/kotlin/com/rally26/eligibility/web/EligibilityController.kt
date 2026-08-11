package com.rally26.eligibility.web

import com.rally26.common.web.CurrentUser
import com.rally26.common.web.resolveClientIp
import com.rally26.eligibility.application.EligibilityService
import com.rally26.eligibility.domain.ClearanceStatus
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/** Owner/admin-facing requirement-set management (Phase 31 slice 31.1, DESIGN-DOC.md section 14.1L §30.1). */
@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/eligibility/requirements")
class EligibilityRequirementController(
    private val service: EligibilityService,
) {
    @GetMapping
    fun list(
        @PathVariable organizationId: UUID,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ) = service.listRequirements(organizationId, currentUser).map { it.toResponse() }

    @PostMapping
    fun create(
        @PathVariable organizationId: UUID,
        @Valid @RequestBody request: CreateEligibilityRequirementRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<EligibilityRequirementResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(
            service
                .createRequirement(
                    organizationId = organizationId,
                    title = request.title,
                    mode = request.mode,
                    content = request.content,
                    sport = request.sport,
                    program = request.program,
                    season = request.season,
                    teamId = request.teamId,
                    guardianActionMode = request.guardianActionMode,
                    athleteSelfSignAllowed = request.athleteSelfSignAllowed,
                    externalEvidenceAccepted = request.externalEvidenceAccepted,
                    acceptedEvidenceLevels = request.acceptedEvidenceLevels,
                    effectiveStart = request.effectiveStart,
                    effectiveEnd = request.effectiveEnd,
                    expirationRule = request.expirationRule,
                    expirationDays = request.expirationDays,
                    currentUser = currentUser,
                ).toResponse(),
        )

    @PostMapping("/{requirementId}/versions")
    fun createVersion(
        @PathVariable organizationId: UUID,
        @PathVariable requirementId: UUID,
        @Valid @RequestBody request: CreateEligibilityRequirementVersionRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<EligibilityRequirementResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(
            service
                .createNewVersion(
                    organizationId = organizationId,
                    requirementId = requirementId,
                    title = request.title,
                    content = request.content,
                    effectiveStart = request.effectiveStart,
                    effectiveEnd = request.effectiveEnd,
                    currentUser = currentUser,
                ).toResponse(),
        )

    @PostMapping("/{requirementId}/archive")
    fun archive(
        @PathVariable organizationId: UUID,
        @PathVariable requirementId: UUID,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Void> {
        service.archiveRequirement(organizationId, requirementId, currentUser)
        return ResponseEntity.noContent().build()
    }
}

/** Coach-and-above-facing clearance visibility (Phase 31 slice 31.1, DESIGN-DOC.md section 14.1L §30.2 — operational status only, never evidence contents). */
@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/teams/{teamId}/eligibility")
class TeamEligibilityController(
    private val service: EligibilityService,
) {
    @GetMapping("/clearance")
    fun listForTeam(
        @PathVariable organizationId: UUID,
        @PathVariable teamId: UUID,
        @RequestParam(required = false) status: ClearanceStatus?,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ) = service.listClearanceForTeam(organizationId, teamId, status, currentUser).map { it.toResponse() }

    @GetMapping("/clearance/{participantId}")
    fun getForParticipant(
        @PathVariable organizationId: UUID,
        @PathVariable teamId: UUID,
        @PathVariable participantId: UUID,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): EligibilityClearanceResponse =
        service.getClearance(organizationId, participantId, teamId, currentUser)?.toResponse()
            ?: rosterPendingResponse(participantId, teamId)

    /**
     * Manual trigger — until slice 31.2+ wires automatic recomputation into evidence
     * submission and roster-assignment flows, this is the only way to (re)evaluate a
     * participant's clearance for a team. Same capability bar as viewing it: a coach
     * triggering a recompute can't see anything a GET wouldn't already show them.
     */
    @PostMapping("/clearance/{participantId}/recompute")
    fun recompute(
        @PathVariable organizationId: UUID,
        @PathVariable teamId: UUID,
        @PathVariable participantId: UUID,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): EligibilityClearanceResponse {
        service.getClearance(organizationId, participantId, teamId, currentUser) // capability check, discard result
        return service.recomputeClearance(organizationId, participantId, teamId).toResponse()
    }
}

/**
 * Guardian/athlete-self facing (Phase 31 slice 31.2, DESIGN-DOC.md section 14.1L
 * §30.3). File-upload evidence is a two-step flow: the caller first uploads through
 * the existing generic `POST /organizations/{organizationId}/media/uploads` (+
 * `/confirm`) with `usageSlot: DOCUMENT` and `entityType: PARTICIPANT`/`entityId` set
 * to this same participant (MediaEntityAccessService's PARTICIPANT resolver, widened
 * this slice to allow DOCUMENT), then passes the resulting `documentAssetId` here.
 */
@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/eligibility/participants/{participantId}")
class ParticipantEligibilityController(
    private val service: EligibilityService,
) {
    @GetMapping("/requirements")
    fun listRequirements(
        @PathVariable organizationId: UUID,
        @PathVariable participantId: UUID,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): List<ParticipantRequirementResponse> =
        service.listRequirementsForParticipant(organizationId, participantId, currentUser).map { (requirement, evidence) ->
            ParticipantRequirementResponse(requirement.toResponse(), evidence?.toResponse())
        }

    @PostMapping("/requirements/{requirementId}/evidence")
    fun submitEvidence(
        @PathVariable organizationId: UUID,
        @PathVariable participantId: UUID,
        @PathVariable requirementId: UUID,
        @Valid @RequestBody request: SubmitGuardianEvidenceRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
        httpRequest: HttpServletRequest,
    ): ResponseEntity<EligibilityEvidenceResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(
            service
                .submitGuardianEvidence(
                    organizationId = organizationId,
                    participantId = participantId,
                    requirementId = requirementId,
                    acceptanceMethod = request.acceptanceMethod,
                    enteredLegalName = request.enteredLegalName,
                    documentAssetId = request.documentAssetId,
                    ipAddress = resolveClientIp(httpRequest),
                    userAgent = httpRequest.getHeader("User-Agent"),
                    currentUser = currentUser,
                ).toResponse(),
        )
}
