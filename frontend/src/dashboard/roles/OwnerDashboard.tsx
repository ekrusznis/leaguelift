import type { ReactNode } from "react";
import { DashboardShell, type DashNavItem } from "../DashboardShell";
import { DashCard } from "../components/DashCard";
import { DashboardPageHeader } from "../components/DashboardPageHeader";
import { StatTile } from "../components/StatTile";
import { Pill } from "../components/Pill";
import { ProgressBar } from "../components/ProgressBar";
import { IconBadge } from "../components/IconBadge";
import { CardQuery } from "../components/CardQuery";
import { PrimaryButton, SecondaryLightButton, TextButton } from "../../marketing/components/buttons";
import { adultAvatars, sidebarPromoBackground } from "../demoAssets";
import { useAuth } from "../../auth/AuthContext";
import { formatMoneyMinorUnits } from "../../lib/money";
import { describeActivityAction, timeAgo } from "../activity";
import {
	useOwnerAttentionRequired,
	useOwnerFinancialOverview,
	useOwnerOnboardingProgress,
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
	DollarIcon,
	DownloadIcon,
	GiftIcon,
	HeartHandshakeIcon,
	HomeIcon,
	MegaphoneIcon,
	PackageIcon,
	PlusIcon,
	SettingsIcon,
	ShirtIcon,
	TrophyIcon,
	UserIcon,
	UserPlusIcon,
	UsersIcon,
	AlertIcon,
	ClipboardCheckIcon,
} from "../icons";

const NAV_ITEMS: DashNavItem[] = [
	{ icon: <HomeIcon className="size-5" />, label: "Overview", active: true },
	{ icon: <BuildingIcon className="size-5" />, label: "Organization" },
	{ icon: <UsersIcon className="size-5" />, label: "Teams" },
	{ icon: <TrophyIcon className="size-5" />, label: "Tournaments" },
	{ icon: <HomeIcon className="size-5" />, label: "Households" },
	{ icon: <UserIcon className="size-5" />, label: "Participants" },
	{ icon: <DollarIcon className="size-5" />, label: "Fees & Payments" },
	{ icon: <HeartHandshakeIcon className="size-5" />, label: "Fundraising" },
	{ icon: <ShirtIcon className="size-5" />, label: "Apparel" },
	{ icon: <MegaphoneIcon className="size-5" />, label: "Sponsorships" },
	{ icon: <GiftIcon className="size-5" />, label: "Credits" },
	{ icon: <ChartIcon className="size-5" />, label: "Reports" },
	{ icon: <UserPlusIcon className="size-5" />, label: "Members" },
	{ icon: <SettingsIcon className="size-5" />, label: "Settings" },
];

const QUICK_ACTIONS = [
	{ icon: <TrophyIcon className="size-5" />, label: "Create Tournament" },
	{ icon: <MegaphoneIcon className="size-5" />, label: "Send Announcement" },
	{ icon: <ChartIcon className="size-5" />, label: "Run Report" },
	{ icon: <UserPlusIcon className="size-5" />, label: "Manage Members" },
];

const ATTENTION_ICONS: Record<string, ReactNode> = {
	"failed-payments": <AlertIcon className="size-4" />,
	"pending-approvals": <ClipboardCheckIcon className="size-4" />,
	"expiring-invitations": <UserPlusIcon className="size-4" />,
	"fulfillment-issues": <PackageIcon className="size-4" />,
};

/** Owner dashboard, wired to real per-card API calls (DESIGN-DOC.md section 10.1/10.2). */
export function OwnerDashboard({ organizationId }: { organizationId: string }) {
	const { user } = useAuth();
	const summary = useOwnerSummary(organizationId);
	const financialOverview = useOwnerFinancialOverview(organizationId);
	const attentionRequired = useOwnerAttentionRequired(organizationId);
	const teamPerformance = useOwnerTeamPerformance(organizationId);
	const upcomingEvents = useOwnerUpcomingEvents(organizationId);
	const recentActivity = useOwnerRecentActivity(organizationId);
	const onboardingProgress = useOwnerOnboardingProgress(organizationId);
	const reportsSnapshot = useOwnerReportsSnapshot(organizationId);

	return (
		<DashboardShell
			contextIcon={<BuildingIcon className="size-4" />}
			contextIconTone="bg-navy-900"
			contextName={summary.data?.organizationName ?? "Organization"}
			contextRole="Director"
			navItems={NAV_ITEMS}
			showSearch
			showHelp
			userName={user?.displayName ?? "Account"}
			userAvatarSrc={adultAvatars.owner}
			promo={{
				heading: "Stronger clubs. Stronger communities.",
				copy: "We're here to help.",
				linkLabel: "Visit Resource Center",
				backgroundSrc: sidebarPromoBackground,
			}}
		>
			<DashboardPageHeader
				heading="Organization Overview"
				description="Manage your club, finances, teams, and operations in one place."
				actions={
					<>
						<SecondaryLightButton icon="none" to={`/app/organizations/${organizationId}/collections`}>
							<DownloadIcon className="size-4" /> Collections &amp; Export
						</SecondaryLightButton>
						<SecondaryLightButton icon="none">
							<UserPlusIcon className="size-4" /> Invite Member
						</SecondaryLightButton>
						<PrimaryButton icon="none">
							<PlusIcon className="size-4" /> Create Team
						</PrimaryButton>
					</>
				}
			/>

			<div className="grid gap-5 lg:grid-cols-3">
				<DashCard title="Organization Summary" action={{ label: "View all" }}>
					<CardQuery query={summary} loadingLabel="Loading summary…">
						{(data) => (
							<>
								<div className="grid grid-cols-2 gap-4">
									<StatTile icon={<UsersIcon className="size-5" />} value={String(data.activeTeams)} label="Active Teams" />
									<StatTile icon={<UserIcon className="size-5" />} value={String(data.participants)} label="Participants" />
									<StatTile icon={<HomeIcon className="size-5" />} value={String(data.households)} label="Households" />
									<StatTile icon={<TrophyIcon className="size-5" />} value={String(data.upcomingTournaments)} label="Upcoming Tournaments" />
								</div>
								<p className="mt-4 flex items-center gap-2 border-t border-slate-200 pt-3 text-xs text-green-600">
									<CheckCircleIcon className="size-4" /> All systems operational
								</p>
							</>
						)}
					</CardQuery>
				</DashCard>

				<DashCard
					title="Financial Overview"
					action={{ label: "View collections", to: `/app/organizations/${organizationId}/collections` }}
				>
					<CardQuery query={financialOverview} loadingLabel="Loading financials…">
						{(data) => (
							<div className="grid grid-cols-3 gap-3 text-sm">
								<div>
									<p className="text-xs text-slate-500">Fees Assigned</p>
									<p className="font-heading text-lg font-bold text-navy-900">{formatMoneyMinorUnits(data.feesAssignedMinor, data.currency)}</p>
								</div>
								<div>
									<p className="text-xs text-slate-500">Fees Collected</p>
									<p className="font-heading text-lg font-bold text-navy-900">{formatMoneyMinorUnits(data.feesCollectedMinor, data.currency)}</p>
								</div>
								<div>
									<p className="text-xs text-slate-500">Outstanding</p>
									<p className="font-heading text-lg font-bold text-error-600">{formatMoneyMinorUnits(data.outstandingMinor, data.currency)}</p>
								</div>
								<div>
									<p className="text-xs text-slate-500">Fundraising</p>
									<p className="font-heading text-lg font-bold text-navy-900">{formatMoneyMinorUnits(data.fundraisingMinor, data.currency)}</p>
								</div>
								<div>
									<p className="text-xs text-slate-500">Apparel Sales</p>
									<p className="font-heading text-lg font-bold text-navy-900">{formatMoneyMinorUnits(data.apparelSalesMinor, data.currency)}</p>
								</div>
								<div>
									<p className="text-xs text-slate-500">Pending Payout</p>
									<p className="font-heading text-lg font-bold text-navy-900">{formatMoneyMinorUnits(data.pendingPayoutMinor, data.currency)}</p>
								</div>
								{data.isFundraisingDemoData && (
									<p className="col-span-3 text-xs text-slate-400">Fundraising/apparel/payout figures are demo data — fees are real.</p>
								)}
							</div>
						)}
					</CardQuery>
				</DashCard>

				<DashCard title="Attention Required" action={{ label: "View all" }}>
					<CardQuery
						query={attentionRequired}
						loadingLabel="Loading…"
						isEmpty={(items) => items.length === 0}
						emptyTitle="Nothing needs attention"
					>
						{(items) => (
							<ul className="flex flex-col gap-3">
								{items.map((item) => (
									<li key={item.id} className="flex items-center gap-3">
										<IconBadge icon={ATTENTION_ICONS[item.id] ?? <AlertIcon className="size-4" />} tone={item.tone === "error" ? "error" : item.tone === "warning" ? "warning" : item.tone === "purple" ? "purple" : "info"} />
										<div className="min-w-0 flex-1">
											<p className="truncate text-sm font-medium text-navy-900">{item.title}</p>
											<p className="truncate text-xs text-slate-500">{item.subtitle}</p>
										</div>
										<Pill tone={item.tone === "error" ? "error" : item.tone === "warning" ? "warning" : "info"}>{String(item.count)}</Pill>
									</li>
								))}
							</ul>
						)}
					</CardQuery>
				</DashCard>

				<DashCard title="Team Performance Overview" action={{ label: "View all teams" }} className="lg:col-span-1">
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
										<tr className="text-slate-500">
											<th className="pb-2 font-medium">Team</th>
											<th className="pb-2 font-medium">Participants</th>
											<th className="pb-2 font-medium">Fundraising</th>
											<th className="pb-2 font-medium">Status</th>
										</tr>
									</thead>
									<tbody className="divide-y divide-slate-100">
										{rows.map((team) => (
											<tr key={team.teamId}>
												<td className="py-2">
													<p className="font-medium text-navy-900">{team.name}</p>
													<p className="text-slate-500">{team.sport}</p>
												</td>
												<td className="py-2 text-navy-900">{team.participants}</td>
												<td className="py-2">
													{team.isFundraisingDemoData || team.fundraisingRaisedMinor === null || team.fundraisingGoalMinor === null ? (
														<span className="text-slate-400">Demo data</span>
													) : (
														<>
															<p className="text-navy-900">
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

				<DashCard title="Upcoming Events" action={{ label: "View calendar" }}>
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
										<div className="flex w-14 shrink-0 flex-col items-center rounded-lg bg-ice-50 py-1 text-xs font-semibold text-slate-500">
											<CalendarIcon className="size-4 text-slate-400" />
											{event.day} {event.date}
										</div>
										<div className="min-w-0 flex-1">
											<p className="truncate text-sm font-medium text-navy-900">{event.title}</p>
											<p className="text-xs text-slate-500">{event.subtitle}</p>
										</div>
										{event.tag && <Pill tone={event.tag === "Home" ? "success" : "info"}>{event.tag}</Pill>}
									</li>
								))}
							</ul>
						)}
					</CardQuery>
				</DashCard>

				<DashCard title="Recent Activity" action={{ label: "View all activity" }}>
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
											<p className="text-sm font-medium text-navy-900">{describeActivityAction(activity.action)}</p>
											<p className="text-xs text-slate-500">{activity.entityType}</p>
										</div>
										<span className="shrink-0 text-xs text-slate-400">{timeAgo(activity.occurredAt)}</span>
									</li>
								))}
							</ul>
						)}
					</CardQuery>
				</DashCard>

				<DashCard title="Onboarding Progress">
					<CardQuery query={onboardingProgress} loadingLabel="Loading…">
						{(data) => (
							<>
								<p className="text-sm font-medium text-navy-900">You&rsquo;re doing great!</p>
								<p className="text-xs text-slate-500">
									{data.completedSteps} of {data.totalSteps} setup tasks completed
									{data.isDemoData && " (demo data)"}
								</p>
								<div className="mt-3">
									<ProgressBar percent={(data.completedSteps / data.totalSteps) * 100} />
								</div>
								<div className="mt-4 flex items-center justify-between">
									<PrimaryButton icon="none">Continue Setup</PrimaryButton>
									<TextButton className="text-green-600">View checklist</TextButton>
								</div>
							</>
						)}
					</CardQuery>
				</DashCard>

				<DashCard title="Reports Snapshot" action={{ label: "View all reports" }}>
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
										<p className="text-xs text-slate-500">{report.label}</p>
										<p className="font-heading text-base font-bold text-navy-900">
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

				<DashCard title="Quick Actions">
					<div className="grid grid-cols-2 gap-3">
						{QUICK_ACTIONS.map((action) => (
							<button
								key={action.label}
								type="button"
								className="flex flex-col items-center gap-2 rounded-xl border border-slate-200 p-4 text-center text-xs font-medium text-navy-900 hover:border-green-500 hover:text-green-600"
							>
								<span className="text-green-600">{action.icon}</span>
								{action.label}
							</button>
						))}
					</div>
				</DashCard>
			</div>
		</DashboardShell>
	);
}
