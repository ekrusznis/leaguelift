import { DashboardShell, type DashNavItem } from "../DashboardShell";
import { DashCard } from "../components/DashCard";
import { DashboardPageHeader } from "../components/DashboardPageHeader";
import { Pill } from "../components/Pill";
import { CardQuery } from "../components/CardQuery";
import { sidebarPromoBackground } from "../demoAssets";
import { useAuth } from "../../auth/AuthContext";
import {
	useAthleteGuardians,
	useAthleteOrders,
	useAthleteOverview,
	useAthleteRecentHistory,
	useAthleteTeams,
	useAthleteWeekEvents,
} from "../api";
import {
	CalendarIcon,
	HistoryIcon,
	HomeIcon,
	MailIcon,
	PackageIcon,
	ShieldIcon,
	UserIcon,
	UsersIcon,
	MapPinIcon,
} from "../icons";

const NAV_ITEMS: DashNavItem[] = [
	{ icon: <HomeIcon className="size-5" />, label: "Overview", active: true },
	{ icon: <UserIcon className="size-5" />, label: "My Profile" },
	{ icon: <UsersIcon className="size-5" />, label: "My Teams" },
	{ icon: <CalendarIcon className="size-5" />, label: "Schedule" },
	{ icon: <HistoryIcon className="size-5" />, label: "History" },
	{ icon: <ShieldIcon className="size-5" />, label: "Guardians" },
	{ icon: <PackageIcon className="size-5" />, label: "Orders" },
];

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
	const orders = useAthleteOrders();

	return (
		<DashboardShell
			contextIcon={<UserIcon className="size-4" />}
			contextIconTone="bg-transparent"
			contextName={overview.data?.displayName ?? user?.displayName ?? "Athlete"}
			contextRole="Athlete"
			navItems={NAV_ITEMS}
			userName={user?.displayName ?? "Account"}
			promo={{
				heading: "Stay game ready.",
				copy: "View your schedule, gear, and updates all in one place.",
				linkLabel: "Explore Features",
				backgroundSrc: sidebarPromoBackground,
			}}
		>
			<DashboardPageHeader heading={`Welcome, ${user?.displayName ?? "Athlete"}`} description="Here is what is coming up for you this week." />

			<div className="grid gap-5 lg:grid-cols-3">
				<DashCard title="Next Event" className="lg:col-span-1">
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

				<DashCard title="My Teams" action={{ label: "View all teams" }}>
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

				<DashCard title="This Week" action={{ label: "View full schedule" }}>
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
											<p className="truncate font-medium text-navy-900">{event.title}</p>
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

				<DashCard title="Recent History" action={{ label: "View all history" }}>
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

				<DashCard title="Guardians" action={{ label: "View all" }}>
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

				<DashCard title="Orders" action={{ label: "View all orders" }}>
					<CardQuery query={orders} loadingLabel="Loading orders…" isEmpty={(items) => items.length === 0} emptyTitle="No orders yet">
						{(items) => (
							<ul className="flex flex-col gap-3">
								{items.map((order) => (
									<li key={order.id} className="flex items-center gap-3 rounded-xl border border-slate-200 p-3">
										<span className="flex size-10 shrink-0 items-center justify-center rounded-lg bg-ice-50 text-slate-400">
											<PackageIcon className="size-5" />
										</span>
										<div className="min-w-0 flex-1">
											<p className="truncate font-medium text-navy-900">{order.productName}</p>
											<p className="text-xs text-slate-500">
												{order.orderNumber} · {order.orderedAt}
											</p>
											<Pill tone="success">{order.status}</Pill>
										</div>
									</li>
								))}
							</ul>
						)}
					</CardQuery>
				</DashCard>
			</div>
		</DashboardShell>
	);
}
