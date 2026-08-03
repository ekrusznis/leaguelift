import { useEffect, useMemo, useState } from "react";
import { Button } from "../../components/Button";
import { ErrorState } from "../../components/states/ErrorState";
import { LoadingState } from "../../components/states/LoadingState";
import {
	usePreviewQuickBooksExport,
	useQuickBooksAccounts,
	useQuickBooksOverview,
	useRefreshQuickBooksCompany,
	useSaveQuickBooksMapping,
} from "./api";
import type { QuickBooksExportPreview, QuickBooksMappingType } from "./types";

const REQUIRED_MAPPINGS: { type: QuickBooksMappingType; label: string }[] = [
	{ type: "SALES_INCOME", label: "Sales income" },
	{ type: "CONTRIBUTION_INCOME", label: "Contributions" },
	{ type: "SPONSORSHIP_INCOME", label: "Sponsorship income" },
	{ type: "REFUNDS", label: "Refunds" },
	{ type: "FEES_RECEIVABLE", label: "Fees receivable" },
	{ type: "BANK_CLEARING", label: "Bank clearing" },
	{ type: "PAYOUT_CLEARING", label: "Payout clearing" },
];

function dateInputValue(date: Date) {
	return date.toISOString().slice(0, 10);
}

export function QuickBooksScaffoldPanel({ organizationId }: { organizationId: string }) {
	const query = useQuickBooksOverview(organizationId);
	const connectionId = query.data?.catalog.connection?.id ?? null;
	const connected = ["CONNECTED", "DEGRADED"].includes(query.data?.catalog.connection?.status ?? "");
	const accountsQuery = useQuickBooksAccounts(organizationId, connectionId, connected);
	const refreshCompany = useRefreshQuickBooksCompany(organizationId, connectionId);
	const saveMapping = useSaveQuickBooksMapping(organizationId, connectionId);
	const previewExport = usePreviewQuickBooksExport(organizationId, connectionId);
	const [selectedAccounts, setSelectedAccounts] = useState<Partial<Record<QuickBooksMappingType, string>>>({});
	const [periodStart, setPeriodStart] = useState(() => dateInputValue(new Date(new Date().getFullYear(), new Date().getMonth(), 1)));
	const [periodEnd, setPeriodEnd] = useState(() => dateInputValue(new Date()));
	const [preview, setPreview] = useState<QuickBooksExportPreview | null>(null);

	useEffect(() => {
		if (!query.data) return;
		setSelectedAccounts(Object.fromEntries(query.data.mappings.map((mapping) => [mapping.mappingType, mapping.externalAccountId])));
	}, [query.data]);

	const accountsById = useMemo(() => new Map((accountsQuery.data ?? []).map((account) => [account.id, account])), [accountsQuery.data]);

	if (query.isLoading) return <LoadingState label="Loading QuickBooks readiness…" />;
	if (query.isError || !query.data) return <ErrorState message="Could not load QuickBooks readiness." onRetry={() => query.refetch()} />;
	const { catalog, setting, mappings, recentBatches } = query.data;
	const status = catalog.connection?.status ?? catalog.readiness;

	async function save(type: QuickBooksMappingType) {
		const accountId = selectedAccounts[type];
		if (!accountId) return;
		await saveMapping.mutateAsync({ mappingType: type, accountId });
	}

	async function runPreview() {
		const result = await previewExport.mutateAsync({
			periodStart,
			periodEnd,
			idempotencyKey: `qb-preview-${crypto.randomUUID()}`,
		});
		setPreview(result);
	}

	return (
		<section className="rounded-xl border border-slate-200 bg-white p-5" aria-labelledby="quickbooks-heading">
			<div className="flex flex-wrap items-start justify-between gap-3">
				<div>
					<h3 id="quickbooks-heading" className="font-heading text-lg font-semibold text-navy-900">QuickBooks Online readiness</h3>
					<p className="mt-1 text-sm text-slate-500">Owner/admin organization connection. Phase 19 supports company discovery, reviewed mappings, and export previews only.</p>
				</div>
				<span className="rounded-full bg-slate-100 px-2.5 py-1 text-xs font-semibold text-slate-700">{status.replaceAll("_", " ")}</span>
			</div>
			<div className="mt-4 grid gap-4 md:grid-cols-2">
				<div className="rounded-lg border border-slate-200 p-4">
					<p className="text-xs font-semibold uppercase tracking-wide text-slate-500">Connected company</p>
					<p className="mt-2 font-medium text-navy-900">{setting?.companyName ?? "No company authorized"}</p>
					<p className="mt-1 text-sm text-slate-500">{setting?.realmId ? `Realm ${setting.realmId}` : catalog.activationRequirement}</p>
					{connected && <Button type="button" variant="secondary" className="mt-3" onClick={() => refreshCompany.mutate()} disabled={refreshCompany.isPending}>{refreshCompany.isPending ? "Refreshing…" : "Refresh company"}</Button>}
				</div>
				<div className="rounded-lg border border-slate-200 p-4">
					<p className="text-xs font-semibold uppercase tracking-wide text-slate-500">Export policy</p>
					<p className="mt-2 font-medium text-navy-900">Provider writes disabled</p>
					<p className="mt-1 text-sm text-slate-500">Sandbox verification and accounting approval are required before an export can be sent.</p>
				</div>
			</div>

			<div className="mt-5">
				<h4 className="font-medium text-navy-900">Chart-of-accounts mapping</h4>
				<p className="mt-1 text-sm text-slate-500">Mappings are stored now so activation does not require a new workflow. Account discovery remains unavailable until authorization succeeds.</p>
				{accountsQuery.isError && <p role="alert" className="mt-2 text-sm text-error-red">Could not read the connected company&apos;s accounts.</p>}
				<div className="mt-3 grid gap-3 md:grid-cols-2">
					{REQUIRED_MAPPINGS.map(({ type, label }) => {
						const existing = mappings.find((mapping) => mapping.mappingType === type);
						const selected = selectedAccounts[type] ?? "";
						return (
							<div key={type} className="rounded-lg border border-slate-200 p-3">
								<label htmlFor={`quickbooks-${type}`} className="text-sm font-medium text-navy-900">{label}</label>
								<select
									id={`quickbooks-${type}`}
									value={selected}
									onChange={(event) => setSelectedAccounts((current) => ({ ...current, [type]: event.target.value }))}
									disabled={!connected || accountsQuery.isLoading}
									className="mt-2 min-h-11 w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm text-navy-900 disabled:bg-slate-50"
								>
									<option value="">{existing ? existing.externalAccountName : "Choose an account"}</option>
									{(accountsQuery.data ?? []).map((account) => <option key={account.id} value={account.id}>{account.name} · {account.accountType}</option>)}
								</select>
								<div className="mt-2 flex items-center justify-between gap-2">
									<span className="text-xs text-slate-500">{existing ? `Mapped to ${existing.externalAccountName}` : "Required before export activation"}</span>
									<Button type="button" variant="secondary" onClick={() => void save(type)} disabled={!connected || !selected || saveMapping.isPending || accountsById.get(selected)?.active === false}>Save</Button>
								</div>
							</div>
						);
					})}
				</div>
			</div>

			<div className="mt-5 rounded-lg border border-slate-200 p-4">
				<h4 className="font-medium text-navy-900">Export-readiness preview</h4>
				<p className="mt-1 text-sm text-slate-500">Counts LeagueLift source records and reports missing mappings. It never sends data to QuickBooks.</p>
				<div className="mt-3 flex flex-wrap items-end gap-3">
					<div><label htmlFor="quickbooks-period-start" className="block text-sm font-medium text-navy-900">Period start</label><input id="quickbooks-period-start" type="date" value={periodStart} onChange={(event) => setPeriodStart(event.target.value)} className="mt-1 min-h-11 rounded-md border border-slate-300 px-3 py-2" /></div>
					<div><label htmlFor="quickbooks-period-end" className="block text-sm font-medium text-navy-900">Period end</label><input id="quickbooks-period-end" type="date" value={periodEnd} onChange={(event) => setPeriodEnd(event.target.value)} className="mt-1 min-h-11 rounded-md border border-slate-300 px-3 py-2" /></div>
					<Button type="button" onClick={() => void runPreview()} disabled={!connected || previewExport.isPending || !periodStart || !periodEnd}>{previewExport.isPending ? "Previewing…" : "Preview export"}</Button>
				</div>
				{previewExport.isError && <p role="alert" className="mt-3 text-sm text-error-red">Could not create the export preview.</p>}
				{preview && <div role="status" className="mt-3 rounded-md bg-ice-50 p-3 text-sm text-slate-700"><p className="font-medium text-navy-900">{preview.counts.total} candidate records</p><p className="mt-1">{preview.counts.contributions} contributions · {preview.counts.sponsorships} sponsorships · {preview.counts.orders} orders · {preview.counts.feePayments} fee payments · {preview.counts.corrections} corrections</p><p className="mt-1">{preview.reason}</p>{preview.missingMappings.length > 0 && <p className="mt-1">Missing: {preview.missingMappings.map((item) => item.replaceAll("_", " ")).join(", ")}</p>}</div>}
			</div>

			{recentBatches.length > 0 && <div className="mt-4"><h4 className="font-medium text-navy-900">Recent export previews</h4><ul className="mt-2 space-y-2">{recentBatches.slice(0, 5).map((batch) => <li key={batch.id} className="flex flex-wrap justify-between gap-2 rounded-md border border-slate-200 px-3 py-2 text-sm"><span>{batch.periodStart} – {batch.periodEnd}</span><span className="text-slate-500">{batch.candidateCount} candidates · {batch.status}</span></li>)}</ul></div>}
		</section>
	);
}
