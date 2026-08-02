import { useState, type FormEvent } from "react";
import { Button } from "../../components/Button";
import { ErrorState } from "../../components/states/ErrorState";
import { useCancelFeePaymentPlan, useCreateFeePaymentPlan, useFeePaymentPlan } from "./api";

interface DraftInstallment { key: string; amount: string; dueDate: string }
const inputClass = "min-h-11 rounded-md border border-slate-gray/30 bg-pure-white px-3 py-2 text-navy";

function money(amountMinor: number, currency: string) {
	return new Intl.NumberFormat("en-US", { style: "currency", currency }).format(amountMinor / 100);
}

function draft(): DraftInstallment {
	return { key: crypto.randomUUID(), amount: "", dueDate: "" };
}

export function PaymentPlanPanel({ organizationId, householdId, assignmentId, balanceMinor, currency, canManage }: {
	organizationId: string;
	householdId: string;
	assignmentId: string;
	balanceMinor: number;
	currency: string;
	canManage: boolean;
}) {
	const query = useFeePaymentPlan(organizationId, assignmentId);
	const create = useCreateFeePaymentPlan(organizationId, householdId, assignmentId);
	const cancel = useCancelFeePaymentPlan(organizationId, householdId, assignmentId);
	const [showForm, setShowForm] = useState(false);
	const [installments, setInstallments] = useState<DraftInstallment[]>([draft(), draft()]);
	const [note, setNote] = useState("");
	const [error, setError] = useState("");

	if (query.isError) return <ErrorState message="Could not load the payment plan." onRetry={() => void query.refetch()} />;
	const plan = query.data;

	async function submit(event: FormEvent) {
		event.preventDefault();
		setError("");
		const normalized = installments.map((item) => ({ amountMinor: Math.round(Number(item.amount) * 100), dueDate: item.dueDate }));
		if (normalized.some((item) => !item.dueDate || !Number.isFinite(item.amountMinor) || item.amountMinor <= 0)) {
			setError("Enter a positive amount and due date for every installment.");
			return;
		}
		if (normalized.reduce((sum, item) => sum + item.amountMinor, 0) !== balanceMinor) {
			setError(`Installments must total ${money(balanceMinor, currency)}.`);
			return;
		}
		try {
			await create.mutateAsync({ installments: normalized, note: note.trim() || null });
			setShowForm(false);
			setInstallments([draft(), draft()]);
			setNote("");
		} catch (caught) {
			setError(caught instanceof Error ? caught.message : "The payment plan could not be created.");
		}
	}

	async function cancelPlan() {
		const reason = window.prompt("Reason for cancelling this payment plan?");
		if (!reason?.trim()) return;
		await cancel.mutateAsync(reason.trim());
	}

	return (
		<section className="rounded-lg border border-slate-gray/20 bg-ice-white p-4" aria-label="Payment plan">
			<div className="flex flex-wrap items-center justify-between gap-3">
				<div>
					<h3 className="text-sm font-semibold text-navy">Payment plan</h3>
					<p className="text-sm text-slate-gray">Dated installments are paid oldest-first and retain void history.</p>
				</div>
				{canManage && plan?.status !== "ACTIVE" && balanceMinor > 0 && <Button type="button" variant="secondary" onClick={() => setShowForm((value) => !value)}>{showForm ? "Cancel" : "Create plan"}</Button>}
			</div>

			{query.isLoading && <p className="mt-3 text-sm text-slate-gray">Loading payment plan…</p>}
			{plan && (
				<div className="mt-3 grid gap-3">
					<div className="flex flex-wrap items-center justify-between gap-3 text-sm">
						<p className="text-navy"><strong>{plan.status}</strong> · {money(plan.paidMinor, plan.currency)} paid · {money(plan.remainingMinor, plan.currency)} remaining</p>
						{canManage && plan.status === "ACTIVE" && <Button type="button" variant="danger" onClick={() => void cancelPlan()} disabled={cancel.isPending}>Cancel plan</Button>}
					</div>
					<ol className="grid gap-2">
						{plan.installments.map((item) => (
							<li key={item.id} className="flex flex-wrap justify-between gap-2 rounded-md bg-pure-white p-3 text-sm">
								<span className="text-navy">#{item.sequenceNumber} · due {item.dueDate}</span>
								<span className={item.status === "OVERDUE" ? "font-semibold text-error-red" : "text-slate-gray"}>{item.status.replaceAll("_", " ")} · {money(item.remainingMinor, plan.currency)} remaining</span>
							</li>
						))}
					</ol>
				</div>
			)}

			{showForm && plan?.status !== "ACTIVE" && (
				<form onSubmit={submit} className="mt-4 grid gap-3" noValidate>
					{installments.map((item, index) => (
						<div key={item.key} className="grid gap-2 sm:grid-cols-[1fr_1fr_auto]">
							<label className="grid gap-1 text-sm text-navy">Installment {index + 1} amount
								<input className={inputClass} type="number" min="0.01" step="0.01" value={item.amount} onChange={(event) => setInstallments((rows) => rows.map((row) => row.key === item.key ? { ...row, amount: event.target.value } : row))} />
							</label>
							<label className="grid gap-1 text-sm text-navy">Due date
								<input className={inputClass} type="date" value={item.dueDate} onChange={(event) => setInstallments((rows) => rows.map((row) => row.key === item.key ? { ...row, dueDate: event.target.value } : row))} />
							</label>
							{installments.length > 2 && <Button type="button" variant="secondary" className="self-end" onClick={() => setInstallments((rows) => rows.filter((row) => row.key !== item.key))}>Remove</Button>}
						</div>
					))}
					<Button type="button" variant="secondary" className="justify-self-start" onClick={() => setInstallments((rows) => [...rows, draft()])} disabled={installments.length >= 24}>Add installment</Button>
					<label className="grid gap-1 text-sm text-navy">Internal note
						<textarea className={inputClass} value={note} onChange={(event) => setNote(event.target.value)} rows={2} />
					</label>
					{error && <p role="alert" className="text-sm text-error-red">{error}</p>}
					<Button type="submit" disabled={create.isPending}>{create.isPending ? "Creating…" : `Create ${money(balanceMinor, currency)} plan`}</Button>
				</form>
			)}
		</section>
	);
}
