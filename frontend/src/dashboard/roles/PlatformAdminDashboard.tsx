import { DashboardShell, type DashNavItem } from "../DashboardShell";
import { DashCard } from "../components/DashCard";
import { DashboardPageHeader } from "../components/DashboardPageHeader";
import { StatTile } from "../components/StatTile";
import { Pill } from "../components/Pill";
import { CardQuery } from "../components/CardQuery";
import { sidebarPromoBackground } from "../demoAssets";
import { useAuth } from "../../auth/AuthContext";
import { useContexts } from "../../authorization/api";
import { capabilitiesFor } from "../../authorization/capabilities";
import { navItemsFor } from "../registry/navRegistry";
import { usePlatformOrganizations, usePlatformOutboxHealth, usePlatformSummary, usePlatformWebhookHealth } from "../api";
import { ShieldIcon } from "../icons";

/**
 * Platform Administrator Dashboard (DESIGN-DOC.md section 10.2, new in Phase 7/
 * ADR-020 — did not exist before this slice; until this slice no account could ever
 * satisfy `CurrentUser.platformAdministrator`, so nothing could reach it). Deliberately
 * narrow — see `PlatformAdminDashboardService`'s class doc for the full nav list this
 * intentionally leaves out (Pilot Applications, Subscriptions, Payments, Payouts,
 * Orders, Audit, Feature Flags, Support all lack a backing aggregate query today).
 */
export function PlatformAdminDashboard() {
	const { user } = useAuth();
	const summary = usePlatformSummary();
	const organizations = usePlatformOrganizations();
	const webhookHealth = usePlatformWebhookHealth();
	const outboxHealth = usePlatformOutboxHealth();

	const contexts = useContexts();
	const platformCapabilities = capabilitiesFor(contexts.data, "PLATFORM_ADMIN", null);
	const navItems: DashNavItem[] = navItemsFor("PLATFORM_ADMIN", platformCapabilities).map((item, index) => ({
		icon: item.icon,
		label: item.label,
		active: index === 0,
	}));

	return (
		<DashboardShell
			contextIcon={<ShieldIcon className="size-4" />}
			contextIconTone="bg-navy-900"
			contextName="LeagueLift Platform"
			contextRole="Platform Admin"
			navItems={navItems}
			userName={user?.displayName ?? "Account"}
			promo={{
				heading: "Platform operations.",
				copy: "Monitor organizations, users, and integration health across LeagueLift.",
				linkLabel: "View runbook",
				backgroundSrc: sidebarPromoBackground,
			}}
		>
			<DashboardPageHeader heading="Platform Overview" description="Cross-organization operational status." />

			<div className="grid gap-5 lg:grid-cols-3">
				<DashCard title="Platform Summary" className="lg:col-span-1">
					<CardQuery query={summary} loadingLabel="Loading…">
						{(data) => (
							<div className="grid grid-cols-2 gap-3">
								<StatTile value={String(data.organizationCount)} label="Organizations" />
								<StatTile value={String(data.userCount)} label="Users" />
							</div>
						)}
					</CardQuery>
				</DashCard>

				<DashCard title="Webhook Health" className="lg:col-span-1">
					<CardQuery query={webhookHealth} loadingLabel="Loading…">
						{(data) => (
							<div className="grid grid-cols-3 gap-3">
								<StatTile value={String(data.processed)} label="Processed" />
								<StatTile value={String(data.failed)} label="Failed" />
								<StatTile value={String(data.ignored)} label="Ignored" />
							</div>
						)}
					</CardQuery>
				</DashCard>

				<DashCard title="Outbox Health" className="lg:col-span-1">
					<CardQuery query={outboxHealth} loadingLabel="Loading…">
						{(data) => (
							<div className="grid grid-cols-2 gap-3">
								<StatTile value={String(data.pending)} label="Pending" />
								<StatTile value={String(data.processing)} label="Processing" />
								<StatTile value={String(data.processed)} label="Processed" />
								<StatTile value={String(data.failed + data.deadLetter)} label="Failed / Dead-letter" />
							</div>
						)}
					</CardQuery>
				</DashCard>

				<DashCard title="Organizations" action={{ label: "View all" }} className="lg:col-span-3">
					<CardQuery query={organizations} loadingLabel="Loading organizations…" isEmpty={(items) => items.length === 0} emptyTitle="No organizations yet">
						{(items) => (
							<ul className="flex flex-col gap-3">
								{items.map((org) => (
									<li key={org.organizationId} className="flex items-center justify-between gap-3 rounded-xl border border-slate-200 p-3">
										<div className="min-w-0">
											<p className="truncate font-medium text-navy-900">{org.name}</p>
											<p className="truncate text-xs text-slate-500">
												/{org.slug} · {org.organizationType}
											</p>
										</div>
										<Pill tone={org.status === "ACTIVE" ? "success" : "neutral"}>{org.status}</Pill>
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
