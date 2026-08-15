import { useState } from "react";
import { Link, useParams } from "react-router-dom";
import { Button } from "../../components/Button";
import { EmptyState } from "../../components/states/EmptyState";
import { ErrorState } from "../../components/states/ErrorState";
import { LoadingState } from "../../components/states/LoadingState";
import { useDisputeSearch } from "./api";
import { STATUS_COLORS, STATUS_LABELS } from "./statusLabels";
import type { DisputeSourceType, DisputeStatus } from "./types";

const SOURCE_TYPE_LABELS: Record<DisputeSourceType, string> = {
	CONTRIBUTION: "Campaign contribution",
	ORDER: "Store order",
	SPONSORSHIP: "Sponsorship",
	FEE_PAYMENT: "Fee payment",
};
const inputClass = "min-h-11 rounded-md border border-slate-gray/30 bg-pure-white dark:bg-[#111827] px-3 py-2 text-navy dark:text-[#f8fafc]";

function formatAmount(amountMinor: number, currency: string) {
	return new Intl.NumberFormat("en-US", { style: "currency", currency }).format(amountMinor / 100);
}
function formatDate(value: string | null) {
	if (!value) return "—";
	return new Date(value).toLocaleDateString("en-US", { year: "numeric", month: "short", day: "numeric" });
}

export function DisputesPage() {
	const { organizationId } = useParams<{ organizationId: string }>();
	const [query, setQuery] = useState("");
	const [status, setStatus] = useState<DisputeStatus | "">("");
	const [sourceType, setSourceType] = useState<DisputeSourceType | "">("");
	const [sort, setSort] = useState<"newest" | "oldest">("newest");
	const [page, setPage] = useState(0);
	const result = useDisputeSearch(organizationId ?? "", { query, status, sourceType, sort, page, size: 20 });
	const pageCount = Math.max(1, Math.ceil((result.data?.totalElements ?? 0) / 20));

	if (!organizationId) {
		return <ErrorState message="No organization selected." />;
	}
	return (
		<div className="flex flex-col gap-6">
			<div>
				<Link to={`/app/organizations/${organizationId}`} className="mb-2 inline-block text-sm text-azure-blue hover:underline">
					← Back to organization
				</Link>
				<h1 className="font-heading text-2xl font-bold text-navy dark:text-[#f8fafc]">Disputes</h1>
				<p className="text-slate-gray dark:text-[#cbd5e1]">
					Stripe disputes (chargebacks) on payments to this organization. Rally26 is the merchant of record and responds to Stripe
					directly — evidence review and submission happen in the Stripe Dashboard, not here.
				</p>
			</div>
			<div className="flex flex-wrap gap-2">
				<input className={inputClass} value={query} onChange={(event) => { setQuery(event.target.value); setPage(0); }} placeholder="Search reason or source ID" aria-label="Search disputes" />
				<select className={inputClass} value={status} onChange={(event) => { setStatus(event.target.value as DisputeStatus | ""); setPage(0); }} aria-label="Dispute status"><option value="">All statuses</option><option value="NEEDS_RESPONSE">Needs response</option><option value="UNDER_REVIEW">Under review</option><option value="WON">Won</option><option value="LOST">Lost</option></select>
				<select className={inputClass} value={sourceType} onChange={(event) => { setSourceType(event.target.value as DisputeSourceType | ""); setPage(0); }} aria-label="Dispute source type"><option value="">All sources</option>{Object.entries(SOURCE_TYPE_LABELS).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select>
				<select className={inputClass} value={sort} onChange={(event) => { setSort(event.target.value as "newest" | "oldest"); setPage(0); }} aria-label="Sort disputes"><option value="newest">Newest</option><option value="oldest">Oldest</option></select>
			</div>
			{result.isLoading && <LoadingState label="Loading disputes…" />}
			{result.isError && <ErrorState message="Could not load disputes." onRetry={() => result.refetch()} />}
			{result.data?.items.length === 0 && <EmptyState title={query || status || sourceType ? "No disputes match these filters" : "No disputes"} description={query || status || sourceType ? "Try changing your search or filters." : "This organization has no Stripe disputes on record."} />}
			{result.data && result.data.items.length > 0 && (
				<>
					<div className="overflow-x-auto rounded-lg border border-slate-gray/20">
						<table className="w-full text-left text-sm">
							<thead className="bg-ice-white dark:bg-[#0f172a] text-slate-gray dark:text-[#cbd5e1]"><tr><th className="p-3 font-medium">Source</th><th className="p-3 font-medium">Amount</th><th className="p-3 font-medium">Reason</th><th className="p-3 font-medium">Opened</th><th className="p-3 font-medium">Evidence due</th><th className="p-3 font-medium">Status</th></tr></thead>
							<tbody>{result.data.items.map((dispute) => <tr key={dispute.id} className="border-t border-slate-gray/10"><td className="p-3"><div>{SOURCE_TYPE_LABELS[dispute.sourceType]}</div><div className="break-all text-xs text-slate-gray dark:text-[#cbd5e1]">{dispute.sourceId}</div></td><td className="p-3 font-semibold">{formatAmount(dispute.amountMinor, dispute.currency)}</td><td className="p-3 text-slate-gray dark:text-[#cbd5e1]">{dispute.reason}</td><td className="p-3">{formatDate(dispute.openedAt)}</td><td className="p-3">{formatDate(dispute.evidenceDueBy)}</td><td className="p-3"><span className={`rounded-full px-2 py-0.5 text-xs font-medium ${STATUS_COLORS[dispute.status]}`}>{STATUS_LABELS[dispute.status]}</span></td></tr>)}</tbody>
						</table>
					</div>
					{result.data.totalElements > result.data.size && <div className="flex items-center justify-between gap-3 text-sm text-slate-gray dark:text-[#cbd5e1]"><span>Page {page + 1} of {pageCount} · {result.data.totalElements} disputes</span><div className="flex gap-2"><Button type="button" variant="secondary" disabled={page === 0} onClick={() => setPage((current) => Math.max(0, current - 1))}>Previous</Button><Button type="button" variant="secondary" disabled={page + 1 >= pageCount} onClick={() => setPage((current) => current + 1)}>Next</Button></div></div>}
				</>
			)}
		</div>
	);
}
