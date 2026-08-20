package com.rally26.search.application

import com.rally26.authorization.domain.RoleAssignmentContextType
import com.rally26.authorization.persistence.GuardianRelationshipRepository
import com.rally26.authorization.persistence.RoleAssignmentRepository
import com.rally26.common.error.ForbiddenException
import com.rally26.common.web.CurrentUser
import com.rally26.membership.application.MembershipService
import com.rally26.search.domain.SearchHit
import com.rally26.search.persistence.SearchRepository
import org.springframework.stereotype.Service
import java.util.UUID

private const val MIN_QUERY_LENGTH = 2
private const val RESULTS_PER_CATEGORY = 8

/**
 * Global search (DESIGN-DOC.md section 13, Phase 7 completion): teams/participants/
 * households within one organization for anyone with a real connection to it, or
 * organizations platform-wide for a platform administrator.
 *
 * Security review (2026-08): this originally only called [MembershipService.requireActiveMembership],
 * which any active member passes regardless of role, letting e.g. a team-scoped Coach
 * pull every household/team/participant in the org via direct API call. A first pass
 * narrowed this to OWNER/ADMINISTRATOR only — but that's not the intended model either:
 * search should stay available to every role (Athlete, Guardian, Coach, Owner/Admin),
 * just with every *result* scoped to what the caller can actually see — an Athlete sees
 * their own team and teammates, a Guardian sees the team(s) their household's athlete(s)
 * play on (teammates and those teams' households), a Coach sees the team(s) they coach
 * (same shape as a Guardian's scope), and Owner/Administrator keep full org-wide
 * visibility. [resolveScope] computes this per caller; every query in [SearchRepository]
 * applies it server-side before any row reaches the response — never a client-side
 * filter, and never a category the caller has no scope into.
 */
@Service
class SearchService(
    private val searchRepository: SearchRepository,
    private val membershipService: MembershipService,
    private val guardianRelationshipRepository: GuardianRelationshipRepository,
    private val roleAssignmentRepository: RoleAssignmentRepository,
) {
    fun searchOrganization(
        organizationId: UUID,
        query: String,
        currentUser: CurrentUser,
    ): List<SearchHit> {
        val teamScope = resolveScope(organizationId, currentUser)
        if (query.trim().length < MIN_QUERY_LENGTH) return emptyList()
        return searchRepository.searchTeams(organizationId, query, RESULTS_PER_CATEGORY, teamScope) +
            searchRepository.searchParticipants(organizationId, query, RESULTS_PER_CATEGORY, teamScope) +
            searchRepository.searchHouseholds(organizationId, query, RESULTS_PER_CATEGORY, teamScope)
    }

    /**
     * `null` = unrestricted (org-wide, Owner/Administrator/platform admin). Otherwise a
     * (possibly empty) set of team IDs the caller may see results through — computed by
     * [SearchRepository.resolveTeamScope], which already covers the Coach/Athlete/
     * Guardian paths in one query. Throws if the caller has no real connection to this
     * organization at all (not a member, not a guardian, not a linked athlete) — an
     * empty *scope* (a real member/guardian/athlete with no team yet) is not the same as
     * *no access*, and returns empty results rather than a 403.
     */
    private fun resolveScope(
        organizationId: UUID,
        currentUser: CurrentUser,
    ): Set<UUID>? {
        if (currentUser.platformAdministrator || membershipService.hasManagerRole(organizationId, currentUser)) {
            return null
        }
        val hasMembership =
            try {
                membershipService.requireActiveMembership(organizationId, currentUser)
                true
            } catch (_: ForbiddenException) {
                false
            }
        val isGuardian =
            guardianRelationshipRepository.findActiveForUser(currentUser.userId).any { it.organizationId == organizationId }
        val isLinkedAthlete =
            roleAssignmentRepository
                .findActiveForUserAndContext(currentUser.userId, RoleAssignmentContextType.PARTICIPANT)
                .any { it.organizationId == organizationId }
        if (!hasMembership && !isGuardian && !isLinkedAthlete) {
            throw ForbiddenException("ORGANIZATION_ACCESS_DENIED", "You do not have access to this organization.")
        }
        return searchRepository.resolveTeamScope(organizationId, currentUser.userId)
    }

    fun searchPlatform(
        query: String,
        currentUser: CurrentUser,
    ): List<SearchHit> {
        if (!currentUser.platformAdministrator) {
            throw ForbiddenException("PLATFORM_ACCESS_DENIED", "Only a platform administrator can search across organizations.")
        }
        if (query.trim().length < MIN_QUERY_LENGTH) return emptyList()
        return searchRepository.searchOrganizations(query, RESULTS_PER_CATEGORY)
    }
}
