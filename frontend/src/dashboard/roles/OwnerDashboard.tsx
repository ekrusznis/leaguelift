import { Link } from "react-router-dom";
import { DashboardShell, type DashNavItem } from "../DashboardShell";
import { useContexts } from "../../authorization/api";
import { capabilitiesFor } from "../../authorization/capabilities";
import { Capabilities } from "../../authorization/capabilityConstants";
import { navItemsFor } from "../registry/navRegistry";
import { visibleWidgetIds } from "../registry/widgetRegistry";
import { DashCard } from "../components/DashCard";
import { DashboardPageHeader } from "../components/DashboardPageHeader";
import { StatTile } from "../components/StatTile";
import { Pill } from "../components/Pill";
import { ProgressBar } from "../components/ProgressBar";
import { CardQuery } from "../components/CardQuery";
import { IconBadge } from "../components/IconBadge";
import { PrimaryButton, SecondaryLightButton } from "../../marketing/components/buttons";
import { adultAvatars, sidebarPromoBackground } from "../demoAssets";
import { useAuth } from "../../auth/AuthContext";
import { formatMoneyMinorUnits } from "../../lib/money";
import { appPaths } from "../../routes/appPaths";
import { describeActivityAction, timeAgo } from "../activity";
import {
	useOwnerFinancialOverview,
	useOwnerRecentActivity,
	useOwnerReportsSnapshot,
	useOwnerSummary,
	useOwnerTeamPerformance,
	useOwnerUpcomingEvents,
} from "../api";
import {
	BuildingIcon,
	CalendarIcon,
	ChartIcon,
	CheckCircleIcon,
	DownloadIcon,
	HomeIcon,
	PlusIcon,
	TrophyIcon,
	UserIcon,
	UserPlusIcon,
	UsersIcon,
} from "../icons";

function quickActions(organizationId: string) {
	return [
		{ icon: <TrophyIcon className="size-5" />, label: "Create Tournament", to: appPaths.organization(organizationId, "tournaments") },
		{ icon: <UsersIcon className="size-5" />, label: "Create Team", to: appPaths.organization(organizationId, "teams") },
		{ icon: <ChartIcon className="size-5" />, label: "Run Report", to: appPaths.organization(organizationId, "reports") },
		{ icon: <UserPlusIcon className="size-5" />, label: "Manage Members", to: appPaths.organization(organizationId, "members") },
	];
}

/** Owner dashboard, wired to real per-card API calls (DESIGN-DOC.md section 10.1/10.2). */
export function OwnerDashboard({ organizationId }: { organizationId: string }) {
	const { user } = useAuth();
	const contexts = useContexts();
	const organizationContext = contexts.data?.find(
		(context) => context.contextType === "ORGANIZATION" && context.resourceId === organizationId,
	);
	const organizationCapabilities = capabilitiesFor(contexts.data, "ORGANIZATION", organizationId);
	const visibleWidgets = visibleWidgetIds("ORGANIZATION", organizationCapabilities);
	const canManageOrganization = organizationCapabilities.has(Capabilities.ORG_MANAGE);
	const canManageMembers = organizationCapabilities.has(Capabilities.ORG_MEMBERS_MANAGE);
	const canManageTeams = organizationCapabilities.has(Capabilities.ORG_TEAM_MANAGE) || canManageOrganization;
	const roleLabel = {
		OWNER: "Owner",
		ADMINISTRATOR: "Administrator",
		VIEWER: "Viewer",
	}[organizationContext?.role ?? ""] ?? "Organization Member";

	const navItems: DashNavItem[] = navItemsFor("ORGANIZATION", organizationCapabilities, { organizationId }).map((item) => ({
		icon: item.icon,
		label: item.label,
		to: item.to,
	}));

	// Pass a null id to hooks for widgets the current organization context cannot see.
	// The hooks already use `enabled: !!organizationId`, so hidden role-specific data
	// is not fetched merely because every organization membership lands on this shell.
	const summary = useOwnerSummary(organizationId);
	const financialOverview = useOwnerFinancialOverview(visibleWidgets.has("owner.financial-overview") ? organizationId : null);
	const teamPerformance = useOwnerTeamPerformance(visibleWidgets.has("owner.team-performance") ? organizationId : null);
	const upcomingEvents = useOwnerUpcomingEvents(visibleWidgets.has("owner.upcoming-events") ? organizationId : null);
	const recentActivity = useOwnerRecentActivity(visibleWidgets.has("owner.recent-activity") ? organizationId : null);
	const reportsSnapshot = useOwnerReportsSnapshot(visibleWidgets.has("owner.reports-snapshot") ? organizationId : null);

	return (
		<DashboardShell
			contextIcon={<BuildingIcon className="size-4" />}
			contextIconTone="bg-navy-900"
			contextName={summary.data?.organizationName ?? "Organization"}
			contextRole={roleLabel}
			navItems={navItems}
			showSearch={canManageOrganization}
			searchScope={{ kind: "organization", organizationId }}
			showHelp
			userName={user?.displayName ?? "Account"}
			userAvatarSrc={adultAvatars.owner}
			promo={{
				heading: "Stronger clubs. Stronger communities.",
				copy: "We're here to help.",
				linkLabel: "Visit Resource Center",
				to: "/help",
				backgroundSrc: sidebarPromoBackground,
			}}
		>
			<DashboardPageHeader
				heading="Organization Overview"
				description="Manage your club, finances, teams, and operations in one place."
				actions={canManageOrganization || canManageMembers || canManageTeams ? (
					<>
						{canManageOrganization && (
							<SecondaryLightButton icon="none" to={`/app/organizations/${organizationId}/collections`}>
								<DownloadIcon className="size-4" /> Collections &amp; Export
							</SecondaryLightButton>
						)}
						{canManageMembers && (
							<SecondaryLightButton icon="none" to={appPaths.organization(organizationId, "members")}>
								<UserPlusIcon className="size-4" /> Invite Member
							</SecondaryLightButton>
						)}
						{canManageTeams && (
							<PrimaryButton icon="none" to={appPaths.organization(organizationId, "teams")}>
								<PlusIcon className="size-4" /> Create Team
							</PrimaryButton>
						)}
					</>
				) : undefined}
			/>

			<div className="grid gap-5 lg:grid-cols-3">
				{visibleWidgets.has("owner.summary") && (
					<DashCard
						id="owner-summary"
						title="Organization Summary"
						action={canManageOrganization ? { label: "Open organization", to: appPaths.organization(organizationId, "overview") } : undefined}
					>
						<CardQuery query={summary} loadingLabel="Loading summary…">
							{(data) => (
								<>
									<div className="grid grid-cols-2 gap-4">
										<StatTile icon={<UsersIcon className="size-5" />} value={String(data.activeTeams)} label="Active Teams" />
										<StatTile icon={<UserIcon className="size-5" />} value={String(data.participants)} label="Participants" />
										<StatTile icon={<HomeIcon className="size-5" />} value={String(data.households)} label="Households" />
										<StatTile icon={<TrophyIcon className="size-5" />} value={String(data.upcomingTournaments)} label="Upcoming Tournaments" />
									</div>
									<p className="mt-4 flex items-center gap-2 border-t border-slate-200 dark:border-[#334155] pt-3 text-xs text-green-600">
										<CheckCircleIcon className="size-4" /> All systems operational
									</p>
								</>
							)}
						</CardQuery>
					</DashCard>
				)}

				{visibleWidgets.has("owner.financial-overview") && (
					<DashCard
						id="owner-financial"
						title="Financial Overview"
						action={{ label: "View reports", to: appPaths.organization(organizationId, "reports") }}
					>
						<CardQuery query={financialOverview} loadingLabel="Loading financials…">
							{(data) => (
								<div className="grid grid-cols-2 gap-3 text-sm sm:grid-cols-3">
									<div>
										<p className="text-xs text-slate-500 dark:text-[#cbd5e1]">Fees Assigned</p>
										<p className="font-heading text-lg font-bold text-navy-900 dark:text-[#f8fafc]">{formatMoneyMinorUnits(data.feesAssignedMinor, data.currency)}</p>
									</div>
									<div>
										<p className="text-xs text-slate-500 dark:text-[#cbd5e1]">Fees Collected</p>
										<p className="font-heading text-lg font-bold text-navy-900 dark:text-[#f8fafc]">{formatMoneyMinorUnits(data.feesCollectedMinor, data.currency)}</p>
									</div>
									<div>
										<p className="text-xs text-slate-500 dark:text-[#cbd5e1]">Outstanding</p>
										<p className="font-heading text-lg font-bold text-error-600">{formatMoneyMinorUnits(data.outstandingMinor, data.currency)}</p>
									</div>
									<div>
										<p className="text-xs text-slate-500 dark:text-[#cbd5e1]">Fundraising</p>
										<p className="font-heading text-lg font-bold text-navy-900 dark:text-[#f8fafc]">{formatMoneyMinorUnits(data.fundraisingMinor, data.currency)}</p>
									</div>
									<div>
										<p className="text-xs text-slate-500 dark:text-[#cbd5e1]">Apparel Sales</p>
										<p className="font-heading text-lg font-bold text-navy-900 dark:text-[#f8fafc]">{formatMoneyMinorUnits(data.apparelSalesMinor, data.currency)}</p>
									</div>
									<div>
										<p className="text-xs text-slate-500 dark:text-[#cbd5e1]">Pending Payout</p>
										<p className="font-heading text-lg font-bold text-navy-900 dark:text-[#f8fafc]">{formatMoneyMinorUnits(data.pendingPayoutMinor, data.currency)}</p>
									</div>
									{data.isFundraisingDemoData && (
										<p className="col-span-2 text-xs text-slate-400 sm:col-span-3">Fundraising/apparel/payout figures are demo data — fees are real.</p>
									)}
								</div>
							)}
						</CardQuery>
					</DashCard>
				)}

				{visibleWidgets.has("owner.team-performance") && (
					<DashCard id="owner-teams" title="Team Performance Overview" action={{ label: "View all teams", to: appPaths.organization(organizationId, "teams") }} className="lg:col-span-1">
						<CardQuery
							query={teamPerformance}
							loadingLabel="Loading teams…"
							isEmpty={(rows) => rows.length === 0}
							emptyTitle="No teams yet"
							emptyDescription="Create a team to see it here."
						>
							{(rows) => (
								<div className="overflow-x-auto">
									<table className="w-full min-w-[420px] text-left text-xs">
										<thead>
											<tr className="text-slate-500 dark:text-[#cbd5e1]">
												<th className="pb-2 font-medium">Team</th>
												<th className="pb-2 font-medium">Participants</th>
												<th className="pb-2 font-medium">Fundraising</th>
												<th className="pb-2 font-medium">Status</th>
											</tr>
										</thead>
										<tbody className="divide-y divide-slate-100 dark:divide-[#334155]">
											{rows.map((team) => (
												<tr key={team.teamId}>
													<td className="py-2">
														<p className="font-medium text-navy-900 dark:text-[#f8fafc]">{team.name}</p>
														<p className="text-slate-500 dark:text-[#cbd5e1]">{team.sport}</p>
													</td>
													<td className="py-2 text-navy-900 dark:text-[#f8fafc]">{team.participants}</td>
													<td className="py-2">
														{team.isFundraisingDemoData || team.fundraisingRaisedMinor === null || team.fundraisingGoalMinor === null ? (
															<span className="text-slate-400">Demo data</span>
														) : (
															<>
																<p className="text-navy-900 dark:text-[#f8fafc]">
																	{formatMoneyMinorUnits(team.fundraisingRaisedMinor, "USD")} / {formatMoneyMinorUnits(team.fundraisingGoalMinor, "USD")}
																</p>
																<div className="mt-1 w-20">
																	<ProgressBar percent={(team.fundraisingRaisedMinor / team.fundraisingGoalMinor) * 100} />
																</div>
															</>
														)}
													</td>
													<td className="py-2">
														<Pill tone={team.status === "ACTIVE" ? "success" : "neutral"}>{team.status}</Pill>
													</td>
												</tr>
											))}
										</tbody>
									</table>
								</div>
							)}
						</CardQuery>
					</DashCard>
				)}

				{visibleWidgets.has("owner.upcoming-events") && (
					<DashCard id="owner-events" title="Upcoming Events" action={{ label: "View calendar", to: appPaths.organizationEvents(organizationId) }}>
						<CardQuery
							query={upcomingEvents}
							loadingLabel="Loading events…"
							isEmpty={(items) => items.length === 0}
							emptyTitle="No upcoming events"
						>
							{(items) => (
								<ul className="flex flex-col gap-3">
									{items.map((event) => (
										<li key={event.id} className="flex items-start gap-3">
											<div className="flex w-14 shrink-0 flex-col items-center rounded-lg bg-ice-50 dark:bg-[#0f172a] py-1 text-xs font-semibold text-slate-500 dark:text-[#cbd5e1]">
												<CalendarIcon className="size-4 text-slate-400" />
												{event.day} {event.date}
											</div>
											<div className="min-w-0 flex-1">
												<Link to={appPaths.event(organizationId, event.id)} className="truncate text-sm font-medium text-navy-900 dark:text-[#f8fafc] hover:text-green-600 hover:underline">{event.title}</Link>
												<p className="text-xs text-slate-500 dark:text-[#cbd5e1]">{event.subtitle}</p>
											</div>
											{event.tag && <Pill tone={event.tag === "Home" ? "success" : "info"}>{event.tag}</Pill>}
										</li>
									))}
								</ul>
							)}
						</CardQuery>
					</DashCard>
				)}

				{visibleWidgets.has("owner.recent-activity") && (
					<DashCard id="owner-recent-activity" title="Recent Activity">
						<CardQuery
							query={recentActivity}
							loadingLabel="Loading activity…"
							isEmpty={(items) => items.length === 0}
							emptyTitle="No recent activity"
						>
							{(items) => (
								<ul className="flex flex-col gap-4">
									{items.map((activity) => (
										<li key={activity.id} className="flex items-start gap-3">
											<IconBadge icon={<ChartIcon className="size-4" />} tone="info" />
											<div className="min-w-0 flex-1">
												<p className="text-sm font-medium text-navy-900 dark:text-[#f8fafc]">{describeActivityAction(activity.action)}</p>
												<p className="text-xs text-slate-500 dark:text-[#cbd5e1]">{activity.entityType}</p>
											</div>
											<span className="shrink-0 text-xs text-slate-400">{timeAgo(activity.occurredAt)}</span>
										</li>
									))}
								</ul>
							)}
						</CardQuery>
					</DashCard>
				)}

				{visibleWidgets.has("owner.reports-snapshot") && (
					<DashCard id="owner-reports" title="Reports Snapshot" action={{ label: "View all reports", to: appPaths.organization(organizationId, "reports") }}>
						<CardQuery
							query={reportsSnapshot}
							loadingLabel="Loading reports…"
							isEmpty={(items) => items.length === 0}
							emptyTitle="No reports yet"
						>
							{(items) => (
								<div className="grid grid-cols-2 gap-4">
									{items.map((report) => (
										<div key={report.label}>
											<p className="text-xs text-slate-500 dark:text-[#cbd5e1]">{report.label}</p>
											<p className="font-heading text-base font-bold text-navy-900 dark:text-[#f8fafc]">
												{formatMoneyMinorUnits(report.valueMinor, "USD")}{" "}
												<span className="text-xs font-semibold text-green-600">
													{report.trendPercent > 0 ? "+" : ""}
													{report.trendPercent}%
												</span>
											</p>
											{report.isDemoData && <p className="text-xs text-slate-400">Demo data</p>}
										</div>
									))}
								</div>
							)}
						</CardQuery>
					</DashCard>
				)}

				{visibleWidgets.has("owner.quick-actions") && (
					<DashCard id="owner-quick-actions" title="Quick Actions">
						<div className="grid grid-cols-2 gap-3">
							{quickActions(organizationId).map((action) => (
								<Link
									key={action.label}
									to={action.to}
									className="flex flex-col items-center gap-2 rounded-xl border border-slate-200 dark:border-[#334155] p-4 text-center text-xs font-medium text-navy-900 dark:text-[#f8fafc] hover:border-green-500 hover:text-green-600"
								>
									<span className="text-green-600">{action.icon}</span>
									{action.label}
								</Link>
							))}
						</div>
					</DashCard>
				)}
			</div>
		</DashboardShell>
	);
}
