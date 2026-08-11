import { useState } from "react";
import { Link } from "react-router-dom";
import { ErrorState } from "../../components/states/ErrorState";
import { LoadingState } from "../../components/states/LoadingState";
import { usePlatformSubscriptions } from "./api";

export function PlatformSubscriptionsPage() {
	const [queryInput, setQueryInput] = useState("");
	const [query, setQuery] = useState("");
	const [status, setStatus] = useState("");
	const [page, setPage] = useState(0);
	const subscriptions = usePlatformSubscriptions({ query, status, page, size: 25 });
	if (subscriptions.isLoading) return <LoadingState label="Loading subscriptions…" />;
	if (subscriptions.isError || !subscriptions.data) {
		return <ErrorState message="Could not load organization subscriptions." onRetry={() => subscriptions.refetch()} />;
	}
	const totalPages = Math.max(1, Math.ceil(subscriptions.data.totalElements / subscriptions.data.size));
	return (
		<div className="flex flex-col gap-6">
			<div>
				<h1 className="font-heading text-2xl font-bold text-navy-900 dark:text-[#f8fafc]">Subscriptions</h1>
				<p className="mt-1 text-slate-500 dark:text-[#cbd5e1]">Read-only organization billing visibility for onboarding, recovery, and support triage.</p>
			</div>
			<form onSubmit={(event) => { event.preventDefault(); setPage(0); setQuery(queryInput); }} className="flex flex-wrap items-end gap-3 rounded-xl border border-slate-200 dark:border-[#334155] bg-white dark:bg-[#111827] p-4">
				<label className="flex min-w-64 flex-1 flex-col gap-1 text-sm font-medium text-navy-900 dark:text-[#f8fafc]">Search
					<input value={queryInput} onChange={(event) => setQueryInput(event.target.value)} placeholder="Organization name or slug" className="min-h-11 rounded-md border border-slate-300 dark:border-[#334155] px-3 py-2 font-normal" />
				</label>
				<label className="flex flex-col gap-1 text-sm font-medium text-navy-900 dark:text-[#f8fafc]">Subscription status
					<select value={status} onChange={(event) => { setStatus(event.target.value); setPage(0); }} className="min-h-11 rounded-md border border-slate-300 dark:border-[#334155] bg-white dark:bg-[#111827] px-3 py-2 font-normal">
						<option value="">All</option><option value="NONE">Not started</option><option value="CHECKOUT_PENDING">Checkout pending</option><option value="TRIALING">Trialing</option><option value="ACTIVE">Active</option><option value="PAST_DUE">Past due</option><option value="CANCELED">Canceled</option><option value="INCOMPLETE">Incomplete</option>
					</select>
				</label>
				<button type="submit" className="min-h-11 rounded-md bg-navy-900 px-4 py-2 font-semibold text-white hover:bg-navy-800">Search</button>
			</form>
			<div className="overflow-x-auto rounded-xl border border-slate-200 dark:border-[#334155] bg-white dark:bg-[#111827]">
				<table className="w-full min-w-[1100px] text-left text-sm">
					<thead className="border-b border-slate-200 dark:border-[#334155] bg-ice-50 dark:bg-[#0f172a] text-slate-500 dark:text-[#cbd5e1]"><tr><th className="px-4 py-3 font-medium">Organization</th><th className="px-4 py-3 font-medium">Plan</th><th className="px-4 py-3 font-medium">Status</th><th className="px-4 py-3 font-medium">Recovery</th><th className="px-4 py-3 font-medium">Provider links</th><th className="px-4 py-3 font-medium">Last failure / success</th><th className="px-4 py-3"><span className="sr-only">Action</span></th></tr></thead>
					<tbody className="divide-y divide-slate-100 dark:divide-[#334155]">
						{subscriptions.data.items.map((item) => (
							<tr key={item.organizationId}>
								<td className="px-4 py-3"><p className="font-semibold text-navy-900 dark:text-[#f8fafc]">{item.organizationName}</p><p className="text-xs text-slate-500 dark:text-[#cbd5e1]">{item.organizationStatus}</p></td>
								<td className="px-4 py-3 text-slate-700 dark:text-[#cbd5e1]">{item.planName ?? "—"}{item.amountMinor != null && item.currency ? <span className="block text-xs text-slate-500 dark:text-[#cbd5e1]">{formatMoney(item.amountMinor, item.currency)} / month</span> : null}</td>
								<td className="px-4 py-3"><StatusPill status={item.status ?? "NONE"} />{item.cancelAtPeriodEnd && <span className="mt-1 block text-xs font-medium text-amber-800">Cancels at period end</span>}</td>
								<td className="px-4 py-3 text-slate-600 dark:text-[#cbd5e1]">{item.recoveryState.replaceAll("_", " ")}</td>
								<td className="px-4 py-3 text-slate-600 dark:text-[#cbd5e1]">Customer {item.hasStripeCustomer ? "✓" : "—"} · Subscription {item.hasStripeSubscription ? "✓" : "—"}</td>
								<td className="px-4 py-3 text-xs text-slate-600 dark:text-[#cbd5e1]"><span className="block">Failure: {formatDate(item.lastPaymentFailureAt)}</span><span className="block">Success: {formatDate(item.lastPaymentSuccessAt)}</span></td>
								<td className="px-4 py-3 text-right"><Link to={`/app/platform/organizations/${item.organizationId}`} className="rounded-md border border-slate-300 dark:border-[#334155] px-3 py-2 font-medium text-navy-900 dark:text-[#f8fafc] hover:border-green-500 hover:text-green-700">Open org</Link></td>
							</tr>
						))}
					</tbody>
				</table>
				{subscriptions.data.items.length === 0 && <p className="p-6 text-center text-sm text-slate-500 dark:text-[#cbd5e1]">No organizations match these filters.</p>}
			</div>
			<div className="flex items-center justify-between text-sm text-slate-600 dark:text-[#cbd5e1]"><p>{subscriptions.data.totalElements} organizations</p><div className="flex items-center gap-2"><button type="button" disabled={page === 0} onClick={() => setPage((value) => Math.max(0, value - 1))} className="rounded-md border border-slate-300 dark:border-[#334155] px-3 py-2 disabled:opacity-40">Previous</button><span>Page {page + 1} of {totalPages}</span><button type="button" disabled={page + 1 >= totalPages} onClick={() => setPage((value) => value + 1)} className="rounded-md border border-slate-300 dark:border-[#334155] px-3 py-2 disabled:opacity-40">Next</button></div></div>
		</div>
	);
}

function StatusPill({ status }: { status: string }) {
	const classes = status === "ACTIVE" || status === "TRIALING" ? "bg-green-100 text-green-800" : status === "PAST_DUE" ? "bg-amber-100 text-amber-900" : "bg-slate-100 dark:bg-slate-800 text-slate-700 dark:text-[#cbd5e1]";
	return <span className={`rounded-full px-2.5 py-1 text-xs font-semibold ${classes}`}>{status.replaceAll("_", " ")}</span>;
}
function formatMoney(amountMinor: number, currency: string) { return new Intl.NumberFormat(undefined, { style: "currency", currency }).format(amountMinor / 100); }
function formatDate(value: string | null) { return value ? new Date(value).toLocaleDateString() : "—"; }
