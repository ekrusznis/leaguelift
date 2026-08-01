import { useState } from "react";
import { Button } from "../../components/Button";
import { EmptyState } from "../../components/states/EmptyState";
import { ErrorState } from "../../components/states/ErrorState";
import { LoadingState } from "../../components/states/LoadingState";
import { ApiError } from "../../lib/apiError";
import { useOrganizationProfileCorrections, useReviewProfileCorrection } from "./api";
import { PROFILE_CORRECTION_FIELD_LABELS, PROFILE_CORRECTION_STATUS_LABELS } from "./labels";
import type { ProfileCorrectionStatus } from "./types";

export function OrganizationCorrectionReviewPanel({ organizationId }: { organizationId: string }) {
	const [status, setStatus] = useState<ProfileCorrectionStatus | "ALL">("PENDING");
	const query = useOrganizationProfileCorrections(organizationId, status === "ALL" ? undefined : status);
	const review = useReviewProfileCorrection(organizationId);
	const [notes, setNotes] = useState<Record<string, string>>({});

	if (query.isLoading) return <LoadingState label="Loading correction requests…" />;
	if (query.isError) return <ErrorState message="Could not load correction requests." onRetry={() => query.refetch()} />;

	return (
		<div className="flex flex-col gap-4">
			<div className="flex flex-wrap items-center gap-2">
				<label htmlFor="correction-status-filter" className="text-sm font-medium text-navy">Status</label>
				<select
					id="correction-status-filter"
					value={status}
					onChange={(event) => setStatus(event.target.value as ProfileCorrectionStatus | "ALL")}
					className="min-h-11 rounded-md border border-slate-gray/30 bg-white px-3 py-2"
				>
					<option value="PENDING">Pending</option>
					<option value="APPROVED">Approved</option>
					<option value="REJECTED">Rejected</option>
					<option value="WITHDRAWN">Withdrawn</option>
					<option value="ALL">All</option>
				</select>
			</div>

			{query.data?.items.length === 0 && <EmptyState title="No matching correction requests" description="New guardian, athlete, or scoped-staff requests will appear here for review." />}
			{query.data && query.data.items.length > 0 && (
				<ul className="flex flex-col gap-3" aria-label="Organization profile correction requests">
					{query.data.items.map((request) => {
						const note = notes[request.id] ?? "";
						const errorMessage = review.error instanceof ApiError ? review.error.message : review.isError ? "Could not review the request." : null;
						return (
							<li key={request.id} className="rounded-lg border border-slate-gray/20 bg-white p-4">
								<div className="flex flex-wrap items-start justify-between gap-3">
									<div className="min-w-0 flex-1">
										<p className="font-medium text-navy">{request.targetLabel} · {PROFILE_CORRECTION_FIELD_LABELS[request.field]}</p>
										<p className="mt-1 break-words text-sm text-slate-gray">
											<span className="font-medium">Current:</span> {request.currentValue || "Not on file"} → <span className="font-medium">Requested:</span> {request.proposedValue}
										</p>
										<p className="mt-2 text-sm text-slate-gray"><span className="font-medium">Reason:</span> {request.reason}</p>
										<p className="mt-1 text-xs text-slate-gray">Requested by {request.requesterName} ({request.requesterEmail}) · {new Date(request.requestedAt).toLocaleString()}</p>
									</div>
									<span className="shrink-0 rounded-full bg-navy/10 px-2 py-1 text-xs font-medium text-navy">{PROFILE_CORRECTION_STATUS_LABELS[request.status]}</span>
								</div>
								{request.status === "PENDING" ? (
									<div className="mt-4 flex flex-col gap-2">
										<label htmlFor={`review-note-${request.id}`} className="text-sm font-medium text-navy">Review note</label>
										<textarea
											id={`review-note-${request.id}`}
											value={note}
											onChange={(event) => setNotes((current) => ({ ...current, [request.id]: event.target.value }))}
											maxLength={500}
											rows={2}
											placeholder="Optional for approval; required for rejection"
											className="rounded-md border border-slate-gray/30 px-3 py-2"
										/>
										{errorMessage && <p role="alert" className="text-sm text-error-red">{errorMessage}</p>}
										<div className="flex justify-end gap-2">
											<Button type="button" variant="secondary" disabled={review.isPending || note.trim().length < 3} onClick={() => review.mutate({ requestId: request.id, decision: "reject", reviewNote: note })}>Reject</Button>
											<Button type="button" disabled={review.isPending} onClick={() => review.mutate({ requestId: request.id, decision: "approve", reviewNote: note })}>Approve correction</Button>
										</div>
									</div>
								) : request.reviewNote ? (
									<p className="mt-3 text-sm text-slate-gray"><span className="font-medium">Review note:</span> {request.reviewNote}</p>
								) : null}
							</li>
						);
					})}
				</ul>
			)}
		</div>
	);
}
