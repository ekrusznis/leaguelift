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
import { visibleWidgetIds } from "../registry/widgetRegistry";
import {
	usePlatformOrdersSummary,
	usePlatformOrganizations,
	usePlatformOutboxHealth,
	usePlatformPaymentsSummary,
	usePlatformPayoutsSummary,
	usePlatformSummary,
	usePlatformWebhookHealth,
} from "../api";
import { useMyActivity } from "../../features/activity/api";
import { describeActivityAction, timeAgo } from "../activity";
import { formatMoneyMinorUnits } from "../../lib/money";
import { appPaths } from "../../routes/appPaths";
import { IconBadge } from "../components/IconBadge";
import { ChartIcon, ShieldIcon } from "../icons";

/**
 * Platform Administrator Dashboard (DESIGN-DOC.md section 10.2, new in Phase 7/
 * ADR-020 — did not exist before this slice; until this slice no account could ever
 * satisfy `CurrentUser.platformAdministrator`, so nothing could reach it). As of the
 * Phase 7 completion effort, Orders/Payments/Payouts/Audit are also real — see
 * `PlatformAdminDashboardService`'s class doc for exactly what's real vs. still left
 * out (Pilot Applications, Subscriptions, Feature Flags, Support have no backing
 * aggregate query and remain out).
 */
export function PlatformAdminDashboard() {
	const { user } = useAuth();
	const summary = usePlatformSummary();
	const organizations = usePlatformOrganizations();
	const webhookHealth = usePlatformWebhookHealth();
	const outboxHealth = usePlatformOutboxHealth();
	const ordersSummary = usePlatformOrdersSummary();
	const paymentsSummary = usePlatformPaymentsSummary();
	const payoutsSummary = usePlatformPayoutsSummary();
	const activity = useMyActivity();

	const contexts = useContexts();
	const platformCapabilities = capabilitiesFor(contexts.data, "PLATFORM_ADMIN", null);
	const navItems: DashNavItem[] = navItemsFor("PLATFORM_ADMIN", platformCapabilities, {}).map((item) => ({
		icon: item.icon,
		label: item.label,
		to: item.to,
	}));
	const visibleWidgets = visibleWidgetIds("PLATFORM_ADMIN", platformCapabilities);

	return (
		<DashboardShell
			contextIcon={<ShieldIcon className="size-4" />}
			contextIconTone="bg-navy-900"
			contextName="LeagueLift Platform"
			contextRole="Platform Admin"
			navItems={navItems}
			showSearch
			searchScope={{ kind: "platform" }}
			userName={user?.displayName ?? "Account"}
			promo={{
				heading: "Platform operations.",
				copy: "Monitor organizations, users, and integration health across LeagueLift.",
				linkLabel: "View Help Center",
				to: "/help",
				backgroundSrc: sidebarPromoBackground,
			}}
		>
			<DashboardPageHeader heading="Platform Overview" description="Cross-organization operational status." />

			<div className="grid gap-5 lg:grid-cols-3">
				{visibleWidgets.has("platform.summary") && (
					<DashCard id="platform-summary" title="Platform Summary" className="lg:col-span-1">
						<CardQuery query={summary} loadingLabel="Loading…">
							{(data) => (
								<div className="grid grid-cols-2 gap-3">
									<StatTile value={String(data.organizationCount)} label="Organizations" />
									<StatTile value={String(data.userCount)} label="Users" />
								</div>
							)}
						</CardQuery>
					</DashCard>
				)}

				{visibleWidgets.has("platform.webhook-health") && (
					<DashCard id="platform-operations" title="Webhook Health" className="lg:col-span-1">
						<CardQuery query={webhookHealth} loadingLabel="Loading…">
							{(data) => (
								<div className="grid grid-cols-2 gap-3 sm:grid-cols-3">
									<StatTile value={String(data.processed)} label="Processed" />
									<StatTile value={String(data.failed)} label="Failed" />
									<StatTile value={String(data.ignored)} label="Ignored" />
								</div>
							)}
						</CardQuery>
					</DashCard>
				)}

				{visibleWidgets.has("platform.outbox-health") && (
					<DashCard id="platform-outbox" title="Outbox Health" className="lg:col-span-1">
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
				)}

				{visibleWidgets.has("platform.orders") && (
					<DashCard id="platform-orders" title="Orders" className="lg:col-span-1">
						<CardQuery query={ordersSummary} loadingLabel="Loading…">
							{(data) => (
								<div className="grid grid-cols-2 gap-3 sm:grid-cols-3">
									<StatTile value={String(data.confirmed)} label="Confirmed" />
									<StatTile value={String(data.pending)} label="Pending" />
									<StatTile value={String(data.refunded)} label="Refunded" />
								</div>
							)}
						</CardQuery>
					</DashCard>
				)}

				{visibleWidgets.has("platform.payments") && (
					<DashCard id="platform-payments" title="Payments" action={{ label: "Open reports", to: appPaths.platformReports() }} className="lg:col-span-1">
						<CardQuery query={paymentsSummary} loadingLabel="Loading…">
							{(data) => (
								<div className="flex flex-col gap-2 text-sm">
									<div className="flex items-center justify-between">
										<span className="text-slate-500">Gross processed</span>
										<span className="font-heading font-bold text-navy-900">{formatMoneyMinorUnits(data.grossProcessedMinor, "USD")}</span>
									</div>
									<div className="flex items-center justify-between">
										<span className="text-slate-500">Platform fees collected</span>
										<span className="font-medium text-navy-900">{formatMoneyMinorUnits(data.platformFeesCollectedMinor, "USD")}</span>
									</div>
									<div className="flex items-center justify-between">
										<span className="text-slate-500">Refunded</span>
										<span className="font-medium text-error-600">{formatMoneyMinorUnits(data.refundedMinor, "USD")}</span>
									</div>
								</div>
							)}
						</CardQuery>
					</DashCard>
				)}

				{visibleWidgets.has("platform.payouts") && (
					<DashCard id="platform-payouts" title="Payouts" className="lg:col-span-1">
						<CardQuery query={payoutsSummary} loadingLabel="Loading…">
							{(data) => (
								<div className="grid grid-cols-2 gap-3">
									<StatTile value={String(data.organizationsPayoutEnabled)} label="Orgs payout-enabled" />
									<StatTile value={formatMoneyMinorUnits(data.totalTransferredMinor, "USD")} label="Total transferred" />
								</div>
							)}
						</CardQuery>
					</DashCard>
				)}

				{visibleWidgets.has("platform.audit") && (
					<DashCard id="platform-audit" title="Audit" className="lg:col-span-3">
						<CardQuery
							query={activity}
							loadingLabel="Loading activity…"
							isEmpty={(data) => data.items.length === 0}
							emptyTitle="No recent activity"
						>
							{(data) => (
								<ul className="flex flex-col gap-4">
									{data.items.slice(0, 10).map((item) => (
										<li key={item.id} className="flex items-start gap-3">
											<IconBadge icon={<ChartIcon className="size-4" />} tone="info" />
											<div className="min-w-0 flex-1">
												<p className="text-sm font-medium text-navy-900">{describeActivityAction(item.action)}</p>
												<p className="text-xs text-slate-500">{item.organizationName ?? item.entityType}</p>
											</div>
											<span className="shrink-0 text-xs text-slate-400">{timeAgo(item.occurredAt)}</span>
										</li>
									))}
								</ul>
							)}
						</CardQuery>
					</DashCard>
				)}

				{visibleWidgets.has("platform.organizations") && (
					<DashCard id="platform-organizations" title="Organizations" action={{ label: "View all", to: appPaths.platformOrganizations() }} className="lg:col-span-3">
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
				)}
			</div>
		</DashboardShell>
	);
}
