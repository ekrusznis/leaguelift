import { useState } from "react";
import { Button } from "../../components/Button";
import { EmptyState } from "../../components/states/EmptyState";
import { ErrorState } from "../../components/states/ErrorState";
import { LoadingState } from "../../components/states/LoadingState";
import { ApiError } from "../../lib/apiError";
import { formatMoneyMinorUnits } from "../../lib/money";
import { useOrgContributions, useRefundContribution } from "./api";

export function ContributionList({ organizationId, campaignId }: { organizationId: string; campaignId: string }) {
	const { data, isLoading, isError, refetch } = useOrgContributions(organizationId, campaignId);
	const refundContribution = useRefundContribution(organizationId, campaignId);
	const [refundError, setRefundError] = useState<string | null>(null);

	if (isLoading) return <LoadingState label="Loading contributions…" />;
	if (isError) return <ErrorState message="Could not load contributions." onRetry={() => refetch()} />;
	if (!data || data.items.length === 0) {
		return <EmptyState title="No contributions yet" description="Confirmed contributions will appear here once a supporter checks out." />;
	}

	async function handleRefund(contributionId: string) {
		setRefundError(null);
		try {
			await refundContribution.mutateAsync(contributionId);
		} catch (e) {
			setRefundError(e instanceof ApiError ? e.message : "Could not refund this contribution. Please try again.");
		}
	}

	return (
		<div className="flex flex-col gap-2">
			{refundError && (
				<p role="alert" className="text-sm text-error-red">
					{refundError}
				</p>
			)}
			<ul className="flex flex-col gap-2" aria-label="Contributions">
				{data.items.map((contribution) => (
					<li key={contribution.id} className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-slate-gray/20 bg-pure-white p-3">
						<div className="min-w-0 flex-1">
							<p className="break-words font-medium text-navy">
								{contribution.isAnonymous ? "Anonymous supporter" : contribution.supporterName ?? "Anonymous supporter"}
								{contribution.paymentSource === "OFFLINE" && (
									<span className="ml-2 rounded-full bg-amber-100 px-2 py-0.5 text-xs font-medium text-amber-800">Recorded offline</span>
								)}
								{contribution.status === "REFUNDED" && (
									<span className="ml-2 rounded-full bg-ice-white px-2 py-0.5 text-xs font-medium text-slate-gray">Refunded</span>
								)}
							</p>
							<p className="text-sm text-slate-gray">{formatMoneyMinorUnits(contribution.amountMinor, contribution.currency)}</p>
						</div>
						{contribution.status === "CONFIRMED" && contribution.paymentSource === "STRIPE" && (
							<Button
								type="button"
								variant="danger"
								className="shrink-0"
								onClick={() => handleRefund(contribution.id)}
								disabled={refundContribution.isPending}
							>
								{refundContribution.isPending ? "Refunding…" : "Refund"}
							</Button>
						)}
					</li>
				))}
			</ul>
		</div>
	);
}
