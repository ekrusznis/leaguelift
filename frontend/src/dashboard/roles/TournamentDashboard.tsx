import { Link } from "react-router-dom";
import { DashboardShell, type DashNavItem } from "../DashboardShell";
import { DashCard } from "../components/DashCard";
import { DashboardPageHeader } from "../components/DashboardPageHeader";
import { Pill } from "../components/Pill";
import { CardQuery } from "../components/CardQuery";
import { sidebarPromoBackground } from "../demoAssets";
import { useAuth } from "../../auth/AuthContext";
import { appPaths } from "../../routes/appPaths";
import { PrimaryButton } from "../../marketing/components/buttons";
import { useContexts } from "../../authorization/api";
import { capabilitiesFor } from "../../authorization/capabilities";
import { navItemsFor } from "../registry/navRegistry";
import { visibleWidgetIds } from "../registry/widgetRegistry";
import { useTournamentPageStatus, useTournamentSummary } from "../api";
import { TrophyIcon } from "../icons";

/**
 * Tournament Dashboard (DESIGN-DOC.md section 10.2, new in Phase 7/ADR-020 — did not
 * exist before this slice). Deliberately minimal: only the tournament-identity and
 * public-page-status cards are wired, both entirely real — see
 * `TournamentDashboardService`'s class doc for what's left out and why (no
 * `tournament_team` join table exists yet, so Participating Teams/Divisions/Apparel/
 * Fundraising/Sponsors/Orders/Reports would have nothing real to show).
 */
export function TournamentDashboard({ organizationId, tournamentId }: { organizationId: string; tournamentId: string }) {
	const { user } = useAuth();
	const summary = useTournamentSummary(organizationId, tournamentId);
	const pageStatus = useTournamentPageStatus(organizationId, tournamentId);

	const contexts = useContexts();
	const tournamentCapabilities = capabilitiesFor(contexts.data, "TOURNAMENT", tournamentId);
	const navItems: DashNavItem[] = navItemsFor("TOURNAMENT", tournamentCapabilities, { organizationId, tournamentId }).map((item) => ({
		icon: item.icon,
		label: item.label,
		to: item.to,
	}));
	const visibleWidgets = visibleWidgetIds("TOURNAMENT", tournamentCapabilities);

	return (
		<DashboardShell
			contextIcon={<TrophyIcon className="size-4" />}
			contextIconTone="bg-navy-900"
			contextName={summary.data?.name ?? "Tournament"}
			contextRole="Tournament Admin"
			navItems={navItems}
			userName={user?.displayName ?? "Account"}
			userAvatarSrc={user?.avatarUrl ?? undefined}
			promo={{
				heading: "Run a tournament families love.",
				copy: "Keep the tournament page and changing event schedule easy to find.",
				linkLabel: "View Schedule",
				to: appPaths.tournamentEvents(organizationId, tournamentId),
				backgroundSrc: sidebarPromoBackground,
			}}
		>
			<DashboardPageHeader heading="Tournament Overview" description="Here's what's happening with your tournament." actions={<PrimaryButton icon="none" to={appPaths.tournamentEvents(organizationId, tournamentId)}>Manage Schedule</PrimaryButton>} />

			<div className="grid gap-5 lg:grid-cols-2">
				{visibleWidgets.has("tournament.summary") && (
					<DashCard id="tournament-summary" title="Tournament">
						<CardQuery query={summary} loadingLabel="Loading tournament…">
							{(data) => (
								<div>
									<div className="flex items-center justify-between">
										<p className="font-heading text-xl font-extrabold text-navy-900 dark:text-[#f8fafc]">{data.name}</p>
										<Pill tone={data.status === "ACTIVE" ? "success" : "neutral"}>{data.status}</Pill>
									</div>
									<p className="mt-1 text-sm text-slate-500 dark:text-[#cbd5e1]">{data.sport ?? "Multi-sport"}</p>
									<div className="mt-3 flex flex-col gap-1 text-sm text-slate-600 dark:text-[#cbd5e1]">
										{data.startDate && (
											<span>
												{data.startDate}
												{data.endDate ? ` – ${data.endDate}` : ""}
											</span>
										)}
										{data.location && <span>{data.location}</span>}
									</div>
								</div>
							)}
						</CardQuery>
					</DashCard>
				)}

				{visibleWidgets.has("tournament.page-status") && (
					<DashCard id="tournament-page" title="Tournament Page Status">
						<CardQuery query={pageStatus} loadingLabel="Loading page status…">
							{(data) => (
								<div className="rounded-xl bg-navy-900 p-4 text-white">
									<div className="flex items-center justify-between">
										<p className="font-heading font-bold">{data.tournamentName}</p>
										<Pill tone={data.status === "PUBLISHED" ? "success" : "neutral"}>{data.status}</Pill>
									</div>
									{data.slug && (
									<div className="mt-2 flex items-center justify-between gap-3">
										<p className="text-xs text-slate-400">/{data.slug}</p>
										<Link to={`/p/${data.slug}`} className="text-xs font-semibold text-green-400 hover:underline">View page</Link>
									</div>
								)}
								</div>
							)}
						</CardQuery>
					</DashCard>
				)}
			</div>
		</DashboardShell>
	);
}
