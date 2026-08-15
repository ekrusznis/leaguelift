import type { ReactNode } from "react";
import { Link } from "react-router-dom";
import { useAuth } from "../../auth/AuthContext";
import { useContexts } from "../../authorization/api";
import { capabilitiesFor } from "../../authorization/capabilities";
import { describeActivityAction, timeAgo } from "../activity";
import {
	usePlatformOrdersSummary,
	usePlatformOutboxHealth,
	usePlatformPayoutsSummary,
	usePlatformSummary,
	usePlatformWebhookHealth,
} from "../api";
import { DashboardShell, type DashNavItem } from "../DashboardShell";
import { sidebarPromoBackground } from "../demoAssets";
import {
	AlertIcon,
	BuildingIcon,
	ChartIcon,
	CheckCircleIcon,
	DollarIcon,
	PackageIcon,
	ShieldIcon,
	UsersIcon,
} from "../icons";
import { navItemsFor } from "../registry/navRegistry";
import { DashCard } from "../components/DashCard";
import { IconBadge } from "../components/IconBadge";
import { Pill } from "../components/Pill";
import { useMyActivity } from "../../features/activity/api";
import {
	useCurrentSupportAccess,
	usePlatformAdminOrganization,
	usePlatformAdminOrganizations,
	usePlatformPayments,
	usePlatformSupportAccessList,
} from "../../features/platformAdmin/api";
import { SupportAccessBanner } from "../../features/platformAdmin/SupportAccessBanner";
import { appPaths } from "../../routes/appPaths";
import { formatMoneyMinorUnits } from "../../lib/money";

/**
 * Rally26 employee overview. The visual hierarchy intentionally follows the
 * approved Platform Admin preview while every number shown is backed by a real
 * platform endpoint. Trend lines and support-ticket counts are omitted until
 * historical metrics and a support-case model exist.
 */
export function PlatformAdminDashboard() {
	const { user } = useAuth();
	const contexts = useContexts();
	const platformCapabilities = capabilitiesFor(contexts.data, "PLATFORM_ADMIN", null);
	const navItems: DashNavItem[] = navItemsFor("PLATFORM_ADMIN", platformCapabilities, {}).map((item) => ({
		icon: item.icon,
		label: item.label,
		to: item.to,
	}));

	const summary = usePlatformSummary();
	const orders = usePlatformOrdersSummary();
	const webhook = usePlatformWebhookHealth();
	const outbox = usePlatformOutboxHealth();
	const payouts = usePlatformPayoutsSummary();
	const pendingPayments = usePlatformPayments({ status: "PENDING", page: 0, size: 1 });
	const activity = useMyActivity();
	const organizations = usePlatformAdminOrganizations({ page: 0, size: 5 });
	const supportSessions = usePlatformSupportAccessList({ status: "ACTIVE", page: 0, size: 1 });
	const supportAccess = useCurrentSupportAccess();
	const organizationSnapshot = usePlatformAdminOrganization(supportAccess.data?.organizationId);

	const integrationExceptions = (webhook.data?.failed ?? 0) + (outbox.data?.failed ?? 0) + (outbox.data?.deadLetter ?? 0);

	return (
		<DashboardShell
			contextIcon={<ShieldIcon className="size-4" />}
			contextIconTone="bg-green-500/15 text-green-400"
			contextName="Rally26 Platform"
			contextRole="Platform Admin"
			navItems={navItems}
			showSearch
			showHelp
			searchScope={{ kind: "platform" }}
			userName={user?.displayName ?? "Account"}
			userRole="Rally26 employee"
			promo={{
				heading: supportAccess.data ? "Support access active" : "Platform operations",
				copy: supportAccess.data
					? `${supportAccess.data.organizationName} until ${new Date(supportAccess.data.expiresAt).toLocaleTimeString([], { hour: "numeric", minute: "2-digit" })}.`
					: "Review customer health, start reasoned support access, and resolve operational exceptions.",
				linkLabel: supportAccess.data ? "Open organization" : "Browse organizations",
				to: supportAccess.data ? appPaths.platformOrganization(supportAccess.data.organizationId) : appPaths.platformOrganizations(),
				backgroundSrc: sidebarPromoBackground,
			}}
		>
			<div className="flex flex-col gap-5">
				{supportAccess.data && <SupportAccessBanner access={supportAccess.data} />}

				<div>
					<h1 className="font-heading text-2xl font-bold text-navy-900 dark:text-[#f8fafc]">Platform Overview</h1>
					<p className="mt-1 text-slate-500 dark:text-[#cbd5e1]">Cross-organization customer health, revenue activity, and support operations.</p>
				</div>

				<div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3 2xl:grid-cols-7">
					<KpiCard label="Total Organizations" value={summary.data?.organizationCount} icon={<BuildingIcon className="size-5" />} tone="success" detail="Customer organizations" />
					<KpiCard label="Total Users" value={summary.data?.userCount} icon={<UsersIcon className="size-5" />} tone="success" detail="Across every organization" />
					<KpiCard label="Active Support Sessions" value={supportSessions.data?.totalElements} icon={<ShieldIcon className="size-5" />} tone="info" detail="Reasoned employee access" />
					<KpiCard label="Pending Orders" value={orders.data?.pending} icon={<PackageIcon className="size-5" />} tone="warning" detail="Awaiting completion" />
					<KpiCard label="Pending Payments" value={pendingPayments.data?.totalElements} icon={<DollarIcon className="size-5" />} tone="warning" detail="Across every payment type" to={appPaths.platformPayments()} />
					<KpiCard label="Integration Exceptions" value={integrationExceptions} icon={<AlertIcon className="size-5" />} tone={integrationExceptions > 0 ? "error" : "success"} detail="Webhook and outbox failures" />
					<KpiCard label="Payout-enabled Orgs" value={payouts.data?.organizationsPayoutEnabled} icon={<DollarIcon className="size-5" />} tone="purple" detail="Ready for transfers" />
				</div>

				<div className="grid gap-5 xl:grid-cols-[minmax(0,1.35fr)_minmax(360px,0.85fr)]">
					<DashCard title="Organizations" action={{ label: "View all organizations", to: appPaths.platformOrganizations() }}>
						{organizations.isLoading ? (
							<p className="py-8 text-center text-sm text-slate-500 dark:text-[#cbd5e1]">Loading organizations…</p>
						) : organizations.isError || !organizations.data ? (
							<p className="py-8 text-center text-sm text-error-600">Organizations could not be loaded.</p>
						) : organizations.data.items.length === 0 ? (
							<p className="py-8 text-center text-sm text-slate-500 dark:text-[#cbd5e1]">No organizations yet.</p>
						) : (
							<div className="overflow-x-auto">
								<table className="w-full min-w-[760px] text-left text-sm">
									<thead className="border-b border-slate-200 dark:border-[#334155] text-xs uppercase tracking-wide text-slate-400">
										<tr>
											<th className="pb-3 font-medium">Organization</th>
											<th className="pb-3 font-medium">Owner</th>
											<th className="pb-3 font-medium">Status</th>
											<th className="pb-3 text-right font-medium">Members</th>
											<th className="pb-3 text-right font-medium">Teams</th>
											<th className="pb-3 text-right font-medium">Gross volume</th>
										</tr>
									</thead>
									<tbody className="divide-y divide-slate-100 dark:divide-[#334155]">
										{organizations.data.items.map((organization) => (
											<tr key={organization.organizationId}>
												<td className="py-3 pr-4">
													<Link to={appPaths.platformOrganization(organization.organizationId)} className="font-semibold text-navy-900 dark:text-[#f8fafc] hover:text-green-700 hover:underline">
														{organization.name}
													</Link>
													<p className="text-xs text-slate-500 dark:text-[#cbd5e1]">/{organization.slug}</p>
												</td>
												<td className="py-3 pr-4">
													<p className="font-medium text-navy-900 dark:text-[#f8fafc]">{organization.primaryOwnerName ?? "Not assigned"}</p>
													<p className="max-w-44 truncate text-xs text-slate-500 dark:text-[#cbd5e1]">{organization.primaryOwnerEmail ?? organization.contactEmail ?? "—"}</p>
												</td>
												<td className="py-3 pr-4"><Pill tone={organization.status === "ACTIVE" ? "success" : "neutral"}>{organization.status}</Pill></td>
												<td className="py-3 text-right font-medium text-navy-900 dark:text-[#f8fafc]">{organization.activeMembers.toLocaleString()}</td>
												<td className="py-3 text-right font-medium text-navy-900 dark:text-[#f8fafc]">{organization.teams.toLocaleString()}</td>
												<td className="py-3 text-right font-semibold text-navy-900 dark:text-[#f8fafc]">{formatMoneyMinorUnits(organization.grossVolumeMinor, "USD")}</td>
											</tr>
										))}
									</tbody>
								</table>
							</div>
						)}
					</DashCard>

					<OrganizationSnapshot
						accessOrganizationId={supportAccess.data?.organizationId}
						accessOrganizationName={supportAccess.data?.organizationName}
						loading={organizationSnapshot.isLoading}
						data={organizationSnapshot.data}
					/>
				</div>

				<div className="grid gap-5 xl:grid-cols-[minmax(0,1.05fr)_minmax(300px,0.55fr)_minmax(0,1fr)]">
					<DashCard title="Recent Audit Activity" action={{ label: "View full audit log", to: appPaths.platformAudit() }}>
						{activity.isLoading ? (
							<p className="py-8 text-center text-sm text-slate-500 dark:text-[#cbd5e1]">Loading activity…</p>
						) : !activity.data || activity.data.items.length === 0 ? (
							<p className="py-8 text-center text-sm text-slate-500 dark:text-[#cbd5e1]">No recent platform activity.</p>
						) : (
							<ul className="flex flex-col gap-4">
								{activity.data.items.slice(0, 5).map((item) => (
									<li key={item.id} className="flex items-start gap-3">
										<IconBadge icon={<ChartIcon className="size-4" />} tone="info" />
										<div className="min-w-0 flex-1">
											<p className="text-sm font-medium text-navy-900 dark:text-[#f8fafc]">{describeActivityAction(item.action)}</p>
											<p className="truncate text-xs text-slate-500 dark:text-[#cbd5e1]">{item.organizationName ?? item.entityType}</p>
										</div>
										<span className="shrink-0 text-xs text-slate-400">{timeAgo(item.occurredAt)}</span>
									</li>
								))}
							</ul>
						)}
					</DashCard>

					<SupportAccessCard access={supportAccess.data} />

					<DashCard title="Operational Queue Overview" action={{ label: "Review operations", to: appPaths.platformOperations() }}>
						<div className="divide-y divide-slate-100 dark:divide-[#334155]">
							<QueueRow label="Pending orders" value={orders.data?.pending} tone="warning" />
							<QueueRow label="Failed webhooks" value={webhook.data?.failed} tone="error" />
							<QueueRow label="Failed outbox events" value={outbox.data?.failed} tone="error" />
							<QueueRow label="Dead-letter events" value={outbox.data?.deadLetter} tone="error" />
						</div>
					</DashCard>
				</div>

				<DashCard title="Platform Health" action={{ label: "View integration operations", to: appPaths.platformOperations() }}>
					<div className="grid gap-3 md:grid-cols-2 xl:grid-cols-4">
						<HealthTile label="Webhook processing" healthy={(webhook.data?.failed ?? 0) === 0} detail={`${webhook.data?.processed ?? 0} processed`} />
						<HealthTile label="Async outbox" healthy={integrationExceptions === 0} detail={`${(outbox.data?.pending ?? 0) + (outbox.data?.processing ?? 0)} pending or processing`} />
						<HealthTile label="Order flow" healthy={(orders.data?.pending ?? 0) === 0} detail={`${orders.data?.pending ?? 0} pending`} />
						<HealthTile label="Payout readiness" healthy={(payouts.data?.organizationsPayoutEnabled ?? 0) > 0} detail={`${payouts.data?.organizationsPayoutEnabled ?? 0} organizations enabled`} />
					</div>
				</DashCard>
			</div>
		</DashboardShell>
	);
}

type KpiTone = "success" | "info" | "warning" | "error" | "purple";

function KpiCard({ label, value, icon, tone, detail, to }: { label: string; value: number | undefined; icon: ReactNode; tone: KpiTone; detail: string; to?: string }) {
	const tones: Record<KpiTone, string> = {
		success: "bg-green-500/15 text-green-700",
		info: "bg-info-600/12 text-info-600",
		warning: "bg-gold-500/15 text-warning-600",
		error: "bg-error-600/12 text-error-600",
		purple: "bg-purple-500/12 text-purple-600",
	};
	const Wrapper = to ? Link : "section";
	const wrapperProps = to ? ({ to, className: "block rounded-2xl border border-slate-200 dark:border-[#334155] bg-white dark:bg-[#111827] p-4 shadow-[0_8px_24px_rgba(11,31,51,0.05)] hover:border-green-500" } as any) : { className: "block rounded-2xl border border-slate-200 dark:border-[#334155] bg-white dark:bg-[#111827] p-4 shadow-[0_8px_24px_rgba(11,31,51,0.05)] hover:border-green-500" };
	return (
		<Wrapper {...wrapperProps}>
			<div className="flex items-start gap-3">
				<span className={`flex size-10 shrink-0 items-center justify-center rounded-full ${tones[tone]}`}>{icon}</span>
				<div className="min-w-0">
					<p className="text-xs font-medium text-slate-500 dark:text-[#cbd5e1]">{label}</p>
					<p className="mt-1 font-heading text-2xl font-bold text-navy-900 dark:text-[#f8fafc]">{value === undefined ? "—" : value.toLocaleString()}</p>
				</div>
			</div>
			<p className="mt-3 text-xs text-slate-400">{detail}</p>
		</Wrapper>
	);
}

function OrganizationSnapshot({ accessOrganizationId, accessOrganizationName, loading, data }: {
	accessOrganizationId: string | undefined;
	accessOrganizationName: string | undefined;
	loading: boolean;
	data: ReturnType<typeof usePlatformAdminOrganization>["data"];
}) {
	if (!accessOrganizationId) {
		return (
			<DashCard title="Organization Snapshot" action={{ label: "Select organization", to: appPaths.platformOrganizations() }}>
				<div className="flex min-h-52 flex-col items-center justify-center text-center">
					<IconBadge icon={<BuildingIcon className="size-4" />} tone="success" />
					<p className="mt-3 font-semibold text-navy-900 dark:text-[#f8fafc]">No support organization selected</p>
					<p className="mt-1 max-w-sm text-sm text-slate-500 dark:text-[#cbd5e1]">Start a reasoned support session to see the customer snapshot and open its workspace.</p>
				</div>
			</DashCard>
		);
	}

	return (
		<DashCard title={`Organization Snapshot: ${accessOrganizationName ?? "Selected organization"}`} action={{ label: "View organization", to: appPaths.platformOrganization(accessOrganizationId) }}>
			{loading || !data ? (
				<p className="py-12 text-center text-sm text-slate-500 dark:text-[#cbd5e1]">Loading organization snapshot…</p>
			) : (
				<div className="grid grid-cols-2 gap-3 sm:grid-cols-4 xl:grid-cols-2 2xl:grid-cols-4">
					<SnapshotMetric label="Teams" value={data.teams.toLocaleString()} />
					<SnapshotMetric label="Households" value={data.households.toLocaleString()} />
					<SnapshotMetric label="Athletes" value={data.participants.toLocaleString()} />
					<SnapshotMetric label="Events" value={data.events.toLocaleString()} />
					<SnapshotMetric label="Orders" value={data.orders.toLocaleString()} />
					<SnapshotMetric label="Gross volume" value={formatMoneyMinorUnits(data.grossVolumeMinor, "USD")} />
					<SnapshotMetric label="Org earnings" value={formatMoneyMinorUnits(data.organizationEarningsMinor, "USD")} />
					<SnapshotMetric label="Campaigns" value={data.campaigns.toLocaleString()} />
				</div>
			)}
		</DashCard>
	);
}

function SnapshotMetric({ label, value }: { label: string; value: string }) {
	return (
		<div className="rounded-xl border border-slate-200 dark:border-[#334155] bg-ice-50 dark:bg-[#0f172a] p-3">
			<p className="font-heading text-lg font-bold text-navy-900 dark:text-[#f8fafc]">{value}</p>
			<p className="mt-0.5 text-xs text-slate-500 dark:text-[#cbd5e1]">{label}</p>
		</div>
	);
}

function SupportAccessCard({ access }: { access: ReturnType<typeof useCurrentSupportAccess>["data"] }) {
	return (
		<DashCard title="Support Access">
			{access ? (
				<div className="rounded-xl border border-amber-300 bg-amber-50 dark:bg-amber-950 p-4">
					<p className="text-xs font-medium uppercase tracking-wide text-amber-700">Currently supporting</p>
					<p className="mt-2 font-heading text-lg font-bold text-navy-900 dark:text-[#f8fafc]">{access.organizationName}</p>
					<p className="mt-1 line-clamp-3 text-sm text-slate-600 dark:text-[#cbd5e1]">{access.reason}</p>
					<Link to={appPaths.platformOrganization(access.organizationId)} className="mt-4 flex min-h-10 items-center justify-center rounded-md bg-amber-800 px-3 py-2 text-sm font-semibold text-white hover:bg-amber-700">
						Open support workspace
					</Link>
					<p className="mt-3 text-center text-[11px] text-slate-500 dark:text-[#cbd5e1]">Access is logged and expires {new Date(access.expiresAt).toLocaleTimeString([], { hour: "numeric", minute: "2-digit" })}.</p>
				</div>
			) : (
				<div className="flex min-h-48 flex-col items-center justify-center text-center">
					<IconBadge icon={<ShieldIcon className="size-4" />} tone="warning" />
					<p className="mt-3 font-semibold text-navy-900 dark:text-[#f8fafc]">No active support session</p>
					<p className="mt-1 text-sm text-slate-500 dark:text-[#cbd5e1]">Select an organization and record a reason before viewing customer data.</p>
					<Link to={appPaths.platformOrganizations()} className="mt-4 rounded-md bg-navy-900 px-4 py-2 text-sm font-semibold text-white hover:bg-navy-800">Browse organizations</Link>
				</div>
			)}
		</DashCard>
	);
}

function QueueRow({ label, value, tone }: { label: string; value: number | undefined; tone: "warning" | "error" }) {
	return (
		<div className="flex items-center justify-between gap-4 py-3 first:pt-0 last:pb-0">
			<div className="flex items-center gap-3">
				<IconBadge icon={tone === "error" ? <AlertIcon className="size-4" /> : <PackageIcon className="size-4" />} tone={tone} />
				<span className="text-sm font-medium text-navy-900 dark:text-[#f8fafc]">{label}</span>
			</div>
			<span className="font-heading text-lg font-bold text-navy-900 dark:text-[#f8fafc]">{value === undefined ? "—" : value.toLocaleString()}</span>
		</div>
	);
}

function HealthTile({ label, healthy, detail }: { label: string; healthy: boolean; detail: string }) {
	return (
		<div className="flex items-start gap-3 rounded-xl border border-slate-200 dark:border-[#334155] bg-ice-50 dark:bg-[#0f172a] p-4">
			<IconBadge icon={healthy ? <CheckCircleIcon className="size-4" /> : <AlertIcon className="size-4" />} tone={healthy ? "success" : "error"} />
			<div>
				<p className="text-sm font-semibold text-navy-900 dark:text-[#f8fafc]">{label}</p>
				<p className="mt-1 text-xs text-slate-500 dark:text-[#cbd5e1]">{detail}</p>
				<p className={`mt-2 text-xs font-semibold ${healthy ? "text-green-700" : "text-error-600"}`}>{healthy ? "Healthy" : "Needs attention"}</p>
			</div>
		</div>
	);
}
