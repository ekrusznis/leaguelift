import { Link, useParams } from "react-router-dom";
import { ErrorState } from "../../components/states/ErrorState";
import { LoadingState } from "../../components/states/LoadingState";
import { EmptyState } from "../../components/states/EmptyState";
import { useDisputes } from "./api";
import { STATUS_COLORS, STATUS_LABELS } from "./statusLabels";
import type { DisputeSourceType } from "./types";

const SOURCE_TYPE_LABELS: Record<DisputeSourceType, string> = {
	CONTRIBUTION: "Campaign contribution",
	ORDER: "Store order",
	SPONSORSHIP: "Sponsorship",
	FEE_PAYMENT: "Fee payment",
};

function formatAmount(amountMinor: number, currency: string) {
	return new Intl.NumberFormat("en-US", { style: "currency", currency }).format(amountMinor / 100);
}

function formatDate(value: string | null) {
	if (!value) return "—";
	return new Date(value).toLocaleDateString("en-US", { year: "numeric", month: "short", day: "numeric" });
}

export function DisputesPage() {
	const { organizationId } = useParams<{ organizationId: string }>();
	const { data, isLoading, isError, refetch } = useDisputes(organizationId ?? "");

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

			{isLoading && <LoadingState label="Loading disputes…" />}
			{isError && <ErrorState message="Could not load disputes." onRetry={() => refetch()} />}
			{data && data.length === 0 && <EmptyState title="No disputes" description="This organization has no Stripe disputes on record." />}
			{data && data.length > 0 && (
				<div className="overflow-x-auto rounded-lg border border-slate-gray/20">
					<table className="w-full text-left text-sm">
						<thead className="bg-ice-white dark:bg-[#0f172a] text-slate-gray dark:text-[#cbd5e1]">
							<tr>
								<th className="p-3 font-medium">Source</th>
								<th className="p-3 font-medium">Amount</th>
								<th className="p-3 font-medium">Reason</th>
								<th className="p-3 font-medium">Opened</th>
								<th className="p-3 font-medium">Evidence due</th>
								<th className="p-3 font-medium">Status</th>
							</tr>
						</thead>
						<tbody>
							{data.map((dispute) => (
								<tr key={dispute.id} className="border-t border-slate-gray/10">
									<td className="p-3">{SOURCE_TYPE_LABELS[dispute.sourceType]}</td>
									<td className="p-3 font-semibold">{formatAmount(dispute.amountMinor, dispute.currency)}</td>
									<td className="p-3 text-slate-gray dark:text-[#cbd5e1]">{dispute.reason}</td>
									<td className="p-3">{formatDate(dispute.openedAt)}</td>
									<td className="p-3">{formatDate(dispute.evidenceDueBy)}</td>
									<td className="p-3">
										<span className={`rounded-full px-2 py-0.5 text-xs font-medium ${STATUS_COLORS[dispute.status]}`}>
											{STATUS_LABELS[dispute.status]}
										</span>
									</td>
								</tr>
							))}
						</tbody>
					</table>
				</div>
			)}
		</div>
	);
}
