import { useState } from "react";
import { Link } from "react-router-dom";
import { ApiError } from "../../lib/apiError";
import { formatMoneyMinorUnits } from "../../lib/money";
import { usePlatformPayments, useRefundOrVoidPayment } from "./api";
import type { PlatformPaymentListItem, PlatformPaymentType } from "./types";

const TYPE_LABELS: Record<PlatformPaymentType, string> = {
	ORDER: "Swag Shop order",
	FEE: "Fee / dues",
	CONTRIBUTION: "Contribution",
	SPONSORSHIP: "Sponsorship",
};

export function PlatformPaymentsPage() {
	const [queryInput, setQueryInput] = useState("");
	const [query, setQuery] = useState("");
	const [type, setType] = useState("");
	const [status, setStatus] = useState("");
	const [dateFrom, setDateFrom] = useState("");
	const [dateTo, setDateTo] = useState("");
	const [page, setPage] = useState(0);
	const [actionError, setActionError] = useState<string | null>(null);
	const payments = usePlatformPayments({
		query,
		type,
		status,
		dateFrom: dateFrom ? new Date(dateFrom).toISOString() : undefined,
		dateTo: dateTo ? new Date(dateTo).toISOString() : undefined,
		page,
		size: 25,
	});
	const refundOrVoid = useRefundOrVoidPayment();

	async function onRefundOrVoid(payment: PlatformPaymentListItem) {
		setActionError(null);
		const verb = payment.type === "FEE" ? "void" : "refund";
		const amount = formatMoneyMinorUnits(payment.amountMinor, payment.currency);
		if (!window.confirm(`${verb === "void" ? "Void" : "Refund"} ${amount} for ${payment.payerName ?? "this payer"}? This can't be undone.`)) return;
		try {
			await refundOrVoid.mutateAsync(payment);
		} catch (error) {
			if (error instanceof ApiError && error.code === "SUPPORT_ACCESS_REQUIRED") {
				setActionError(`Start a support session for ${payment.organizationName} first — open the organization below, then retry.`);
			} else {
				setActionError(`Could not ${verb} this payment. Please try again.`);
			}
		}
	}

	if (payments.isLoading) return <p className="text-slate-500 dark:text-[#cbd5e1]">Loading payments…</p>;
	if (payments.isError || !payments.data) {
		return (
			<div className="rounded-lg border border-rose-300 bg-rose-50 p-4 text-rose-800">
				Could not load payments. <button type="button" className="underline" onClick={() => payments.refetch()}>Retry</button>
			</div>
		);
	}

	const totalPages = Math.max(1, Math.ceil(payments.data.totalElements / payments.data.size));

	return (
		<div className="flex flex-col gap-6">
			<div>
				<h1 className="font-heading text-2xl font-bold text-navy-900 dark:text-[#f8fafc]">Payments</h1>
				<p className="mt-1 text-slate-500 dark:text-[#cbd5e1]">
					Every attempted payment across every organization — Swag Shop orders, fee/dues payments, contributions, and sponsorships — to help
					find where a payer is stuck. Refund/void requires an active support session for that payment's organization.
				</p>
			</div>

			{actionError && (
				<div role="alert" className="rounded-lg border border-rose-300 bg-rose-50 p-3 text-sm text-rose-800">
					{actionError}
				</div>
			)}

			<form
				onSubmit={(event) => { event.preventDefault(); setPage(0); setQuery(queryInput); }}
				className="flex flex-wrap items-end gap-3 rounded-xl border border-slate-200 dark:border-[#334155] bg-white dark:bg-[#111827] p-4"
			>
				<label className="flex min-w-64 flex-1 flex-col gap-1 text-sm font-medium text-navy-900 dark:text-[#f8fafc]">
					Search
					<input value={queryInput} onChange={(event) => setQueryInput(event.target.value)} placeholder="Payer name, email, organization, or team" className="min-h-11 rounded-md border border-slate-300 dark:border-[#334155] px-3 py-2 font-normal" />
				</label>
				<label className="flex flex-col gap-1 text-sm font-medium text-navy-900 dark:text-[#f8fafc]">
					Type
					<select value={type} onChange={(event) => { setType(event.target.value); setPage(0); }} className="min-h-11 rounded-md border border-slate-300 dark:border-[#334155] bg-white dark:bg-[#111827] px-3 py-2 font-normal">
						<option value="">All types</option>
						{(Object.keys(TYPE_LABELS) as PlatformPaymentType[]).map((value) => <option key={value} value={value}>{TYPE_LABELS[value]}</option>)}
					</select>
				</label>
				<label className="flex flex-col gap-1 text-sm font-medium text-navy-900 dark:text-[#f8fafc]">
					Status
					<select value={status} onChange={(event) => { setStatus(event.target.value); setPage(0); }} className="min-h-11 rounded-md border border-slate-300 dark:border-[#334155] bg-white dark:bg-[#111827] px-3 py-2 font-normal">
						<option value="">All statuses</option>
						<option value="PENDING">Pending</option>
						<option value="PENDING_CHECKOUT">Pending checkout</option>
						<option value="CONFIRMED">Confirmed</option>
						<option value="CANCELED">Canceled</option>
						<option value="REFUNDED">Refunded</option>
					</select>
				</label>
				<label className="flex flex-col gap-1 text-sm font-medium text-navy-900 dark:text-[#f8fafc]">
					From
					<input type="date" value={dateFrom} onChange={(event) => { setDateFrom(event.target.value); setPage(0); }} className="min-h-11 rounded-md border border-slate-300 dark:border-[#334155] px-3 py-2 font-normal" />
				</label>
				<label className="flex flex-col gap-1 text-sm font-medium text-navy-900 dark:text-[#f8fafc]">
					To
					<input type="date" value={dateTo} onChange={(event) => { setDateTo(event.target.value); setPage(0); }} className="min-h-11 rounded-md border border-slate-300 dark:border-[#334155] px-3 py-2 font-normal" />
				</label>
				<button type="submit" className="min-h-11 rounded-md bg-navy-900 px-4 py-2 font-semibold text-white hover:bg-navy-800">Search</button>
			</form>

			<div className="overflow-x-auto rounded-xl border border-slate-200 dark:border-[#334155] bg-white dark:bg-[#111827]">
				<table className="w-full min-w-[1100px] text-left text-sm">
					<thead className="border-b border-slate-200 dark:border-[#334155] bg-ice-50 dark:bg-[#0f172a] text-slate-500 dark:text-[#cbd5e1]">
						<tr>
							<th className="px-4 py-3 font-medium">Organization</th>
							<th className="px-4 py-3 font-medium">Team</th>
							<th className="px-4 py-3 font-medium">Type</th>
							<th className="px-4 py-3 font-medium">Payer</th>
							<th className="px-4 py-3 font-medium">Amount</th>
							<th className="px-4 py-3 font-medium">Status</th>
							<th className="px-4 py-3 font-medium">Date</th>
							<th className="px-4 py-3 font-medium"><span className="sr-only">Actions</span></th>
						</tr>
					</thead>
					<tbody className="divide-y divide-slate-100 dark:divide-[#334155]">
						{payments.data.items.map((payment) => (
							<tr key={`${payment.type}-${payment.id}`}>
								<td className="px-4 py-3 font-semibold text-navy-900 dark:text-[#f8fafc]">{payment.organizationName}</td>
								<td className="px-4 py-3 text-slate-600 dark:text-[#cbd5e1]">{payment.teamName ?? "—"}</td>
								<td className="px-4 py-3 text-slate-600 dark:text-[#cbd5e1]">{TYPE_LABELS[payment.type]}</td>
								<td className="px-4 py-3">
									<p className="font-medium text-navy-900 dark:text-[#f8fafc]">{payment.payerName ?? "—"}</p>
									<p className="text-xs text-slate-500 dark:text-[#cbd5e1]">{payment.payerEmail ?? ""}</p>
								</td>
								<td className="px-4 py-3 text-slate-600 dark:text-[#cbd5e1]">{formatMoneyMinorUnits(payment.amountMinor, payment.currency)}</td>
								<td className="px-4 py-3"><StatusPill status={payment.status} /></td>
								<td className="px-4 py-3 text-slate-600 dark:text-[#cbd5e1]">{new Date(payment.createdAt).toLocaleDateString()}</td>
								<td className="px-4 py-3 text-right">
									<div className="flex justify-end gap-2">
										{payment.canRefundOrVoid && (
											<button
												type="button"
												disabled={refundOrVoid.isPending}
												onClick={() => void onRefundOrVoid(payment)}
												className="rounded-md border border-rose-300 px-3 py-2 font-medium text-rose-700 hover:bg-rose-50 disabled:opacity-40"
											>
												{payment.type === "FEE" ? "Void" : "Refund"}
											</button>
										)}
										<Link to={`/app/platform/organizations/${payment.organizationId}`} className="rounded-md border border-slate-300 dark:border-[#334155] px-3 py-2 font-medium text-navy-900 dark:text-[#f8fafc] hover:border-green-500 hover:text-green-700">Open organization</Link>
									</div>
								</td>
							</tr>
						))}
					</tbody>
				</table>
				{payments.data.items.length === 0 && <p className="p-6 text-center text-sm text-slate-500 dark:text-[#cbd5e1]">No payments match these filters.</p>}
			</div>

			<div className="flex items-center justify-between text-sm text-slate-600 dark:text-[#cbd5e1]">
				<p>{payments.data.totalElements} payments</p>
				<div className="flex items-center gap-2">
					<button type="button" disabled={page === 0} onClick={() => setPage((value) => Math.max(0, value - 1))} className="rounded-md border border-slate-300 dark:border-[#334155] px-3 py-2 disabled:opacity-40">Previous</button>
					<span>Page {page + 1} of {totalPages}</span>
					<button type="button" disabled={page + 1 >= totalPages} onClick={() => setPage((value) => value + 1)} className="rounded-md border border-slate-300 dark:border-[#334155] px-3 py-2 disabled:opacity-40">Next</button>
				</div>
			</div>
		</div>
	);
}

function StatusPill({ status }: { status: string }) {
	const classes =
		status === "CONFIRMED"
			? "bg-green-100 text-green-800"
			: status === "REFUNDED" || status === "CANCELED"
				? "bg-slate-100 dark:bg-slate-800 text-slate-700 dark:text-[#cbd5e1]"
				: "bg-amber-100 text-amber-900";
	return <span className={`rounded-full px-2.5 py-1 text-xs font-semibold ${classes}`}>{status.replaceAll("_", " ")}</span>;
}
