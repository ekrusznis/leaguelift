import { useState } from "react";
import { DashboardShell, type DashNavItem } from "../DashboardShell";
import { DashCard } from "../components/DashCard";
import { DashboardPageHeader } from "../components/DashboardPageHeader";
import { StatTile } from "../components/StatTile";
import { Pill } from "../components/Pill";
import { ProgressBar } from "../components/ProgressBar";
import { IconBadge } from "../components/IconBadge";
import { CardQuery } from "../components/CardQuery";
import { PrimaryButton, SecondaryLightButton } from "../../marketing/components/buttons";
import { adultAvatars, sidebarPromoBackground } from "../demoAssets";
import { useAuth } from "../../auth/AuthContext";
import { formatMoneyMinorUnits } from "../../lib/money";
import { useContexts } from "../../authorization/api";
import { capabilitiesFor } from "../../authorization/capabilities";
import { Capabilities } from "../../authorization/capabilityConstants";
import { navItemsFor } from "../registry/navRegistry";
import { visibleWidgetIds } from "../registry/widgetRegistry";
import {
	useCoachAnnouncements,
	useCoachFundraisingProgress,
	useCoachRequiredActions,
	useCoachRosterSummary,
	useCoachTeamPageStatus,
	useCoachTeamSchedule,
	useCoachTeams,
} from "../api";
import { CheckCircleIcon, FileTextIcon, MegaphoneIcon, PlusIcon, UsersIcon } from "../icons";

/** Coach dashboard, wired to real per-card API calls (DESIGN-DOC.md section 10.1/10.2). */
export function CoachDashboard({ organizationId }: { organizationId: string }) {
	const { user } = useAuth();
	const teams = useCoachTeams(organizationId);
	const teamSchedule = useCoachTeamSchedule(organizationId);
	const rosterSummary = useCoachRosterSummary(organizationId);

	// A coach with multiple assigned teams picks which one Team Page Status/
	// Fundraising Progress (and their capability gating below) reflect — the team
	// selector ADR-020 deferred. `teams.data` is already sorted by name
	// (CoachDashboardService.getTeams), so the default with no explicit selection
	// matches the backend's own alphabetically-first default exactly.
	const [selectedTeamId, setSelectedTeamId] = useState<string | null>(null);
	const primaryTeamId = teams.data?.[0]?.teamId ?? null;
	const activeTeamId = selectedTeamId ?? primaryTeamId;

	const teamPageStatus = useCoachTeamPageStatus(organizationId, activeTeamId);
	const fundraisingProgress = useCoachFundraisingProgress(organizationId, activeTeamId);
	const announcements = useCoachAnnouncements(organizationId);
	const requiredActions = useCoachRequiredActions(organizationId);

	// Nav/widget visibility are registries filtered by real capabilities for the
	// active team (DESIGN-DOC.md section 10.2/10.3) — not hardcoded arrays.
	// Fees/Members/Settings only appear at TEAM_MANAGER tier. Team Page Status/
	// Fundraising Progress actions are similarly gated inline, matching
	// DESIGN-DOC.md section 10.2's "edit/manage gated by capability" note for those
	// two widgets specifically.
	const contexts = useContexts();
	const teamCapabilities = capabilitiesFor(contexts.data, "TEAM", activeTeamId);
	const navItems: DashNavItem[] = navItemsFor("TEAM", teamCapabilities).map((item, index) => ({
		icon: item.icon,
		label: item.label,
		active: index === 0,
	}));
	const visibleWidgets = visibleWidgetIds("TEAM", teamCapabilities);
	const canEditTeamPage = teamCapabilities.has(Capabilities.TEAM_PAGE_EDIT);
	const canManageFundraising = teamCapabilities.has(Capabilities.TEAM_FUNDRAISING_MANAGE);

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
				copy: "LeagueLift helps you save time, communicate better, and bring your team together.",
				linkLabel: "Learn more",
				backgroundSrc: sidebarPromoBackground,
			}}
		>
			<DashboardPageHeader
				heading="Team Overview"
				description="Here's what's happening with your teams."
				actions={
					<>
						{teams.data && teams.data.length > 1 && (
							<label className="flex items-center gap-2 text-sm text-slate-600">
								<span className="sr-only">Viewing team</span>
								<select
									value={activeTeamId ?? ""}
									onChange={(event) => setSelectedTeamId(event.target.value)}
									className="rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm font-medium text-navy-900"
								>
									{teams.data.map((team) => (
										<option key={team.teamId} value={team.teamId}>
											{team.name}
										</option>
									))}
								</select>
							</label>
						)}
						<SecondaryLightButton icon="none">Message Team</SecondaryLightButton>
						<PrimaryButton icon="none">
							<PlusIcon className="size-4" /> New Announcement
						</PrimaryButton>
					</>
				}
			/>

			<div className="grid gap-5 lg:grid-cols-3">
				{visibleWidgets.has("coach.my-teams") && (
					<DashCard title="My Teams" action={{ label: "View all teams" }}>
						<CardQuery query={teams} loadingLabel="Loading teams…" isEmpty={(items) => items.length === 0} emptyTitle="No teams yet">
							{(items) => (
								<ul className="flex flex-col gap-3">
									{items.map((team) => (
										<li key={team.teamId} className="flex items-center justify-between gap-3 rounded-xl border border-slate-200 p-3">
											<div>
												<p className="font-medium text-navy-900">{team.name}</p>
												<p className="text-xs text-slate-500">{team.sport}</p>
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
					<DashCard title="Team Schedule" action={{ label: "View full schedule" }}>
						<CardQuery query={teamSchedule} loadingLabel="Loading schedule…" isEmpty={(items) => items.length === 0} emptyTitle="No upcoming events">
							{(items) => (
								<ul className="flex flex-col gap-3">
									{items.map((event) => (
										<li key={event.id} className="flex items-center gap-3">
											<div className="flex w-12 shrink-0 flex-col items-center rounded-lg bg-ice-50 py-1 text-xs font-semibold text-slate-500">
												<span>{event.day}</span>
												<span className="font-heading text-base text-navy-900">{event.date}</span>
											</div>
											<div className="min-w-0 flex-1">
												<p className="truncate font-medium text-navy-900">{event.title}</p>
												<p className="text-xs text-slate-500">{event.subtitle}</p>
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
					<DashCard title="Roster Summary" action={{ label: "View full roster" }}>
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
					<DashCard title="Team Page Status" action={canEditTeamPage ? { label: "Edit Page" } : undefined}>
						<CardQuery query={teamPageStatus} loadingLabel="Loading page status…">
							{(data) =>
								data ? (
									<div className="rounded-xl bg-navy-900 p-4 text-white">
										<div className="flex items-center justify-between">
											<p className="font-heading font-bold">{data.teamName}</p>
											<Pill tone={data.status === "PUBLISHED" ? "success" : "neutral"}>{data.status}</Pill>
										</div>
										{data.slug && <p className="text-xs text-slate-400">/{data.slug}</p>}
									</div>
								) : (
									<p className="text-sm text-slate-500">No team page created yet.</p>
								)
							}
						</CardQuery>
					</DashCard>
				)}

				{visibleWidgets.has("coach.fundraising-progress") && (
					<DashCard title="Fundraising Progress" action={canManageFundraising ? { label: "View all fundraisers" } : undefined}>
						<CardQuery query={fundraisingProgress} loadingLabel="Loading fundraiser…">
							{(data) =>
								data ? (
									<>
										<div className="flex items-center justify-between">
											<p className="font-medium text-navy-900">{data.name}</p>
											<Pill tone="success">{data.status}</Pill>
										</div>
										<p className="mt-2 font-heading text-2xl font-extrabold text-navy-900">
											{formatMoneyMinorUnits(data.raisedMinor, data.currency)}{" "}
											<span className="text-sm font-normal text-slate-500">raised of {formatMoneyMinorUnits(data.goalAmountMinor, data.currency)} goal</span>
										</p>
										<div className="mt-2">
											<ProgressBar percent={(data.raisedMinor / data.goalAmountMinor) * 100} />
										</div>
										{data.isRaisedDemoData && <p className="mt-2 text-xs text-slate-400">Raised amount is demo data — contribution recording isn't built yet.</p>}
									</>
								) : (
									<p className="text-sm text-slate-500">No active fundraiser.</p>
								)
							}
						</CardQuery>
					</DashCard>
				)}

				{visibleWidgets.has("coach.announcements") && (
					<DashCard title="Announcements" action={{ label: "View all" }} className="lg:col-span-2">
						<CardQuery query={announcements} loadingLabel="Loading announcements…" isEmpty={(items) => items.length === 0} emptyTitle="No announcements">
							{(items) => (
								<ul className="flex flex-col gap-4">
									{items.map((item) => (
										<li key={item.id} className="flex items-start gap-3">
											<IconBadge icon={<MegaphoneIcon className="size-4" />} tone="success" />
											<div className="min-w-0 flex-1">
												<div className="flex items-center gap-2">
													<p className="font-medium text-navy-900">{item.title}</p>
													{item.isNew && <Pill tone="success">New</Pill>}
												</div>
												<p className="text-sm text-slate-500">{item.body}</p>
											</div>
											<span className="shrink-0 text-xs text-slate-400">{item.postedLabel}</span>
										</li>
									))}
								</ul>
							)}
						</CardQuery>
					</DashCard>
				)}

				{visibleWidgets.has("coach.required-actions") && (
					<DashCard title="Required Actions" action={{ label: "View all" }}>
						<CardQuery query={requiredActions} loadingLabel="Loading…" isEmpty={(items) => items.length === 0} emptyTitle="Nothing pending">
							{(items) => (
								<ul className="flex flex-col gap-3">
									{items.map((action) => (
										<li key={action.id} className="flex items-center gap-3">
											<IconBadge icon={<FileTextIcon className="size-4" />} tone={action.tone === "warning" ? "warning" : action.tone === "error" ? "error" : "info"} />
											<div className="min-w-0 flex-1">
												<p className="truncate text-sm font-medium text-navy-900">{action.title}</p>
												<p className="truncate text-xs text-slate-500">{action.subtitle}</p>
											</div>
											<span className="shrink-0 text-xs font-semibold text-error-600">{action.dueLabel}</span>
										</li>
									))}
								</ul>
							)}
						</CardQuery>
					</DashCard>
				)}
			</div>
		</DashboardShell>
	);
}
