package com.rally26.media.application

import com.rally26.authorization.application.AuthorizationService
import com.rally26.authorization.domain.Capabilities
import com.rally26.common.error.ForbiddenException
import com.rally26.common.error.NotFoundException
import com.rally26.common.error.ValidationException
import com.rally26.common.web.CurrentUser
import com.rally26.household.domain.AdultStatus
import com.rally26.household.persistence.HouseholdRepository
import com.rally26.media.domain.MediaEntityType
import com.rally26.media.domain.MediaUsageSlot
import com.rally26.media.domain.Visibility
import com.rally26.membership.application.MembershipService
import com.rally26.participant.domain.ParticipantStatus
import com.rally26.participant.persistence.ParticipantRepository
import com.rally26.publicpage.domain.PageStatus
import com.rally26.publicpage.domain.PageType
import com.rally26.publicpage.persistence.PublicPageRepository
import com.rally26.team.domain.TeamStatus
import com.rally26.team.persistence.TeamRepository
import com.rally26.tournament.domain.TournamentStatus
import com.rally26.tournament.persistence.TournamentRepository
import org.springframework.stereotype.Service
import java.util.UUID

data class ResolvedMediaTarget(
    val organizationId: UUID,
    val entityType: MediaEntityType,
    val entityId: UUID,
    val allowedSlots: Set<MediaUsageSlot>,
    val visibility: Visibility,
)

/**
 * Centralizes privacy and resource-scope checks for Phase 16 branding. The media
 * assignment table has no foreign key to each polymorphic entity, so every API path
 * must resolve the target through its owning repository before reading or mutating an
 * assignment. Guardians and athlete-self accounts are checked through exact
 * relationships, never through the broader "any organization member" household helper.
 */
@Service
class MediaEntityAccessService(
    private val membershipService: MembershipService,
    private val authorizationService: AuthorizationService,
    private val teamRepository: TeamRepository,
    private val tournamentRepository: TournamentRepository,
    private val householdRepository: HouseholdRepository,
    private val participantRepository: ParticipantRepository,
    private val publicPageRepository: PublicPageRepository,
) {
    fun resolveForRead(
        organizationId: UUID,
        entityType: MediaEntityType,
        entityId: UUID,
        currentUser: CurrentUser,
    ): ResolvedMediaTarget = resolve(organizationId, entityType, entityId, currentUser, manage = false)

    fun resolveForManage(
        organizationId: UUID,
        entityType: MediaEntityType,
        entityId: UUID,
        currentUser: CurrentUser,
    ): ResolvedMediaTarget = resolve(organizationId, entityType, entityId, currentUser, manage = true)

    fun requireAllowedSlot(
        target: ResolvedMediaTarget,
        usageSlot: MediaUsageSlot,
    ) {
        if (usageSlot !in target.allowedSlots) {
            throw ValidationException(
                "${usageSlot.name} is not a valid media slot for ${target.entityType.name}.",
            )
        }
    }

    private fun resolve(
        organizationId: UUID,
        entityType: MediaEntityType,
        entityId: UUID,
        currentUser: CurrentUser,
        manage: Boolean,
    ): ResolvedMediaTarget =
        when (entityType) {
            MediaEntityType.ORGANIZATION -> {
                if (entityId != organizationId) {
                    throw NotFoundException("MEDIA_TARGET_NOT_FOUND", "The media target could not be found.")
                }
                if (manage) {
                    membershipService.requireManagerRole(organizationId, currentUser)
                } else {
                    membershipService.requireActiveMembership(organizationId, currentUser)
                }
                ResolvedMediaTarget(
                    organizationId,
                    entityType,
                    entityId,
                    setOf(MediaUsageSlot.LOGO, MediaUsageSlot.COVER),
                    publishedPageVisibility(entityId, PageType.ORGANIZATION, Visibility.ORGANIZATION_PRIVATE),
                )
            }

            MediaEntityType.TEAM -> {
                val team =
                    teamRepository.findById(entityId, organizationId)
                        ?: throw NotFoundException("TEAM_NOT_FOUND", "The team could not be found.")
                if (manage) {
                    if (team.status != TeamStatus.ACTIVE) throw ValidationException("An archived team cannot be rebranded.")
                    authorizationService.requireTeamCapability(organizationId, entityId, currentUser, Capabilities.TEAM_PAGE_EDIT)
                } else {
                    authorizationService.requireTeamCapability(organizationId, entityId, currentUser, Capabilities.TEAM_VIEW)
                }
                ResolvedMediaTarget(
                    organizationId,
                    entityType,
                    entityId,
                    setOf(MediaUsageSlot.LOGO, MediaUsageSlot.COVER),
                    publishedPageVisibility(entityId, PageType.TEAM, Visibility.TEAM_PRIVATE),
                )
            }

            MediaEntityType.TOURNAMENT -> {
                val tournament =
                    tournamentRepository.findById(entityId, organizationId)
                        ?: throw NotFoundException("TOURNAMENT_NOT_FOUND", "The tournament could not be found.")
                if (manage) {
                    if (tournament.status !=
                        TournamentStatus.ACTIVE
                    ) {
                        throw ValidationException("An archived tournament cannot be rebranded.")
                    }
                    authorizationService.requireTournamentCapability(
                        organizationId,
                        entityId,
                        currentUser,
                        Capabilities.TOURNAMENT_PAGE_EDIT,
                    )
                } else {
                    authorizationService.requireTournamentCapability(
                        organizationId,
                        entityId,
                        currentUser,
                        Capabilities.TOURNAMENT_VIEW,
                    )
                }
                ResolvedMediaTarget(
                    organizationId,
                    entityType,
                    entityId,
                    setOf(MediaUsageSlot.LOGO, MediaUsageSlot.COVER),
                    publishedPageVisibility(entityId, PageType.TOURNAMENT, Visibility.ORGANIZATION_PRIVATE),
                )
            }

            MediaEntityType.HOUSEHOLD_ADULT -> {
                val adult =
                    householdRepository
                        .findAdultById(entityId, organizationId)
                        ?.takeIf { it.status == AdultStatus.ACTIVE }
                        ?: throw NotFoundException("HOUSEHOLD_ADULT_NOT_FOUND", "The adult profile could not be found.")
                if (manage) {
                    requireAdultProfileManageAccess(organizationId, adult.id, currentUser)
                } else {
                    requireHouseholdProfileAccess(organizationId, adult.householdId, currentUser)
                }
                ResolvedMediaTarget(
                    organizationId,
                    entityType,
                    entityId,
                    setOf(MediaUsageSlot.PROFILE_PHOTO),
                    Visibility.HOUSEHOLD_PRIVATE,
                )
            }

            MediaEntityType.PARTICIPANT -> {
                val participant =
                    participantRepository
                        .findById(entityId, organizationId)
                        ?.takeIf { it.status == ParticipantStatus.ACTIVE }
                        ?: throw NotFoundException("PARTICIPANT_NOT_FOUND", "The participant could not be found.")
                val householdAccess =
                    membershipService.hasManagerRole(organizationId, currentUser) ||
                        authorizationService.hasGuardianRelationship(organizationId, participant.householdId, currentUser)
                val athleteCapability = if (manage) Capabilities.ATHLETE_PROFILE_UPDATE else Capabilities.ATHLETE_PROFILE_VIEW
                val athleteAccess = authorizationService.hasParticipantCapability(currentUser, participant.id, athleteCapability)
                if (!householdAccess && !athleteAccess) {
                    throw ForbiddenException("PROFILE_PHOTO_ACCESS_DENIED", "You do not have access to this profile photo.")
                }
                ResolvedMediaTarget(
                    organizationId,
                    entityType,
                    entityId,
                    // DOCUMENT added Phase 31 slice 31.2: a guardian (or the athlete
                    // themselves, when a requirement allows self-sign) uploading
                    // eligibility/waiver evidence reuses this exact same
                    // guardian-relationship/athlete-self check — no new access-control
                    // path needed for what's still fundamentally "can this person act on
                    // this participant's private files."
                    setOf(MediaUsageSlot.PROFILE_PHOTO, MediaUsageSlot.DOCUMENT),
                    Visibility.HOUSEHOLD_PRIVATE,
                )
            }

            // Household media (Track 5, 2026-08-13) — the first HOUSEHOLD case this
            // service handles (documents bypass this service entirely, see
            // DocumentService's own doc comment); same read/manage split as PARTICIPANT
            // and HOUSEHOLD_ADULT above, since it's the same underlying question — can
            // this person act on this household's private files.
            MediaEntityType.HOUSEHOLD -> {
                householdRepository.findById(entityId, organizationId)
                    ?: throw NotFoundException("HOUSEHOLD_NOT_FOUND", "The household could not be found.")
                requireHouseholdProfileAccess(organizationId, entityId, currentUser)
                ResolvedMediaTarget(
                    organizationId,
                    entityType,
                    entityId,
                    setOf(MediaUsageSlot.HOUSEHOLD_MEDIA),
                    Visibility.HOUSEHOLD_PRIVATE,
                )
            }

            else -> throw ValidationException("This media entity type is not available through the branding API.")
        }

    private fun requireAdultProfileManageAccess(
        organizationId: UUID,
        adultId: UUID,
        currentUser: CurrentUser,
    ) {
        if (membershipService.hasManagerRole(organizationId, currentUser)) return
        if (authorizationService.hasGuardianAdultRelationship(organizationId, adultId, currentUser)) return
        throw ForbiddenException("PROFILE_PHOTO_ACCESS_DENIED", "You cannot change this adult's profile photo.")
    }

    private fun requireHouseholdProfileAccess(
        organizationId: UUID,
        householdId: UUID,
        currentUser: CurrentUser,
    ) {
        if (membershipService.hasManagerRole(organizationId, currentUser)) return
        if (authorizationService.hasGuardianRelationship(organizationId, householdId, currentUser)) return
        throw ForbiddenException("PROFILE_PHOTO_ACCESS_DENIED", "You do not have access to this profile photo.")
    }

    private fun publishedPageVisibility(
        entityId: UUID,
        expectedType: PageType,
        privateVisibility: Visibility,
    ): Visibility {
        val page = publicPageRepository.findByEntityId(entityId)
        return if (page?.pageType == expectedType && page.status == PageStatus.PUBLISHED) Visibility.PUBLIC else privateVisibility
    }
}
