package com.leaguelift.profilecorrection.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.leaguelift.audit.application.AuditService
import com.leaguelift.authorization.application.AuthorizationService
import com.leaguelift.authorization.domain.Capabilities
import com.leaguelift.common.error.ConflictException
import com.leaguelift.common.error.ForbiddenException
import com.leaguelift.common.error.NotFoundException
import com.leaguelift.common.error.ValidationException
import com.leaguelift.common.web.CurrentUser
import com.leaguelift.household.application.HouseholdService
import com.leaguelift.household.domain.AdultStatus
import com.leaguelift.household.persistence.HouseholdRepository
import com.leaguelift.membership.application.MembershipService
import com.leaguelift.participant.application.ParticipantService
import com.leaguelift.participant.domain.ParticipantStatus
import com.leaguelift.participant.persistence.ParticipantRepository
import com.leaguelift.profilecorrection.domain.ProfileCorrectionField
import com.leaguelift.profilecorrection.domain.ProfileCorrectionRequest
import com.leaguelift.profilecorrection.domain.ProfileCorrectionStatus
import com.leaguelift.profilecorrection.domain.ProfileCorrectionTargetType
import com.leaguelift.profilecorrection.persistence.ProfileCorrectionRepository
import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.util.UUID

@Service
class ProfileCorrectionService(
    private val repository: ProfileCorrectionRepository,
    private val householdRepository: HouseholdRepository,
    private val participantRepository: ParticipantRepository,
    private val householdService: HouseholdService,
    private val participantService: ParticipantService,
    private val membershipService: MembershipService,
    private val authorizationService: AuthorizationService,
    private val auditService: AuditService,
    private val objectMapper: ObjectMapper,
) {

    @Transactional
    fun create(
        organizationId: UUID,
        targetType: ProfileCorrectionTargetType,
        targetId: UUID,
        field: ProfileCorrectionField,
        proposedValue: String,
        reason: String,
        currentUser: CurrentUser,
    ): ProfileCorrectionRequest {
        if (field.targetType != targetType) {
            throw ValidationException("The selected field does not belong to the selected profile type.")
        }
        val normalizedReason = reason.trim()
        if (normalizedReason.length !in 5..500) {
            throw ValidationException("Explain the requested correction in 5 to 500 characters.")
        }
        val target = resolveTarget(organizationId, targetType, targetId)
        requireCanRequest(organizationId, target, currentUser)
        val normalizedValue = normalizeProposedValue(field, proposedValue)
        val currentValue = currentValue(target, field)
        if (valuesEqual(field, currentValue, normalizedValue)) {
            throw ValidationException("The proposed value is already on the profile.")
        }
        if (repository.hasPending(organizationId, targetType, targetId, field)) {
            throw ConflictException("CORRECTION_ALREADY_PENDING", "A correction for this field is already awaiting review.")
        }
        val request = try {
            repository.insert(
                organizationId = organizationId,
                householdId = target.householdId,
                targetType = targetType,
                targetId = targetId,
                field = field,
                targetLabel = target.label,
                currentValue = currentValue,
                proposedValue = normalizedValue,
                reason = normalizedReason,
                requestedBy = currentUser.userId,
            )
        } catch (_: DuplicateKeyException) {
            throw ConflictException("CORRECTION_ALREADY_PENDING", "A correction for this field is already awaiting review.")
        }
        auditService.record(
            currentUser.userId,
            organizationId,
            "profile_correction.requested",
            "profile_correction_request",
            request.id,
            objectMapper.writeValueAsString(mapOf("targetType" to targetType.name, "targetId" to targetId, "field" to field.name)),
        )
        return request
    }

    fun listForOrganization(
        organizationId: UUID,
        status: ProfileCorrectionStatus?,
        currentUser: CurrentUser,
        offset: Int,
        limit: Int,
    ): Pair<List<ProfileCorrectionRequest>, Long> {
        membershipService.requireManagerRole(organizationId, currentUser)
        return repository.listForOrganization(organizationId, status, offset, limit) to
            repository.countForOrganization(organizationId, status)
    }

    fun listForHousehold(
        organizationId: UUID,
        householdId: UUID,
        currentUser: CurrentUser,
    ): List<ProfileCorrectionRequest> {
        householdRepository.findById(householdId, organizationId)
            ?: throw NotFoundException("HOUSEHOLD_NOT_FOUND", "The household could not be found.")
        val manager = membershipService.hasManagerRole(organizationId, currentUser)
        if (!manager && !authorizationService.hasGuardianRelationship(organizationId, householdId, currentUser)) {
            throw ForbiddenException("CORRECTION_REQUEST_ACCESS_DENIED", "You do not have access to correction requests for this household.")
        }
        val requests = repository.listForHousehold(organizationId, householdId)
        if (manager) return requests
        return requests.filter { request ->
            request.targetType == ProfileCorrectionTargetType.PARTICIPANT ||
                authorizationService.hasGuardianAdultRelationship(organizationId, request.targetId, currentUser)
        }
    }

    @Transactional
    fun approve(
        organizationId: UUID,
        requestId: UUID,
        reviewNote: String?,
        currentUser: CurrentUser,
    ): ProfileCorrectionRequest {
        membershipService.requireManagerRole(organizationId, currentUser)
        val request = pendingRequest(organizationId, requestId)
        val target = resolveTarget(organizationId, request.targetType, request.targetId)
        val actualCurrentValue = currentValue(target, request.field)
        if (!valuesEqual(request.field, actualCurrentValue, request.currentValue)) {
            throw ConflictException(
                "CORRECTION_TARGET_CHANGED",
                "This profile changed after the request was submitted. Reject or withdraw the stale request and review the current profile.",
            )
        }
        applyCorrection(organizationId, request, target, currentUser)
        val updated = repository.review(
            request.id,
            organizationId,
            ProfileCorrectionStatus.APPROVED,
            currentUser.userId,
            normalizeOptionalNote(reviewNote),
        )
        if (updated == 0) throw ConflictException("CORRECTION_ALREADY_REVIEWED", "This correction request is no longer pending.")
        auditService.record(
            currentUser.userId,
            organizationId,
            "profile_correction.approved",
            "profile_correction_request",
            request.id,
            objectMapper.writeValueAsString(mapOf("targetType" to request.targetType.name, "targetId" to request.targetId, "field" to request.field.name)),
        )
        return repository.findById(request.id, organizationId)!!
    }

    @Transactional
    fun reject(
        organizationId: UUID,
        requestId: UUID,
        reviewNote: String,
        currentUser: CurrentUser,
    ): ProfileCorrectionRequest {
        membershipService.requireManagerRole(organizationId, currentUser)
        val request = pendingRequest(organizationId, requestId)
        val note = reviewNote.trim()
        if (note.length !in 3..500) throw ValidationException("A rejection note between 3 and 500 characters is required.")
        val updated = repository.review(
            request.id,
            organizationId,
            ProfileCorrectionStatus.REJECTED,
            currentUser.userId,
            note,
        )
        if (updated == 0) throw ConflictException("CORRECTION_ALREADY_REVIEWED", "This correction request is no longer pending.")
        auditService.record(
            currentUser.userId,
            organizationId,
            "profile_correction.rejected",
            "profile_correction_request",
            request.id,
            objectMapper.writeValueAsString(mapOf("field" to request.field.name)),
        )
        return repository.findById(request.id, organizationId)!!
    }

    @Transactional
    fun withdraw(organizationId: UUID, requestId: UUID, currentUser: CurrentUser) {
        val request = repository.findById(requestId, organizationId)
            ?: throw NotFoundException("CORRECTION_REQUEST_NOT_FOUND", "The correction request could not be found.")
        if (request.requestedBy != currentUser.userId && !membershipService.hasManagerRole(organizationId, currentUser)) {
            throw ForbiddenException("CORRECTION_WITHDRAW_DENIED", "Only the requester or an organization manager can withdraw this request.")
        }
        val updated = repository.withdraw(requestId, organizationId, request.requestedBy)
        if (updated == 0) throw ConflictException("CORRECTION_ALREADY_REVIEWED", "Only a pending correction request can be withdrawn.")
        auditService.record(
            currentUser.userId,
            organizationId,
            "profile_correction.withdrawn",
            "profile_correction_request",
            request.id,
            objectMapper.writeValueAsString(mapOf("field" to request.field.name)),
        )
    }

    private fun pendingRequest(organizationId: UUID, requestId: UUID): ProfileCorrectionRequest {
        val request = repository.findById(requestId, organizationId)
            ?: throw NotFoundException("CORRECTION_REQUEST_NOT_FOUND", "The correction request could not be found.")
        if (request.status != ProfileCorrectionStatus.PENDING) {
            throw ConflictException("CORRECTION_ALREADY_REVIEWED", "This correction request is no longer pending.")
        }
        return request
    }

    private fun resolveTarget(
        organizationId: UUID,
        targetType: ProfileCorrectionTargetType,
        targetId: UUID,
    ): CorrectionTarget = when (targetType) {
        ProfileCorrectionTargetType.HOUSEHOLD_ADULT -> {
            val adult = householdRepository.findAdultById(targetId, organizationId)
                ?.takeIf { it.status == AdultStatus.ACTIVE }
                ?: throw NotFoundException("ADULT_NOT_FOUND", "The household adult could not be found.")
            CorrectionTarget(
                householdId = adult.householdId,
                label = "${adult.firstName} ${adult.lastName}".trim(),
                adult = adult,
            )
        }
        ProfileCorrectionTargetType.PARTICIPANT -> {
            val participant = participantRepository.findById(targetId, organizationId)
                ?.takeIf { it.status == ParticipantStatus.ACTIVE }
                ?: throw NotFoundException("PARTICIPANT_NOT_FOUND", "The active participant could not be found.")
            CorrectionTarget(
                householdId = participant.householdId,
                label = "${participant.firstName} ${participant.lastName}".trim(),
                participant = participant,
            )
        }
    }

    private fun requireCanRequest(organizationId: UUID, target: CorrectionTarget, currentUser: CurrentUser) {
        if (membershipService.hasManagerRole(organizationId, currentUser)) return
        if (target.adult != null && authorizationService.hasGuardianAdultRelationship(organizationId, target.adult.id, currentUser)) return
        if (target.participant != null) {
            if (authorizationService.hasGuardianRelationship(organizationId, target.householdId, currentUser)) return
            if (authorizationService.hasParticipantCapability(currentUser, target.participant.id, Capabilities.ATHLETE_PROFILE_UPDATE)) return
            val canManageRoster = participantRepository.listTeamAssignments(target.participant.id, organizationId).any { assignment ->
                authorizationService.hasTeamCapability(
                    organizationId,
                    assignment.teamId,
                    currentUser,
                    Capabilities.TEAM_ROSTER_MANAGE,
                )
            }
            if (canManageRoster) return
        }
        throw ForbiddenException("CORRECTION_REQUEST_DENIED", "You cannot request a correction for this profile.")
    }

    private fun currentValue(target: CorrectionTarget, field: ProfileCorrectionField): String? = when (field) {
        ProfileCorrectionField.ADULT_FIRST_NAME -> target.adult!!.firstName
        ProfileCorrectionField.ADULT_LAST_NAME -> target.adult!!.lastName
        ProfileCorrectionField.ADULT_EMAIL -> target.adult!!.email
        ProfileCorrectionField.ADULT_PHONE -> target.adult!!.phone
        ProfileCorrectionField.ADULT_RELATIONSHIP -> target.adult!!.relationship
        ProfileCorrectionField.PARTICIPANT_FIRST_NAME -> target.participant!!.firstName
        ProfileCorrectionField.PARTICIPANT_LAST_NAME -> target.participant!!.lastName
        ProfileCorrectionField.PARTICIPANT_DATE_OF_BIRTH -> target.participant!!.dateOfBirth?.toString()
    }

    private fun normalizeProposedValue(field: ProfileCorrectionField, raw: String): String {
        val value = raw.trim()
        return when (field) {
            ProfileCorrectionField.ADULT_FIRST_NAME,
            ProfileCorrectionField.ADULT_LAST_NAME,
            ProfileCorrectionField.PARTICIPANT_FIRST_NAME,
            ProfileCorrectionField.PARTICIPANT_LAST_NAME -> {
                if (value.length !in 1..100) throw ValidationException("Names must contain 1 to 100 characters.")
                value
            }
            ProfileCorrectionField.ADULT_EMAIL -> {
                val normalized = value.lowercase()
                if (normalized.length !in 3..254 || !EMAIL_PATTERN.matches(normalized)) {
                    throw ValidationException("Enter a valid email address.")
                }
                normalized
            }
            ProfileCorrectionField.ADULT_PHONE -> {
                if (value.length !in 7..40) throw ValidationException("Phone numbers must contain 7 to 40 characters.")
                value
            }
            ProfileCorrectionField.ADULT_RELATIONSHIP -> {
                if (value.length !in 1..100) throw ValidationException("Relationship must contain 1 to 100 characters.")
                value
            }
            ProfileCorrectionField.PARTICIPANT_DATE_OF_BIRTH -> try {
                LocalDate.parse(value).toString()
            } catch (_: DateTimeParseException) {
                throw ValidationException("Date of birth must use YYYY-MM-DD format.")
            }
        }
    }

    private fun valuesEqual(field: ProfileCorrectionField, left: String?, right: String?): Boolean = when (field) {
        ProfileCorrectionField.ADULT_EMAIL -> left?.trim()?.lowercase() == right?.trim()?.lowercase()
        else -> left?.trim() == right?.trim()
    }

    private fun applyCorrection(
        organizationId: UUID,
        request: ProfileCorrectionRequest,
        target: CorrectionTarget,
        currentUser: CurrentUser,
    ) {
        when (request.field) {
            ProfileCorrectionField.ADULT_FIRST_NAME -> householdService.updateAdult(
                organizationId, target.householdId, request.targetId,
                firstName = request.proposedValue, lastName = null, email = null, phone = null, relationship = null,
                currentUser = currentUser,
            )
            ProfileCorrectionField.ADULT_LAST_NAME -> householdService.updateAdult(
                organizationId, target.householdId, request.targetId,
                firstName = null, lastName = request.proposedValue, email = null, phone = null, relationship = null,
                currentUser = currentUser,
            )
            ProfileCorrectionField.ADULT_EMAIL -> householdService.updateAdult(
                organizationId, target.householdId, request.targetId,
                firstName = null, lastName = null, email = request.proposedValue, phone = null, relationship = null,
                currentUser = currentUser,
            )
            ProfileCorrectionField.ADULT_PHONE -> householdService.updateAdult(
                organizationId, target.householdId, request.targetId,
                firstName = null, lastName = null, email = null, phone = request.proposedValue, relationship = null,
                currentUser = currentUser,
            )
            ProfileCorrectionField.ADULT_RELATIONSHIP -> householdService.updateAdult(
                organizationId, target.householdId, request.targetId,
                firstName = null, lastName = null, email = null, phone = null, relationship = request.proposedValue,
                currentUser = currentUser,
            )
            ProfileCorrectionField.PARTICIPANT_FIRST_NAME -> participantService.update(
                organizationId, request.targetId, request.proposedValue, null, null, null, currentUser,
            )
            ProfileCorrectionField.PARTICIPANT_LAST_NAME -> participantService.update(
                organizationId, request.targetId, null, request.proposedValue, null, null, currentUser,
            )
            ProfileCorrectionField.PARTICIPANT_DATE_OF_BIRTH -> participantService.update(
                organizationId, request.targetId, null, null, LocalDate.parse(request.proposedValue), null, currentUser,
            )
        }
    }

    private fun normalizeOptionalNote(note: String?): String? {
        val normalized = note?.trim()?.takeIf { it.isNotEmpty() }
        if (normalized != null && normalized.length > 500) throw ValidationException("Review notes cannot exceed 500 characters.")
        return normalized
    }

    private data class CorrectionTarget(
        val householdId: UUID,
        val label: String,
        val adult: com.leaguelift.household.domain.HouseholdAdult? = null,
        val participant: com.leaguelift.participant.domain.Participant? = null,
    )

    private companion object {
        val EMAIL_PATTERN = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
    }
}
