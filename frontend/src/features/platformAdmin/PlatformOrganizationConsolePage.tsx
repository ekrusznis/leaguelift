import { useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { ErrorState } from "../../components/states/ErrorState";
import { LoadingState } from "../../components/states/LoadingState";
import { formatMoneyMinorUnits } from "../../lib/money";
import { appPaths, type OrganizationSection } from "../../routes/appPaths";
import { useCurrentSupportAccess, usePlatformAdminOrganization, useStartSupportAccess } from "./api";

const MODULES: Array<{ section: OrganizationSection; label: string; description: string; countKey?: keyof Counts }> = [
	{ section: "overview", label: "Organization overview", description: "Profile, onboarding, and workspace entry points." },
	{ section: "teams", label: "Teams", description: "Teams, assigned staff, rosters, pages, and schedules.", countKey: "teams" },
	{ section: "tournaments", label: "Tournaments", description: "Tournament records, pages, and child events.", countKey: "tournaments" },
	{ section: "households", label: "Households & athletes", description: "Households, guardians, athletes, and assignments.", countKey: "households" },
	{ section: "events", label: "Events", description: "Manual and imported organization events.", countKey: "events" },
	{ section: "fees", label: "Fees & payments", description: "Fee templates, assignments, payments, and collections." },
	{ section: "fundraising", label: "Fundraising", description: "Campaigns and contributions.", countKey: "campaigns" },
	{ section: "stores", label: "Stores & orders", description: "Stores, products, orders, and fulfillment.", countKey: "stores" },
	{ section: "sponsorships", label: "Sponsorships", description: "Packages, sponsors, approvals, and refunds.", countKey: "sponsorships" },
	{ section: "reports", label: "Reports", description: "Revenue, fees, refunds, and product performance." },
	{ section: "documents", label: "Documents", description: "Organization and household documents.", countKey: "documents" },
	{ section: "members", label: "Members & invitations", description: "Organization access and pending invitations.", countKey: "activeMembers" },
	{ section: "settings", label: "Settings & integrations", description: "Branding, public pages, payouts, and connections." },
];

type Counts = {
	teams: number;
	tournaments: number;
	households: number;
	events: number;
	campaigns: number;
	stores: number;
	sponsorships: number;
	documents: number;
	activeMembers: number;
};

export function PlatformOrganizationConsolePage() {
	const { organizationId } = useParams<{ organizationId: string }>();
	const organization = usePlatformAdminOrganization(organizationId);
	const currentAccess = useCurrentSupportAccess();
	const startAccess = useStartSupportAccess(organizationId ?? "");
	const [reason, setReason] = useState("");
	const navigate = useNavigate();

	if (!organizationId) return <ErrorState message="No organization selected." />;
	const selectedOrganizationId = organizationId;
	if (organization.isLoading || currentAccess.isLoading) return <LoadingState label="Loading organization console…" />;
	if (organization.isError || !organization.data) return <ErrorState message="Could not load this organization." onRetry={() => organization.refetch()} />;

	const data = organization.data;
	const hasActiveAccess = currentAccess.data?.organizationId === organizationId && currentAccess.data.status === "ACTIVE";
	const activeForAnotherOrganization = currentAccess.data && currentAccess.data.organizationId !== organizationId;
	const counts: Counts = data;

	function beginSupportAccess() {
		startAccess.mutate(reason, {
			onSuccess: () => navigate(appPaths.organization(selectedOrganizationId, "overview")),
		});
	}

	return (
		<div className="flex flex-col gap-6">
			<div className="flex flex-wrap items-start justify-between gap-4">
				<div>
					<Link to="/app/platform/organizations" className="text-sm font-medium text-azure-blue hover:underline">← All organizations</Link>
					<h1 className="mt-2 font-heading text-2xl font-bold text-navy-900">{data.name}</h1>
					<p className="mt-1 text-slate-500">/{data.slug} · {data.organizationType.replaceAll("_", " ")} · {data.status}</p>
				</div>
				{hasActiveAccess && (
					<Link to={appPaths.organization(organizationId, "overview")} className="rounded-md bg-green-600 px-4 py-2.5 font-semibold text-white hover:bg-green-700">
						Open organization workspace
					</Link>
				)}
			</div>

			<section className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4" aria-label="Organization totals">
				<Metric label="Active members" value={data.activeMembers} detail={`${data.invitedMembers} invited`} />
				<Metric label="Teams & tournaments" value={data.teams + data.tournaments} detail={`${data.teams} teams · ${data.tournaments} tournaments`} />
				<Metric label="Households & athletes" value={data.households + data.participants} detail={`${data.guardians} guardians · ${data.participants} athletes`} />
				<Metric label="Events" value={data.events} detail={`${data.activeEventConnections} active source connections`} />
				<Metric label="Stores & orders" value={data.stores + data.orders} detail={`${data.stores} stores · ${data.orders} orders`} />
				<Metric label="Campaign activity" value={data.campaigns + data.contributions} detail={`${data.campaigns} campaigns · ${data.contributions} contributions`} />
				<Metric label="Gross volume" value={formatMoneyMinorUnits(data.grossVolumeMinor, "USD")} detail={`${formatMoneyMinorUnits(data.refundedMinor, "USD")} refunded`} />
				<Metric label="Organization earnings" value={formatMoneyMinorUnits(data.organizationEarningsMinor, "USD")} detail={`${data.sponsorships} sponsorship records`} />
			</section>

			<section className="grid gap-4 rounded-xl border border-slate-200 bg-white p-5 lg:grid-cols-2">
				<div>
					<h2 className="font-heading text-lg font-semibold text-navy-900">Customer contacts</h2>
					<dl className="mt-3 grid gap-3 text-sm sm:grid-cols-2">
						<Detail label="Owner" value={data.ownerNames.join(", ") || "No active owner"} />
						<Detail label="Owner email" value={data.ownerEmails.join(", ") || "Not available"} />
						<Detail label="Organization email" value={data.contactEmail ?? "Not set"} />
						<Detail label="Organization phone" value={data.contactPhone ?? "Not set"} />
					</dl>
				</div>
				<div>
					<h2 className="font-heading text-lg font-semibold text-navy-900">Support access</h2>
					{hasActiveAccess ? (
						<div className="mt-3 rounded-lg border border-green-200 bg-green-50 p-4 text-sm text-green-900">
							<p className="font-semibold">Active until {new Date(currentAccess.data!.expiresAt).toLocaleString()}</p>
							<p className="mt-1">{currentAccess.data!.reason}</p>
						</div>
					) : (
						<div className="mt-3 flex flex-col gap-3">
							{activeForAnotherOrganization && <p className="rounded-md bg-amber-50 p-3 text-sm text-amber-900">Starting this session will end your active access to {currentAccess.data!.organizationName}.</p>}
							<label className="flex flex-col gap-1 text-sm font-medium text-navy-900">
								Reason for access
								<textarea value={reason} onChange={(event) => setReason(event.target.value)} rows={3} maxLength={500} placeholder="Example: Help the owner resolve a failed roster import and verify the affected households." className="rounded-md border border-slate-300 px-3 py-2 font-normal" />
							</label>
							<p className="text-xs text-slate-500">Required, audited, and limited to two hours. You remain the actor; this does not impersonate the customer.</p>
							<button type="button" disabled={reason.trim().length < 10 || startAccess.isPending} onClick={beginSupportAccess} className="self-start rounded-md bg-navy-900 px-4 py-2.5 font-semibold text-white hover:bg-navy-800 disabled:cursor-not-allowed disabled:opacity-50">
								{startAccess.isPending ? "Starting access…" : "Start support access"}
							</button>
							{startAccess.isError && <p role="alert" className="text-sm text-error-600">Could not start support access. Check the reason and try again.</p>}
						</div>
					)}
				</div>
			</section>

			<section>
				<h2 className="font-heading text-lg font-semibold text-navy-900">Organization workspace</h2>
				<p className="mt-1 text-sm text-slate-500">These destinations reuse the organization’s real pages and backend authorization. Start support access before opening customer data.</p>
				<div className="mt-4 grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
					{MODULES.map((module) => {
						const count = module.countKey ? counts[module.countKey] : null;
						const content = (
							<>
								<div className="flex items-start justify-between gap-3">
									<h3 className="font-semibold text-navy-900">{module.label}</h3>
									{count !== null && <span className="rounded-full bg-ice-50 px-2 py-0.5 text-xs font-semibold text-slate-600">{count}</span>}
								</div>
								<p className="mt-1 text-sm text-slate-500">{module.description}</p>
							</>
						);
						return hasActiveAccess ? (
							<Link key={module.section} to={appPaths.organization(organizationId, module.section)} className="rounded-xl border border-slate-200 bg-white p-4 hover:border-green-500 hover:shadow-sm">{content}</Link>
						) : (
							<div key={module.section} aria-disabled="true" className="rounded-xl border border-slate-200 bg-slate-50 p-4 opacity-65">{content}</div>
						);
					})}
				</div>
			</section>
		</div>
	);
}

function Metric({ label, value, detail }: { label: string; value: number | string; detail: string }) {
	return <div className="rounded-xl border border-slate-200 bg-white p-4"><p className="text-xs font-medium uppercase tracking-wide text-slate-500">{label}</p><p className="mt-2 font-heading text-2xl font-bold text-navy-900">{value}</p><p className="mt-1 text-xs text-slate-500">{detail}</p></div>;
}

function Detail({ label, value }: { label: string; value: string }) {
	return <div><dt className="text-xs font-medium uppercase tracking-wide text-slate-500">{label}</dt><dd className="mt-1 break-words text-navy-900">{value}</dd></div>;
}
