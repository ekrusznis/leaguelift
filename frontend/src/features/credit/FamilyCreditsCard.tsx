import { useState } from "react";
import { Button } from "../../components/Button";
import { formatMoneyMinorUnits } from "../../lib/money";
import { useFeeAssignments } from "../fees/api";
import { useApplyFamilyCredit, useFamilyCreditBalance, useTransferFamilyCredit } from "./api";

/**
 * Phase 23 (DESIGN-DOC.md section 13/14.1): a guardian's real family credit
 * balance, with the two real actions the backend supports — applying credit
 * against one of their own outstanding fees, and (only when the organization
 * has explicitly enabled it) sending credit to another household in the org.
 */
export function FamilyCreditsCard({ organizationId, householdId }: { organizationId: string; householdId: string }) {
	const balance = useFamilyCreditBalance(organizationId, householdId);
	const [showApply, setShowApply] = useState(false);
	const [showTransfer, setShowTransfer] = useState(false);

	if (balance.isLoading) return <p className="text-sm text-slate-500 dark:text-[#cbd5e1]">Loading credits…</p>;
	if (balance.isError || !balance.data) return <p className="text-sm text-slate-500 dark:text-[#cbd5e1]">Could not load family credits.</p>;
	const data = balance.data;

	return (
		<div className="flex flex-col gap-4">
			<div className="flex items-center justify-between">
				<div>
					<p className="text-xs text-slate-500 dark:text-[#cbd5e1]">Available Credit</p>
					<p className="font-heading text-3xl font-extrabold text-navy-900 dark:text-[#f8fafc]">{formatMoneyMinorUnits(data.availableMinor, data.currency)}</p>
				</div>
				{data.availableMinor > 0 && (
					<Button type="button" variant="secondary" onClick={() => setShowApply((value) => !value)}>
						{showApply ? "Cancel" : "Apply to a fee"}
					</Button>
				)}
			</div>
			<dl className="grid grid-cols-2 gap-3 border-t border-slate-200 dark:border-[#334155] pt-3 text-sm">
				<div>
					<dt className="text-slate-500 dark:text-[#cbd5e1]">Pending</dt>
					<dd className="font-medium text-navy-900 dark:text-[#f8fafc]">{formatMoneyMinorUnits(data.pendingMinor, data.currency)}</dd>
				</div>
				<div>
					<dt className="text-slate-500 dark:text-[#cbd5e1]">Applied to date</dt>
					<dd className="font-medium text-navy-900 dark:text-[#f8fafc]">{formatMoneyMinorUnits(data.appliedAllTimeMinor, data.currency)}</dd>
				</div>
				{data.expiringSoonMinor > 0 && (
					<div className="col-span-2">
						<dt className="text-slate-500 dark:text-[#cbd5e1]">Expiring within 30 days</dt>
						<dd className="font-medium text-amber-700">{formatMoneyMinorUnits(data.expiringSoonMinor, data.currency)}</dd>
					</div>
				)}
			</dl>
			{showApply && (
				<ApplyCreditForm
					organizationId={organizationId}
					householdId={householdId}
					availableMinor={data.availableMinor}
					currency={data.currency}
					onDone={() => setShowApply(false)}
				/>
			)}
			{data.p2pTransferEnabled && (data.availableMinor > 0 || showTransfer) && (
				<div className="border-t border-slate-200 dark:border-[#334155] pt-3">
					{!showTransfer && (
						<Button type="button" variant="secondary" onClick={() => setShowTransfer(true)}>
							Send credit to another family
						</Button>
					)}
					{showTransfer && (
						<TransferCreditForm
							organizationId={organizationId}
							householdId={householdId}
							availableMinor={data.availableMinor}
							onDone={() => setShowTransfer(false)}
						/>
					)}
				</div>
			)}
		</div>
	);
}

function ApplyCreditForm({
	organizationId,
	householdId,
	availableMinor,
	currency,
	onDone,
}: {
	organizationId: string;
	householdId: string;
	availableMinor: number;
	currency: string;
	onDone: () => void;
}) {
	const feeAssignments = useFeeAssignments(organizationId, householdId);
	const applyCredit = useApplyFamilyCredit(organizationId, householdId);
	const [feeAssignmentId, setFeeAssignmentId] = useState("");
	const [amount, setAmount] = useState("");
	const [error, setError] = useState<string | null>(null);
	const outstanding = (feeAssignments.data?.items ?? []).filter((item) => item.balanceMinor > 0);

	async function onSubmit(event: React.FormEvent) {
		event.preventDefault();
		setError(null);
		const amountMinor = Math.round(Number(amount) * 100);
		if (!feeAssignmentId) { setError("Choose a fee to apply credit toward."); return; }
		if (!amountMinor || amountMinor <= 0 || amountMinor > availableMinor) { setError("Enter a valid amount, up to your available credit."); return; }
		try {
			await applyCredit.mutateAsync({ feeAssignmentId, amountMinor });
			onDone();
		} catch {
			setError("Could not apply credit. Please try again.");
		}
	}

	return (
		<form onSubmit={onSubmit} className="flex flex-col gap-3 rounded-md bg-ice-white dark:bg-[#0f172a] p-3" noValidate>
			<label className="flex flex-col gap-1 text-sm font-medium text-navy-900 dark:text-[#f8fafc]">
				Fee
				<select value={feeAssignmentId} onChange={(event) => setFeeAssignmentId(event.target.value)} className="min-h-11 rounded-md border border-slate-300 dark:border-[#334155] px-3 py-2 font-normal">
					<option value="">Select…</option>
					{outstanding.map((item) => (
						<option key={item.id} value={item.id}>{item.description} — {formatMoneyMinorUnits(item.balanceMinor, item.currency)} due</option>
					))}
				</select>
			</label>
			<label className="flex flex-col gap-1 text-sm font-medium text-navy-900 dark:text-[#f8fafc]">
				Amount ({currency}, up to {formatMoneyMinorUnits(availableMinor, currency)})
				<input type="number" min="0.01" step="0.01" value={amount} onChange={(event) => setAmount(event.target.value)} className="min-h-11 rounded-md border border-slate-300 dark:border-[#334155] px-3 py-2 font-normal" />
			</label>
			{error && <p role="alert" className="text-sm text-error-red">{error}</p>}
			<Button type="submit" disabled={applyCredit.isPending}>{applyCredit.isPending ? "Applying…" : "Apply credit"}</Button>
		</form>
	);
}

function TransferCreditForm({
	organizationId,
	householdId,
	availableMinor,
	onDone,
}: {
	organizationId: string;
	householdId: string;
	availableMinor: number;
	onDone: () => void;
}) {
	const transfer = useTransferFamilyCredit(organizationId, householdId);
	const [toHouseholdId, setToHouseholdId] = useState("");
	const [amount, setAmount] = useState("");
	const [error, setError] = useState<string | null>(null);
	const [submitted, setSubmitted] = useState(false);

	async function onSubmit(event: React.FormEvent) {
		event.preventDefault();
		setError(null);
		const amountMinor = Math.round(Number(amount) * 100);
		if (!toHouseholdId.trim()) { setError("Enter the recipient household's ID."); return; }
		if (!amountMinor || amountMinor <= 0 || amountMinor > availableMinor) { setError("Enter a valid amount, up to your available credit."); return; }
		try {
			await transfer.mutateAsync({ toHouseholdId: toHouseholdId.trim(), amountMinor });
			setSubmitted(true);
		} catch {
			setError("Could not transfer credit. Please try again.");
		}
	}

	if (submitted) {
		return (
			<div className="mt-3 flex flex-col gap-2 rounded-md bg-ice-white dark:bg-[#0f172a] p-3">
				<p className="text-sm font-medium text-navy-900 dark:text-[#f8fafc]">Transfer request sent.</p>
				<p className="text-xs text-slate-500 dark:text-[#cbd5e1]">The amount is held from your available credit until your organization reviews and approves the transfer.</p>
				<Button type="button" variant="secondary" onClick={onDone}>Done</Button>
			</div>
		);
	}

	return (
		<form onSubmit={onSubmit} className="mt-3 flex flex-col gap-3 rounded-md bg-ice-white dark:bg-[#0f172a] p-3" noValidate>
			<p className="text-xs text-slate-500 dark:text-[#cbd5e1]">Ask the other family for their household ID — a searchable directory isn&rsquo;t available yet. Transfers are held until your organization reviews and approves them.</p>
			<label className="flex flex-col gap-1 text-sm font-medium text-navy-900 dark:text-[#f8fafc]">
				Recipient household ID
				<input value={toHouseholdId} onChange={(event) => setToHouseholdId(event.target.value)} className="min-h-11 rounded-md border border-slate-300 dark:border-[#334155] px-3 py-2 font-normal" />
			</label>
			<label className="flex flex-col gap-1 text-sm font-medium text-navy-900 dark:text-[#f8fafc]">
				Amount, up to {formatMoneyMinorUnits(availableMinor, "USD")}
				<input type="number" min="0.01" step="0.01" value={amount} onChange={(event) => setAmount(event.target.value)} className="min-h-11 rounded-md border border-slate-300 dark:border-[#334155] px-3 py-2 font-normal" />
			</label>
			{error && <p role="alert" className="text-sm text-error-red">{error}</p>}
			<div className="flex gap-2">
				<Button type="submit" disabled={transfer.isPending}>{transfer.isPending ? "Sending…" : "Request transfer"}</Button>
				<Button type="button" variant="secondary" disabled={transfer.isPending} onClick={onDone}>Cancel</Button>
			</div>
		</form>
	);
}
