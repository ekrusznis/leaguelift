import { useState, type FormEvent } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { Button } from "../../components/Button";
import { EmptyState } from "../../components/states/EmptyState";
import { ErrorState } from "../../components/states/ErrorState";
import { LoadingState } from "../../components/states/LoadingState";
import { appPaths } from "../../routes/appPaths";
import { useExecuteFinancialCorrection, useFinancialCorrections, usePreviewFinancialCorrection } from "./api";
import type { FinancialCorrection, FinancialCorrectionPreview, FinancialCorrectionTargetType } from "./types";

const inputClass = "min-h-11 rounded-md border border-slate-gray/30 bg-pure-white dark:bg-[#111827] px-3 py-2 text-navy dark:text-[#f8fafc]";
function money(minor: number, currency: string) { return new Intl.NumberFormat("en-US", { style: "currency", currency }).format(minor / 100); }

export function FinancialCorrectionsPanel({ organizationId }: { organizationId: string }) {
	const [searchParams] = useSearchParams();
	const initialTargetType = searchParams.get("targetType");
	const initialTargetId = searchParams.get("targetId") ?? "";
	const previewMutation = usePreviewFinancialCorrection(organizationId);
	const execute = useExecuteFinancialCorrection(organizationId);
	const [targetType, setTargetType] = useState<FinancialCorrectionTargetType>(
		initialTargetType === "SPONSORSHIP" || initialTargetType === "ORDER" || initialTargetType === "OFFLINE_FINANCIAL_RECORD" ? initialTargetType : "CONTRIBUTION",
	);
	const [targetId, setTargetId] = useState(initialTargetId);
	const [amount, setAmount] = useState("");
	const [reason, setReason] = useState("");
	const [preview, setPreview] = useState<FinancialCorrectionPreview | null>(null);
	const [message, setMessage] = useState("");
	const [error, setError] = useState("");
	const [historyQuery, setHistoryQuery] = useState("");
	const [historyTargetType, setHistoryTargetType] = useState<FinancialCorrectionTargetType | "">("");
	const [historyCorrectionType, setHistoryCorrectionType] = useState<FinancialCorrection["correctionType"] | "">("");
	const [historySort, setHistorySort] = useState<"newest" | "oldest">("newest");
	const [historyPage, setHistoryPage] = useState(0);
	const list = useFinancialCorrections(organizationId, {
		query: historyQuery,
		targetType: historyTargetType,
		correctionType: historyCorrectionType,
		sort: historySort,
		page: historyPage,
		size: 20,
	});
	const historyPageCount = Math.max(1, Math.ceil((list.data?.totalElements ?? 0) / 20));

	async function createPreview(event: FormEvent) {
		event.preventDefault(); setError(""); setMessage(""); setPreview(null);
		const amountMinor = amount.trim() ? Math.round(Number(amount) * 100) : null;
		if (!targetId.trim() || !reason.trim() || (amountMinor !== null && (!Number.isFinite(amountMinor) || amountMinor <= 0))) {
			setError("Enter a record ID, a reason, and an optional positive amount."); return;
		}
		try { setPreview(await previewMutation.mutateAsync({ targetType, targetId: targetId.trim(), amountMinor, reason: reason.trim() })); }
		catch (caught) { setError(caught instanceof Error ? caught.message : "The correction could not be previewed."); }
	}
	async function confirm() {
		if (!preview) return;
		try {
			await execute.mutateAsync({ targetType, targetId: targetId.trim(), amountMinor: amount.trim() ? Math.round(Number(amount) * 100) : null, reason: reason.trim(), confirmationHash: preview.confirmationHash, idempotencyKey: crypto.randomUUID() });
			setMessage(`${preview.correctionType === "REFUND" ? "Refund" : "Reversal"} recorded successfully.`);
			setPreview(null); setTargetId(""); setAmount(""); setReason("");
		} catch (caught) { setError(caught instanceof Error ? caught.message : "The correction could not be completed."); }
	}
	return <section className="rounded-xl border border-slate-gray/20 bg-pure-white dark:bg-[#111827] p-5" aria-labelledby="corrections-heading">
		<div className="flex flex-wrap items-start justify-between gap-3">
			<div><h3 id="corrections-heading" className="font-heading text-lg font-semibold text-navy dark:text-[#f8fafc]">Controlled refunds &amp; reversals</h3><p className="mt-1 text-sm text-slate-gray dark:text-[#cbd5e1]">Preview the exact provider and ledger effect before confirming. Original records remain visible.</p></div>
			<Link to={appPaths.helpArticle("previewing-refunds-and-financial-reversals")} className="text-sm font-medium text-info-blue hover:underline">Read the correction how-to</Link>
		</div>
		<form onSubmit={createPreview} className="mt-4 grid gap-3 lg:grid-cols-2" noValidate>
			<label className="grid gap-1 text-sm text-navy dark:text-[#f8fafc]">Record type<select className={inputClass} value={targetType} onChange={(e) => { setTargetType(e.target.value as FinancialCorrectionTargetType); setPreview(null); }}><option value="CONTRIBUTION">Contribution</option><option value="SPONSORSHIP">Sponsorship</option><option value="ORDER">Order</option><option value="OFFLINE_FINANCIAL_RECORD">Offline record</option></select></label>
			<label className="grid gap-1 text-sm text-navy dark:text-[#f8fafc]">Record ID<input className={inputClass} value={targetId} onChange={(e) => { setTargetId(e.target.value); setPreview(null); }} placeholder="UUID from the related record" /></label>
			<label className="grid gap-1 text-sm text-navy dark:text-[#f8fafc]">Amount (leave blank for full)<input className={inputClass} type="number" min="0.01" step="0.01" value={amount} onChange={(e) => { setAmount(e.target.value); setPreview(null); }} /></label>
			<label className="grid gap-1 text-sm text-navy dark:text-[#f8fafc]">Reason<textarea className={inputClass} rows={2} value={reason} onChange={(e) => { setReason(e.target.value); setPreview(null); }} /></label>
			<div className="lg:col-span-2"><Button type="submit" disabled={previewMutation.isPending}>{previewMutation.isPending ? "Previewing…" : "Preview correction"}</Button></div>
		</form>
		{error && <p role="alert" className="mt-3 text-sm text-error-red">{error}</p>}{message && <p role="status" className="mt-3 text-sm text-success-700 dark:text-success-400">{message}</p>}
		{preview && <div className="mt-4 rounded-lg border border-amber-300 bg-amber-50 dark:bg-amber-950 p-4">
			<h4 className="font-semibold text-navy dark:text-[#f8fafc]">Confirm {preview.correctionType.toLowerCase()}</h4>
			<p className="mt-1 text-sm text-navy dark:text-[#f8fafc]">{preview.targetLabel}: {money(preview.requestedAmountMinor, preview.currency)}. Remaining correctable amount: {money(preview.remainingAfterMinor, preview.currency)}.</p>
			<ul className="mt-2 list-disc pl-5 text-sm text-slate-gray dark:text-[#cbd5e1]">{preview.warnings.map((warning) => <li key={warning}>{warning}</li>)}</ul>
			<Button type="button" variant="danger" className="mt-3" onClick={() => void confirm()} disabled={execute.isPending}>{execute.isPending ? "Processing…" : "Confirm correction"}</Button>
		</div>}
		<div className="mt-6">
			<div className="flex flex-wrap items-end justify-between gap-3">
				<h4 className="font-semibold text-navy dark:text-[#f8fafc]">Correction history</h4>
				<div className="flex flex-wrap gap-2">
					<input aria-label="Search correction history" className={inputClass} value={historyQuery} onChange={(event) => { setHistoryQuery(event.target.value); setHistoryPage(0); }} placeholder="Search ID or reason" />
					<select aria-label="Correction target type" className={inputClass} value={historyTargetType} onChange={(event) => { setHistoryTargetType(event.target.value as FinancialCorrectionTargetType | ""); setHistoryPage(0); }}><option value="">All record types</option><option value="CONTRIBUTION">Contribution</option><option value="SPONSORSHIP">Sponsorship</option><option value="ORDER">Order</option><option value="OFFLINE_FINANCIAL_RECORD">Offline record</option></select>
					<select aria-label="Correction type" className={inputClass} value={historyCorrectionType} onChange={(event) => { setHistoryCorrectionType(event.target.value as FinancialCorrection["correctionType"] | ""); setHistoryPage(0); }}><option value="">Refunds & reversals</option><option value="REFUND">Refunds</option><option value="REVERSAL">Reversals</option></select>
					<select aria-label="Sort correction history" className={inputClass} value={historySort} onChange={(event) => { setHistorySort(event.target.value as "newest" | "oldest"); setHistoryPage(0); }}><option value="newest">Newest</option><option value="oldest">Oldest</option></select>
				</div>
			</div>
			{list.isLoading && <LoadingState label="Loading financial corrections…" />}
			{list.isError && <ErrorState message="Could not load financial corrections." onRetry={() => list.refetch()} />}
			{list.data?.items.length === 0 && <EmptyState title="No financial corrections" description={historyQuery || historyTargetType || historyCorrectionType ? "No corrections match these filters." : "Completed refunds and reversals will appear here."} />}
			{list.data?.items.length ? <ul className="mt-2 grid gap-2">{list.data.items.map((item) => <li key={item.id} className="rounded-md bg-ice-white dark:bg-[#0f172a] p-3 text-sm text-slate-gray dark:text-[#cbd5e1]"><strong className="text-navy dark:text-[#f8fafc]">{item.correctionType}</strong> · {item.targetType} · {money(item.amountMinor, item.currency)} · {new Date(item.createdAt).toLocaleString()}<div className="break-all text-xs">Target: {item.targetId}</div><div>{item.reason}</div></li>)}</ul> : null}
			{list.data && list.data.totalElements > list.data.size && <div className="mt-3 flex items-center justify-between gap-3 text-sm text-slate-gray dark:text-[#cbd5e1]"><span>Page {historyPage + 1} of {historyPageCount} · {list.data.totalElements} records</span><div className="flex gap-2"><Button type="button" variant="secondary" disabled={historyPage === 0} onClick={() => setHistoryPage((page) => Math.max(0, page - 1))}>Previous</Button><Button type="button" variant="secondary" disabled={historyPage + 1 >= historyPageCount} onClick={() => setHistoryPage((page) => page + 1)}>Next</Button></div></div>}
		</div>
	</section>;
}
