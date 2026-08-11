import { useAuth } from "../../auth/AuthContext";
import { Button } from "../../components/Button";
import { EmptyState } from "../../components/states/EmptyState";
import { ErrorState } from "../../components/states/ErrorState";
import { LoadingState } from "../../components/states/LoadingState";
import { useHouseholdProfileCorrections, useWithdrawProfileCorrection } from "./api";
import { PROFILE_CORRECTION_FIELD_LABELS, PROFILE_CORRECTION_STATUS_LABELS } from "./labels";

export function HouseholdCorrectionRequestsPanel({ organizationId, householdId }: { organizationId: string; householdId: string }) {
	const { user } = useAuth();
	const query = useHouseholdProfileCorrections(organizationId, householdId);
	const withdraw = useWithdrawProfileCorrection(organizationId, householdId);

	if (query.isLoading) return <LoadingState label="Loading correction requests…" />;
	if (query.isError) return <ErrorState message="Could not load correction requests." onRetry={() => query.refetch()} />;
	if (!query.data?.length) return <EmptyState title="No correction requests" description="Requests submitted for adult or participant profile changes will appear here." />;

	return (
		<ul className="flex flex-col gap-3" aria-label="Profile correction requests">
			{query.data.map((request) => (
				<li key={request.id} className="rounded-lg border border-slate-gray/20 bg-white dark:bg-[#111827] p-4">
					<div className="flex flex-wrap items-start justify-between gap-3">
						<div className="min-w-0 flex-1">
							<p className="font-medium text-navy dark:text-[#f8fafc]">{request.targetLabel} · {PROFILE_CORRECTION_FIELD_LABELS[request.field]}</p>
							<p className="mt-1 break-words text-sm text-slate-gray dark:text-[#cbd5e1]">
								<span className="font-medium">Current:</span> {request.currentValue || "Not on file"} → <span className="font-medium">Requested:</span> {request.proposedValue}
							</p>
							<p className="mt-2 break-words text-sm text-slate-gray dark:text-[#cbd5e1]">{request.reason}</p>
							{request.reviewNote && <p className="mt-2 text-sm text-slate-gray dark:text-[#cbd5e1]"><span className="font-medium">Review note:</span> {request.reviewNote}</p>}
						</div>
						<span className="shrink-0 rounded-full bg-navy/10 px-2 py-1 text-xs font-medium text-navy dark:text-[#f8fafc]">
							{PROFILE_CORRECTION_STATUS_LABELS[request.status]}
						</span>
					</div>
					<div className="mt-3 flex flex-wrap items-center justify-between gap-2 text-xs text-slate-gray dark:text-[#cbd5e1]">
						<span>Requested {new Date(request.requestedAt).toLocaleString()}</span>
						{request.status === "PENDING" && request.requesterEmail.toLowerCase() === user?.email.toLowerCase() && (
							<Button type="button" variant="secondary" onClick={() => withdraw.mutate(request.id)} disabled={withdraw.isPending}>
								Withdraw
							</Button>
						)}
					</div>
				</li>
			))}
		</ul>
	);
}
