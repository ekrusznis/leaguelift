import { useState } from "react";
import { Link } from "react-router-dom";
import { ErrorState } from "../../components/states/ErrorState";
import { LoadingState } from "../../components/states/LoadingState";
import { formatMoneyMinorUnits } from "../../lib/money";
import { appPaths } from "../../routes/appPaths";
import { usePlatformReport } from "./api";

function defaultRange() {
	const to = new Date();
	const from = new Date(to);
	from.setDate(from.getDate() - 30);
	return { from: from.toISOString().slice(0, 10), to: to.toISOString().slice(0, 10) };
}

export function PlatformReportsPage() {
	const [range, setRange] = useState(defaultRange);
	const report = usePlatformReport(range);

	if (report.isLoading) return <LoadingState label="Loading platform report…" />;
	if (report.isError || !report.data) return <ErrorState message="Could not load the platform report." onRetry={() => report.refetch()} />;

	return (
		<div className="flex flex-col gap-6">
			<div>
				<Link to={appPaths.dashboard()} className="mb-2 inline-block text-sm text-azure-blue hover:underline">← Back to platform dashboard</Link>
				<h1 className="font-heading text-2xl font-bold text-navy">Platform Reports</h1>
				<p className="mt-1 text-slate-gray">Platform growth, transaction volume, refunds, and integration health.</p>
			</div>
			<div className="flex flex-wrap gap-3 rounded-xl border border-slate-gray/20 bg-ice-white p-4">
				<label className="flex flex-col gap-1 text-sm font-medium text-navy">From<input type="date" value={range.from} onChange={(event) => setRange((current) => ({ ...current, from: event.target.value }))} className="min-h-11 rounded-md border border-slate-gray/30 bg-white px-3 py-2" /></label>
				<label className="flex flex-col gap-1 text-sm font-medium text-navy">To<input type="date" value={range.to} onChange={(event) => setRange((current) => ({ ...current, to: event.target.value }))} className="min-h-11 rounded-md border border-slate-gray/30 bg-white px-3 py-2" /></label>
			</div>
			<div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
				<Metric label="New organizations" value={String(report.data.newOrganizations)} />
				<Metric label="Active organizations" value={String(report.data.activeOrganizations)} />
				<Metric label="New customers" value={String(report.data.newCustomers)} />
				<Metric label="Gross transaction volume" value={formatMoneyMinorUnits(report.data.grossTransactionVolumeMinor, "USD")} />
				<Metric label="Refunded" value={formatMoneyMinorUnits(report.data.refundedMinor, "USD")} />
				<Metric label="Refund rate" value={report.data.refundRatePercent === null ? "N/A" : `${report.data.refundRatePercent.toFixed(2)}%`} />
				<Metric label="Webhook failures" value={String(report.data.webhookFailed)} detail={`${report.data.webhookProcessed} processed`} />
				<Metric label="Dead-letter events" value={String(report.data.outboxDeadLetter)} detail={`${report.data.outboxPending} pending`} />
			</div>
		</div>
	);
}

function Metric({ label, value, detail }: { label: string; value: string; detail?: string }) {
	return <div className="rounded-xl border border-slate-gray/20 bg-pure-white p-4"><p className="text-xs font-medium uppercase tracking-wide text-slate-gray">{label}</p><p className="mt-2 font-heading text-2xl font-bold text-navy">{value}</p>{detail && <p className="mt-1 text-xs text-slate-gray">{detail}</p>}</div>;
}
