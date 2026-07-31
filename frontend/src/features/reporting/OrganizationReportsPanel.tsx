import { useState } from "react";
import { Button } from "../../components/Button";
import { ErrorState } from "../../components/states/ErrorState";
import { LoadingState } from "../../components/states/LoadingState";
import { formatMoneyMinorUnits } from "../../lib/money";
import {
	exportRevenueReport,
	useCampaignReport,
	useFeeCollectionsReport,
	useProductReport,
	useRefundsReport,
	useRevenueReport,
	type ReportRange,
} from "./api";

function defaultRange(): Required<ReportRange> {
	const to = new Date();
	const from = new Date(to);
	from.setDate(from.getDate() - 30);
	return { from: from.toISOString().slice(0, 10), to: to.toISOString().slice(0, 10) };
}

function saveBlob(blob: Blob, filename: string) {
	const url = URL.createObjectURL(blob);
	const anchor = document.createElement("a");
	anchor.href = url;
	anchor.download = filename;
	document.body.appendChild(anchor);
	anchor.click();
	anchor.remove();
	URL.revokeObjectURL(url);
}

export function OrganizationReportsPanel({ organizationId }: { organizationId: string }) {
	const [range, setRange] = useState(defaultRange);
	const [exportError, setExportError] = useState<string | null>(null);
	const revenue = useRevenueReport(organizationId, range);
	const campaigns = useCampaignReport(organizationId, range);
	const products = useProductReport(organizationId, range);
	const refunds = useRefundsReport(organizationId, range);
	const fees = useFeeCollectionsReport(organizationId, range);

	if (revenue.isLoading || campaigns.isLoading || products.isLoading || refunds.isLoading || fees.isLoading) {
		return <LoadingState label="Loading reports…" />;
	}
	if (revenue.isError || campaigns.isError || products.isError || refunds.isError || fees.isError) {
		return <ErrorState message="Could not load one or more reports." onRetry={() => Promise.all([revenue.refetch(), campaigns.refetch(), products.refetch(), refunds.refetch(), fees.refetch()])} />;
	}

	async function downloadCsv() {
		setExportError(null);
		try {
			const file = await exportRevenueReport(organizationId, range);
			saveBlob(file.blob, file.filename);
		} catch {
			setExportError("Could not export the revenue report.");
		}
	}

	return (
		<div className="flex flex-col gap-5">
			<div className="flex flex-wrap items-end justify-between gap-3 rounded-xl border border-slate-gray/20 bg-ice-white p-4">
				<div className="flex flex-wrap gap-3">
					<label className="flex flex-col gap-1 text-sm font-medium text-navy">From<input type="date" value={range.from} onChange={(event) => setRange((current) => ({ ...current, from: event.target.value }))} className="min-h-11 rounded-md border border-slate-gray/30 bg-white px-3 py-2" /></label>
					<label className="flex flex-col gap-1 text-sm font-medium text-navy">To<input type="date" value={range.to} onChange={(event) => setRange((current) => ({ ...current, to: event.target.value }))} className="min-h-11 rounded-md border border-slate-gray/30 bg-white px-3 py-2" /></label>
				</div>
				<Button type="button" variant="secondary" onClick={downloadCsv}>Export revenue CSV</Button>
			</div>
			{exportError && <p role="alert" className="text-sm text-error-red">{exportError}</p>}

			<div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
				<Metric label="Revenue" value={formatMoneyMinorUnits(revenue.data?.totalMinor ?? 0, "USD")} />
				<Metric label="Fees collected" value={formatMoneyMinorUnits(fees.data?.collectedMinor ?? 0, "USD")} />
				<Metric label="Outstanding fees" value={formatMoneyMinorUnits(fees.data?.outstandingMinor ?? 0, "USD")} />
				<Metric label="Refunds" value={formatMoneyMinorUnits(refunds.data?.totalMinor ?? 0, "USD")} detail={`${refunds.data?.count ?? 0} refund${refunds.data?.count === 1 ? "" : "s"}`} />
			</div>

			<div className="grid gap-5 lg:grid-cols-2">
				<ReportTable title="Revenue by source" headers={["Source", "Amount"]} rows={(revenue.data?.bySourceType ?? []).map((row) => [row.sourceType, formatMoneyMinorUnits(row.amountMinor, "USD")])} />
				<ReportTable title="Revenue by team" headers={["Team", "Amount"]} rows={(revenue.data?.byTeam ?? []).map((row) => [row.teamName ?? "Organization-wide", formatMoneyMinorUnits(row.amountMinor, "USD")])} />
				<ReportTable title="Campaign performance" headers={["Campaign", "Revenue"]} rows={(campaigns.data ?? []).map((row) => [row.campaignName, formatMoneyMinorUnits(row.amountMinor, "USD")])} />
				<ReportTable title="Product performance" headers={["Product", "Quantity", "Revenue"]} rows={(products.data ?? []).map((row) => [row.productName, String(row.quantitySold), formatMoneyMinorUnits(row.revenueMinor, "USD")])} />
			</div>
		</div>
	);
}

function Metric({ label, value, detail }: { label: string; value: string; detail?: string }) {
	return <div className="rounded-xl border border-slate-gray/20 bg-pure-white p-4"><p className="text-xs font-medium uppercase tracking-wide text-slate-gray">{label}</p><p className="mt-2 font-heading text-2xl font-bold text-navy">{value}</p>{detail && <p className="mt-1 text-xs text-slate-gray">{detail}</p>}</div>;
}

function ReportTable({ title, headers, rows }: { title: string; headers: string[]; rows: string[][] }) {
	return (
		<section className="rounded-xl border border-slate-gray/20 bg-pure-white p-4">
			<h3 className="font-heading text-base font-semibold text-navy">{title}</h3>
			{rows.length === 0 ? <p className="mt-3 text-sm text-slate-gray">No data in this period.</p> : (
				<div className="mt-3 overflow-x-auto"><table className="w-full min-w-[360px] text-left text-sm"><thead><tr className="border-b border-slate-gray/20 text-slate-gray">{headers.map((header) => <th key={header} className="pb-2 pr-3 font-medium">{header}</th>)}</tr></thead><tbody className="divide-y divide-slate-gray/10">{rows.map((row, index) => <tr key={`${row[0]}-${index}`}>{row.map((value, cell) => <td key={`${index}-${cell}`} className={`py-2 pr-3 ${cell === 0 ? "text-navy" : "text-slate-gray"}`}>{value}</td>)}</tr>)}</tbody></table></div>
			)}
		</section>
	);
}
