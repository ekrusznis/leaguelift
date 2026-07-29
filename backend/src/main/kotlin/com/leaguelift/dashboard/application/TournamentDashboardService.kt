package com.leaguelift.dashboard.application

import com.leaguelift.authorization.application.AuthorizationService
import com.leaguelift.authorization.domain.Capabilities
import com.leaguelift.common.error.NotFoundException
import com.leaguelift.common.web.CurrentUser
import com.leaguelift.dashboard.web.TournamentPageStatusItem
import com.leaguelift.dashboard.web.TournamentSummaryResponse
import com.leaguelift.publicpage.persistence.PublicPageRepository
import com.leaguelift.tournament.persistence.TournamentRepository
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * Tournament Dashboard (DESIGN-DOC.md section 10.2, new in Phase 7/ADR-020 — this
 * dashboard did not exist as a component or backend service before this slice).
 * Deliberately minimal: only the two cards the current schema genuinely supports
 * (tournament identity and public-page status) are wired real. The full nav DESIGN-DOC.md
 * describes (Participating Teams, Divisions, Apparel, Fundraising, Sponsors, Orders,
 * Reports) depends on domain concepts that don't exist yet — `tournament_team` is
 * still design-target only (DESIGN-DOC.md section 8.3) — so those are left out
 * entirely rather than backed by invented/demo data (ADR-020 consequences).
 */
@Service
class TournamentDashboardService(
	private val authorizationService: AuthorizationService,
	private val tournamentRepository: TournamentRepository,
	private val publicPageRepository: PublicPageRepository,
) {

	fun getSummary(organizationId: UUID, tournamentId: UUID, currentUser: CurrentUser): TournamentSummaryResponse {
		authorizationService.requireTournamentCapability(organizationId, tournamentId, currentUser, Capabilities.TOURNAMENT_VIEW)
		val tournament = tournamentRepository.findById(tournamentId, organizationId)
			?: throw NotFoundException("TOURNAMENT_NOT_FOUND", "The tournament could not be found.")
		return TournamentSummaryResponse(
			tournamentId = tournament.id,
			name = tournament.name,
			sport = tournament.sport,
			status = tournament.status.name,
			startDate = tournament.startDate,
			endDate = tournament.endDate,
			location = tournament.location,
		)
	}

	fun getPageStatus(organizationId: UUID, tournamentId: UUID, currentUser: CurrentUser): TournamentPageStatusItem {
		authorizationService.requireTournamentCapability(organizationId, tournamentId, currentUser, Capabilities.TOURNAMENT_VIEW)
		val tournament = tournamentRepository.findById(tournamentId, organizationId)
			?: throw NotFoundException("TOURNAMENT_NOT_FOUND", "The tournament could not be found.")
		val page = publicPageRepository.findByEntityId(tournament.id)
		return TournamentPageStatusItem(tournament.id, tournament.name, page?.status?.name ?: "NOT_CREATED", page?.slug)
	}

	/** Every tournament the caller has TOURNAMENT_VIEW on in this organization — used to pick a default tournament to land on. */
	fun listAccessibleTournamentIds(organizationId: UUID, currentUser: CurrentUser): Set<UUID> =
		authorizationService.listAccessibleTournamentIds(organizationId, currentUser, Capabilities.TOURNAMENT_VIEW)
}
