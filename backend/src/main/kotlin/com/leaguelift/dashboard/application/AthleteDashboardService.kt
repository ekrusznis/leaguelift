package com.leaguelift.dashboard.application

import com.leaguelift.common.web.CurrentUser
import com.leaguelift.dashboard.web.AthleteOverviewResponse
import com.leaguelift.dashboard.web.AthleteTeamSummary
import com.leaguelift.dashboard.web.GuardianSummary
import com.leaguelift.dashboard.web.HistoryItem
import com.leaguelift.dashboard.web.NextEventSummary
import com.leaguelift.dashboard.web.OrderSummary
import com.leaguelift.dashboard.web.ScheduleItem
import org.springframework.stereotype.Service
import java.time.LocalDate

/**
 * One method per Athlete-dashboard card, all entirely demo data, self-scoped to the
 * caller. Unlike the other three dashboards, there is no real data to wire this to:
 * LeagueLift has no product-sanctioned participant-login concept (DESIGN-DOC.md
 * section 4.6), so an "athlete" app_user has no real linked participant record to
 * query. Each endpoint's auth boundary is still real (requires a valid signed-in
 * user), it just has nothing real to return yet.
 */
@Service
class AthleteDashboardService {

	fun getOverview(currentUser: CurrentUser) = AthleteOverviewResponse(
		displayName = currentUser.displayName,
		isDemoData = true,
		nextEvent = NextEventSummary(
			title = "vs Northview Falcons",
			subtitle = "Varsity Soccer · Home Game",
			dateLabel = "Saturday",
			location = "Riverside High School",
		),
	)

	fun getTeams(currentUser: CurrentUser): List<AthleteTeamSummary> = listOf(
		AthleteTeamSummary("Riverside High School", "Varsity Soccer", "Coach Jordan Ellis"),
	)

	fun getWeekEvents(currentUser: CurrentUser): List<ScheduleItem> = listOf(
		ScheduleItem("wk-1", "SAT", "24", "vs Northview Falcons", "Varsity Soccer", "4:30 PM", "Home"),
		ScheduleItem("wk-2", "TUE", "27", "Practice", "Varsity Soccer", "5:00 PM", null),
	)

	fun getRecentHistory(currentUser: CurrentUser): List<HistoryItem> = listOf(
		HistoryItem("hist-1", "vs Cedar Ridge Raiders", "recently", "Home", "W 3-1", true),
		HistoryItem("hist-2", "@ Lake Travis Cavaliers", "recently", "Away", "L 1-2", false),
	)

	fun getGuardians(currentUser: CurrentUser): List<GuardianSummary> = listOf(
		GuardianSummary("Sarah Johnson", "Primary Guardian", "sarah.johnson@example.com", "(555) 555-0198"),
	)

	fun getOrders(currentUser: CurrentUser): List<OrderSummary> = listOf(
		OrderSummary("ord-1", "Riverside Soccer Hoodie", "#LL-78426", LocalDate.now().minusDays(6), "Delivered"),
	)
}
