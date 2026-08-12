import { useState } from "react";
import { Link } from "react-router-dom";
import { DashboardShell, type DashNavItem } from "../DashboardShell";
import { DashCard } from "../components/DashCard";
import { DashboardPageHeader } from "../components/DashboardPageHeader";
import { StatTile } from "../components/StatTile";
import { Pill } from "../components/Pill";
import { ProgressBar } from "../components/ProgressBar";
import { CardQuery } from "../components/CardQuery";
import { PrimaryButton } from "../../marketing/components/buttons";
import { adultAvatars, sidebarPromoBackground } from "../demoAssets";
import { useAuth } from "../../auth/AuthContext";
import { formatMoneyMinorUnits } from "../../lib/money";
import { appPaths } from "../../routes/appPaths";
import { useContexts } from "../../authorization/api";
import { capabilitiesFor } from "../../authorization/capabilities";
import { navItemsFor } from "../registry/navRegistry";
import { visibleWidgetIds } from "../registry/widgetRegistry";
import {
	useCoachFundraisingProgress,
	useCoachRosterSummary,
	useCoachTeamPageStatus,
	useCoachTeamSchedule,
	useCoachTeams,
} from "../api";
import { CheckCircleIcon, UsersIcon } from "../icons";

/** Coach dashboard, wired to real per-card API calls (DESIGN-DOC.md section 10.1/10.2). */
export function CoachDashboard({ organizationId }: { organizationId: string }) {
	const { user } = useAuth();
	const teams = useCoachTeams(organizationId);

	// A coach with multiple assigned teams picks which one Team Page Status/
	// Fundraising Progress (and their capability gating below) reflect — the team
	// selector ADR-020 deferred. `teams.data` is already sorted by name
	// (CoachDashboardService.getTeams), so the default with no explicit selection
	// matches the backend's own alphabetically-first default exactly.
	const [selectedTeamId, setSelectedTeamId] = useState<string | null>(null);
	const primaryTeamId = teams.data?.[0]?.teamId ?? null;
	const activeTeamId = selectedTeamId ?? primaryTeamId;

	const teamSchedule = useCoachTeamSchedule(organizationId, activeTeamId);
	const rosterSummary = useCoachRosterSummary(organizationId, activeTeamId);
	const teamPageStatus = useCoachTeamPageStatus(organizationId, activeTeamId);
	const fundraisingProgress = useCoachFundraisingProgress(organizationId, activeTeamId);

	// Nav/widget visibility are registries filtered by real capabilities for the
	// active team (DESIGN-DOC.md section 10.2/10.3) — not hardcoded arrays.
	// Fees/Members/Settings only appear at TEAM_MANAGER tier. Team Page Status/
	// Fundraising Progress actions are similarly gated inline, matching
	// DESIGN-DOC.md section 10.2's "edit/manage gated by capability" note for those
	// two widgets specifically.
	const contexts = useContexts();
	const teamCapabilities = capabilitiesFor(contexts.data, "TEAM", activeTeamId);
	const navItems: DashNavItem[] = navItemsFor("TEAM", teamCapabilities, { organizationId, teamId: activeTeamId }).map((item) => ({
		icon: item.icon,
		label: item.label,
		to: item.to,
	}));
	const visibleWidgets = visibleWidgetIds("TEAM", teamCapabilities);

	return (
		<DashboardShell
			contextIcon={<UsersIcon className="size-4" />}
			contextIconTone="bg-navy-900"
			contextName="My Teams"
			contextRole="Coach"
			navItems={navItems}
			userName={user?.displayName ?? "Account"}
			userAvatarSrc={adultAvatars.coachJordan}
			promo={{
				heading: "Build a stronger team on and off the field.",
				copy: "Keep schedules, roster summaries, team pages, and fundraising connected.",
				linkLabel: "View Schedule",
				to: activeTeamId ? appPaths.teamEvents(organizationId, activeTeamId) : appPaths.dashboard("coach-teams"),
				backgroundSrc: sidebarPromoBackground,
			}}
		>
			<DashboardPageHeader
				heading="Team Overview"
				description="Here's what's happening with your teams."
				actions={
					<>
						{teams.data && teams.data.length > 1 && (
							<label className="flex items-center gap-2 text-sm text-slate-600 dark:text-[#cbd5e1]">
								<span className="sr-only">Viewing team</span>
								<select
									value={activeTeamId ?? ""}
									onChange={(event) => setSelectedTeamId(event.target.value)}
									className="rounded-lg border border-slate-200 dark:border-[#334155] bg-white dark:bg-[#111827] px-3 py-2 text-sm font-medium text-navy-900 dark:text-[#f8fafc]"
								>
									{teams.data.map((team) => (
										<option key={team.teamId} value={team.teamId}>
											{team.name}
										</option>
									))}
								</select>
							</label>
						)}
						{activeTeamId && (
							<PrimaryButton icon="none" to={appPaths.teamEvents(organizationId, activeTeamId)}>
								Manage Schedule
							</PrimaryButton>
						)}
					</>
				}
			/>

			<div className="grid gap-5 lg:grid-cols-3">
				{visibleWidgets.has("coach.my-teams") && (
					<DashCard id="coach-teams" title="My Teams">
						<CardQuery query={teams} loadingLabel="Loading teams…" isEmpty={(items) => items.length === 0} emptyTitle="No teams yet">
							{(items) => (
								<ul className="flex flex-col gap-3">
									{items.map((team) => (
										<li key={team.teamId} className="flex items-center justify-between gap-3 rounded-xl border border-slate-200 dark:border-[#334155] p-3">
											<div>
												<p className="font-medium text-navy-900 dark:text-[#f8fafc]">{team.name}</p>
												<p className="text-xs text-slate-500 dark:text-[#cbd5e1]">{team.sport}</p>
											</div>
											<StatTile value={String(team.participants)} label="Athletes" />
										</li>
									))}
								</ul>
							)}
						</CardQuery>
					</DashCard>
				)}

				{visibleWidgets.has("coach.team-schedule") && (
					<DashCard id="coach-schedule" title="Team Schedule" action={activeTeamId ? { label: "View full schedule", to: appPaths.teamEvents(organizationId, activeTeamId) } : undefined}>
						<CardQuery query={teamSchedule} loadingLabel="Loading schedule…" isEmpty={(items) => items.length === 0} emptyTitle="No upcoming events">
							{(items) => (
								<ul className="flex flex-col gap-3">
									{items.map((event) => (
										<li key={event.id} className="flex items-center gap-3">
											<div className="flex w-12 shrink-0 flex-col items-center rounded-lg bg-ice-50 dark:bg-[#0f172a] py-1 text-xs font-semibold text-slate-500 dark:text-[#cbd5e1]">
												<span>{event.day}</span>
												<span className="font-heading text-base text-navy-900 dark:text-[#f8fafc]">{event.date}</span>
											</div>
											<div className="min-w-0 flex-1">
												<Link to={appPaths.event(organizationId, event.id)} className="truncate font-medium text-navy-900 dark:text-[#f8fafc] hover:text-green-600 hover:underline">{event.title}</Link>
												<p className="text-xs text-slate-500 dark:text-[#cbd5e1]">{event.subtitle}</p>
											</div>
											{event.tag && <span className={`text-xs font-semibold ${event.tag === "Home" ? "text-green-600" : "text-info-600"}`}>{event.tag}</span>}
										</li>
									))}
								</ul>
							)}
						</CardQuery>
					</DashCard>
				)}

				{visibleWidgets.has("coach.roster-summary") && (
					<DashCard id="coach-roster" title="Roster Summary" action={activeTeamId ? { label: "View roster", to: appPaths.teamRoster(organizationId, activeTeamId) } : undefined}>
						<CardQuery query={rosterSummary} loadingLabel="Loading roster…">
							{(data) => (
								<>
									<div className="grid grid-cols-2 gap-3 sm:grid-cols-3">
										<StatTile value={String(data.athletes)} label="Athletes" />
										<StatTile value={String(data.attendanceRatePercent)} label="Attendance %" />
										<StatTile value={String(data.availabilityResponsePercent)} label="Availability %" />
									</div>
									<p className="mt-4 flex items-center gap-2 text-xs text-green-600">
										<CheckCircleIcon className="size-4" /> Roster count is live
										{data.isAttendanceDemoData && " · attendance figures are demo data"}
									</p>
								</>
							)}
						</CardQuery>
					</DashCard>
				)}

				{visibleWidgets.has("coach.team-page-status") && (
					<DashCard id="coach-team-page" title="Team Page Status">
						<CardQuery query={teamPageStatus} loadingLabel="Loading page status…">
							{(data) =>
								data ? (
									<div className="rounded-xl bg-navy-900 p-4 text-white">
										<div className="flex items-center justify-between">
											<p className="font-heading font-bold">{data.teamName}</p>
											<Pill tone={data.status === "PUBLISHED" ? "success" : "neutral"}>{data.status}</Pill>
										</div>
										{data.slug && (
											<div className="mt-2 flex items-center justify-between gap-3">
												<p className="text-xs text-slate-400">/{data.slug}</p>
												<Link to={`/p/${data.slug}`} className="text-xs font-semibold text-green-400 hover:underline">View page</Link>
											</div>
										)}
									</div>
								) : (
									<p className="text-sm text-slate-500 dark:text-[#cbd5e1]">No team page created yet.</p>
								)
							}
						</CardQuery>
					</DashCard>
				)}

				{visibleWidgets.has("coach.fundraising-progress") && (
					<DashCard id="coach-fundraising" title="Fundraising Progress">
						<CardQuery query={fundraisingProgress} loadingLabel="Loading fundraiser…">
							{(data) =>
								data ? (
									<>
										<div className="flex items-center justify-between">
											<p className="font-medium text-navy-900 dark:text-[#f8fafc]">{data.name}</p>
											<Pill tone="success">{data.status}</Pill>
										</div>
										<p className="mt-2 font-heading text-2xl font-extrabold text-navy-900 dark:text-[#f8fafc]">
											{formatMoneyMinorUnits(data.raisedMinor, data.currency)}{" "}
											<span className="text-sm font-normal text-slate-500 dark:text-[#cbd5e1]">raised of {formatMoneyMinorUnits(data.goalAmountMinor, data.currency)} goal</span>
										</p>
										<div className="mt-2">
											<ProgressBar percent={(data.raisedMinor / data.goalAmountMinor) * 100} />
										</div>
										{data.isRaisedDemoData && <p className="mt-2 text-xs text-slate-400">Raised amount is demo data — contribution recording isn't built yet.</p>}
									</>
								) : (
									<p className="text-sm text-slate-500 dark:text-[#cbd5e1]">No active fundraiser.</p>
								)
							}
						</CardQuery>
					</DashCard>
				)}


			</div>
		</DashboardShell>
	);
}
