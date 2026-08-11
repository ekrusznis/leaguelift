import { useState } from "react";
import { Button } from "../../components/Button";
import { ErrorState } from "../../components/states/ErrorState";
import { LoadingState } from "../../components/states/LoadingState";
import { ApiError } from "../../lib/apiError";
import { formatMoneyMinorUnits } from "../../lib/money";
import { usePayoutSummary, useTriggerTransfer } from "./api";

/**
 * ADR-017 (Phase 5 slice 1) — manual-trigger-only transfers, no scheduler exists yet.
 * Like PayoutConnectPanel, "Transfer now" is shown to any org member rather than
 * hidden by role: the backend enforces OWNER/ADMINISTRATOR
 * (PayoutAccountService.triggerTransfer), and a non-manager gets the 403 surfaced
 * inline (DESIGN-DOC.md section 4.4 "show, don't hide").
 */
export function PayoutSummaryPanel({ organizationId, currency = "USD" }: { organizationId: string; currency?: string }) {
	const { data: summary, isLoading, isError, refetch } = usePayoutSummary(organizationId);
	const triggerTransfer = useTriggerTransfer(organizationId);
	const [error, setError] = useState<string | null>(null);

	if (isLoading) {
		return <LoadingState label="Loading payout balance…" />;
	}
	if (isError || !summary) {
		return <ErrorState message="Could not load payout balance." onRetry={() => refetch()} />;
	}

	async function handleTransfer() {
		setError(null);
		try {
			await triggerTransfer.mutateAsync();
		} catch (e) {
			setError(e instanceof ApiError ? e.message : "Could not start the transfer. Please try again.");
		}
	}

	const isNegative = summary.netAvailableMinor < 0;

	return (
		<div className="flex flex-col gap-3 rounded-lg border border-slate-gray/20 bg-pure-white dark:bg-[#111827] p-4">
			<div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
				<Figure label="Eligible now" amountMinor={summary.eligibleMinor} currency={currency} />
				<Figure label="Held (holding period)" amountMinor={summary.heldMinor} currency={currency} />
				<Figure label="Pending debits" amountMinor={summary.pendingDebitsMinor} currency={currency} />
				<Figure
					label="Net available"
					amountMinor={summary.netAvailableMinor}
					currency={currency}
					emphasize
					negative={isNegative}
				/>
			</div>
			{isNegative && (
				<p className="text-sm text-slate-gray dark:text-[#cbd5e1]">
					A refund exceeded what's currently eligible — this negative balance will be deducted from the next transfer.
				</p>
			)}
			{error && (
				<p role="alert" className="text-sm text-error-red">
					{error}
				</p>
			)}
			<div>
				<Button
					type="button"
					variant="secondary"
					onClick={handleTransfer}
					disabled={triggerTransfer.isPending || summary.netAvailableMinor <= 0}
				>
					{triggerTransfer.isPending ? "Transferring…" : "Transfer now"}
				</Button>
			</div>
		</div>
	);
}

function Figure({
	label,
	amountMinor,
	currency,
	emphasize = false,
	negative = false,
}: {
	label: string;
	amountMinor: number;
	currency: string;
	emphasize?: boolean;
	negative?: boolean;
}) {
	return (
		<div>
			<p className="text-xs text-slate-gray dark:text-[#cbd5e1]">{label}</p>
			<p className={`${emphasize ? "text-lg font-semibold" : "text-sm font-medium"} ${negative ? "text-error-red" : "text-navy dark:text-[#f8fafc]"}`}>
				{formatMoneyMinorUnits(amountMinor, currency)}
			</p>
		</div>
	);
}
