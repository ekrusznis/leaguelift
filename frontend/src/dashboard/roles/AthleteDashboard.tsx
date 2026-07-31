import { Link } from "react-router-dom";
import { DashboardShell, type DashNavItem } from "../DashboardShell";
import { useContexts } from "../../authorization/api";
import { capabilitiesFor } from "../../authorization/capabilities";
import { navItemsFor } from "../registry/navRegistry";
import { visibleWidgetIds } from "../registry/widgetRegistry";
import { DashCard } from "../components/DashCard";
import { DashboardPageHeader } from "../components/DashboardPageHeader";
import { Pill } from "../components/Pill";
import { CardQuery } from "../components/CardQuery";
import { sidebarPromoBackground } from "../demoAssets";
import { useAuth } from "../../auth/AuthContext";
import { appPaths } from "../../routes/appPaths";
import {
	useAthleteGuardians,
	useAthleteOverview,
	useAthleteRecentHistory,
	useAthleteTeams,
	useAthleteWeekEvents,
} from "../api";
import {
	CalendarIcon,
	MailIcon,
	UserIcon,
	MapPinIcon,
} from "../icons";

/**
 * Athlete dashboard, wired to real per-card API calls, all of which are entirely demo
 * data — there is no product-sanctioned participant-login concept yet (DESIGN-DOC.md
 * section 4.6), so there's no real record to query. See AthleteDashboardService.
 */
export function AthleteDashboard() {
	const { user } = useAuth();
	const overview = useAthleteOverview();
	const teams = useAthleteTeams();
	const weekEvents = useAthleteWeekEvents();
	const recentHistory = useAthleteRecentHistory();
	const guardians = useAthleteGuardians();

	// Nav/widget visibility are registries filtered by real capabilities for the
	// caller's athlete self-link (DESIGN-DOC.md section 10.2/10.3) — not hardcoded
	// arrays. Athlete items are unconditional, matching this dashboard's
	// pre-migration nav/cards exactly.
	const contexts = useContexts();
	const athleteContext = contexts.data?.find((context) => context.contextType === "ATHLETE");
	const participantId = athleteContext?.resourceId ?? null;
	const organizationId = athleteContext?.organizationId ?? null;
	const athleteCapabilities = capabilitiesFor(contexts.data, "ATHLETE", participantId);
	const navItems: DashNavItem[] = navItemsFor("ATHLETE", athleteCapabilities, { participantId, organizationId }).map((item) => ({
		icon: item.icon,
		label: item.label,
		to: item.to,
	}));
	const visibleWidgets = visibleWidgetIds("ATHLETE", athleteCapabilities);
	const eventSearch = participantId ? new URLSearchParams({ participantId, returnTo: appPaths.dashboard() }) : undefined;

	return (
		<DashboardShell
			contextIcon={<UserIcon className="size-4" />}
			contextIconTone="bg-transparent"
			contextName={overview.data?.displayName ?? user?.displayName ?? "Athlete"}
			contextRole="Athlete"
			navItems={navItems}
			userName={user?.displayName ?? "Account"}
			promo={{
				heading: "Stay game ready.",
				copy: "View your schedule, teams, and event updates in one place.",
				linkLabel: "View Schedule",
				to: organizationId && participantId ? appPaths.participantEvents(organizationId, participantId) : appPaths.dashboard("athlete-week"),
				backgroundSrc: sidebarPromoBackground,
			}}
		>
			<DashboardPageHeader heading={`Welcome, ${user?.displayName ?? "Athlete"}`} description="Here is what is coming up for you this week." />

			<div className="grid gap-5 lg:grid-cols-3">
				{visibleWidgets.has("athlete.next-event") && (
					<DashCard id="athlete-next-event" title="Next Event" className="lg:col-span-1">
						<CardQuery query={overview} loadingLabel="Loading…">
							{(data) =>
								data.nextEvent ? (
									<div>
										<h4 className="font-heading text-xl font-extrabold text-navy-900">{data.nextEvent.title}</h4>
										<p className="text-sm text-slate-500">{data.nextEvent.subtitle}</p>
										<div className="mt-4 flex flex-col gap-2 text-sm text-slate-600">
											<span className="flex items-center gap-2">
												<CalendarIcon className="size-4 text-slate-400" /> {data.nextEvent.dateLabel}
											</span>
											<span className="flex items-center gap-2">
												<MapPinIcon className="size-4 text-slate-400" /> {data.nextEvent.location}
											</span>
										</div>
										{data.isDemoData && <p className="mt-3 text-xs text-slate-400">Demo data</p>}
									</div>
								) : (
									<p className="text-sm text-slate-500">No upcoming events.</p>
								)
							}
						</CardQuery>
					</DashCard>
				)}

				{visibleWidgets.has("athlete.my-teams") && (
					<DashCard id="athlete-teams" title="My Teams">
						<CardQuery query={teams} loadingLabel="Loading teams…" isEmpty={(items) => items.length === 0} emptyTitle="No teams yet">
							{(items) => (
								<ul className="flex flex-col gap-4">
									{items.map((team) => (
										<li key={team.detail} className="rounded-xl border border-slate-200 p-3">
											<p className="font-semibold text-navy-900">{team.name}</p>
											<p className="text-sm text-slate-500">{team.detail}</p>
											<p className="mt-2 text-sm text-slate-600">Coach {team.coachName}</p>
										</li>
									))}
								</ul>
							)}
						</CardQuery>
					</DashCard>
				)}

				{visibleWidgets.has("athlete.this-week") && (
					<DashCard id="athlete-week" title="This Week" action={organizationId && participantId ? { label: "View full schedule", to: appPaths.participantEvents(organizationId, participantId) } : undefined}>
						<CardQuery query={weekEvents} loadingLabel="Loading schedule…" isEmpty={(items) => items.length === 0} emptyTitle="Nothing scheduled">
							{(items) => (
								<ul className="flex flex-col gap-3">
									{items.map((event) => (
										<li key={event.id} className="flex items-center gap-3">
											<div className="flex w-12 shrink-0 flex-col items-center rounded-lg bg-ice-50 py-1 text-xs font-semibold text-slate-500">
												<span>{event.day}</span>
												<span className="font-heading text-base text-navy-900">{event.date}</span>
											</div>
											<div className="min-w-0 flex-1">
												{organizationId && eventSearch ? (
											<Link to={appPaths.event(organizationId, event.id, eventSearch)} className="truncate font-medium text-navy-900 hover:text-green-600 hover:underline">{event.title}</Link>
										) : (
											<p className="truncate font-medium text-navy-900">{event.title}</p>
										)}
												<p className="text-xs text-slate-500">{event.subtitle}</p>
											</div>
											<div className="text-right text-xs">
												<p className="font-semibold text-navy-900">{event.time}</p>
												{event.tag && <p className={event.tag === "Home" ? "text-green-600" : "text-info-600"}>{event.tag}</p>}
											</div>
										</li>
									))}
								</ul>
							)}
						</CardQuery>
					</DashCard>
				)}

				{visibleWidgets.has("athlete.recent-history") && (
					<DashCard id="athlete-history" title="Recent History">
						<CardQuery query={recentHistory} loadingLabel="Loading history…" isEmpty={(items) => items.length === 0} emptyTitle="No history yet">
							{(items) => (
								<ul className="flex flex-col gap-3">
									{items.map((item) => (
										<li key={item.id} className="flex items-center justify-between gap-3">
											<div className="min-w-0">
												<p className="truncate font-medium text-navy-900">{item.opponent}</p>
												<p className="text-xs text-slate-500">
													{item.dateLabel} · {item.location}
												</p>
											</div>
											<Pill tone={item.won === true ? "success" : item.won === false ? "error" : "neutral"}>{item.result}</Pill>
										</li>
									))}
								</ul>
							)}
						</CardQuery>
					</DashCard>
				)}

				{visibleWidgets.has("athlete.guardians") && (
					<DashCard id="athlete-profile" title="Guardians">
						<CardQuery query={guardians} loadingLabel="Loading guardians…" isEmpty={(items) => items.length === 0} emptyTitle="No guardians on file">
							{(items) => (
								<ul className="flex flex-col gap-4">
									{items.map((guardian) => (
										<li key={guardian.name} className="flex items-start gap-3">
											<div className="min-w-0 flex-1">
												<div className="flex items-center gap-2">
													<p className="font-medium text-navy-900">{guardian.name}</p>
													<Pill tone={guardian.role === "Primary Guardian" ? "success" : "neutral"}>{guardian.role}</Pill>
												</div>
												<p className="truncate text-xs text-slate-500">{guardian.email}</p>
												<p className="text-xs text-slate-500">{guardian.phone}</p>
											</div>
											<MailIcon className="size-4 shrink-0 text-slate-400" />
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
