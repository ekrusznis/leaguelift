import { useState, type FormEvent } from "react";
import { Button } from "../../components/Button";
import { EmptyState } from "../../components/states/EmptyState";
import { ErrorState } from "../../components/states/ErrorState";
import { LoadingState } from "../../components/states/LoadingState";
import { useCreateMessageContactRestriction, useGuardianMessagingParticipants, useLiftMessageContactRestriction, useMessageSafeSportPolicy, useMyMessageContactRestrictions, useRecordSafeSportReview } from "./api";
import type { MessageContactRestrictionKind, MessageSafeSportReviewStatus } from "./types";

const STATUS_LABELS: Record<MessageSafeSportReviewStatus, string> = {
	PENDING: "Pending review",
	APPROVED: "Approved",
	REJECTED: "Rejected",
};

const STATUS_CLASSES: Record<MessageSafeSportReviewStatus, string> = {
	PENDING: "bg-championship-gold/10 text-championship-gold",
	APPROVED: "bg-victory-green/10 text-victory-green",
	REJECTED: "bg-error-red/10 text-error-red",
};

/**
 * Org-level messaging SafeSport/compliance gate (Phase 25.4, ADR-077) — status is
 * visible to org owners/admins; the review action itself is Platform-Admin-only,
 * server-side (MessageSafeSportService.recordReview), reached here only through an
 * active reasoned support-access session (same "existing domain-service UI reused for
 * customer data" pattern as every other Platform Admin org action, ADR-043). Added
 * Phase 37 slice 37.2 — this policy previously had no UI path off PENDING at all.
 */
export function SafeSportOrgPolicyPanel({ organizationId, canReview }: { organizationId: string; canReview: boolean }) {
	const policy = useMessageSafeSportPolicy(organizationId);
	const recordReview = useRecordSafeSportReview(organizationId);
	const [reviewStatus, setReviewStatus] = useState<MessageSafeSportReviewStatus>("APPROVED");
	const [athleteMessagingEnabled, setAthleteMessagingEnabled] = useState(false);
	const [reviewReference, setReviewReference] = useState("");
	const [showForm, setShowForm] = useState(false);
	const [error, setError] = useState<string | null>(null);

	if (policy.isLoading) return <LoadingState label="Loading SafeSport policy…" />;
	if (policy.isError || !policy.data) return <ErrorState message="Could not load the SafeSport/compliance policy." onRetry={() => policy.refetch()} />;
	const data = policy.data;

	async function submit(event: FormEvent) {
		event.preventDefault();
		setError(null);
		try {
			await recordReview.mutateAsync({
				reviewStatus,
				athleteMessagingEnabled: reviewStatus === "APPROVED" && athleteMessagingEnabled,
				reviewReference: reviewReference.trim() || undefined,
			});
			setShowForm(false);
			setReviewReference("");
		} catch (err) {
			setError(err instanceof Error ? err.message : "The review could not be recorded.");
		}
	}

	return (
		<section className="rounded-lg border border-slate-gray/20 bg-ice-white p-4" aria-labelledby="safesport-policy-heading">
			<div className="flex flex-wrap items-center justify-between gap-3">
				<h3 id="safesport-policy-heading" className="font-heading text-lg font-semibold text-navy">
					Messaging SafeSport / Compliance Review
				</h3>
				<span className={`rounded-full px-2 py-0.5 text-xs font-medium ${STATUS_CLASSES[data.reviewStatus]}`}>{STATUS_LABELS[data.reviewStatus]}</span>
			</div>
			<p className="mt-1 text-sm text-slate-gray">
				Athlete-created peer messaging stays disabled organization-wide until a Rally26 platform administrator records an approved
				compliance review with a durable reference.
			</p>
			<dl className="mt-3 grid gap-2 text-sm sm:grid-cols-2">
				<div>
					<dt className="text-slate-gray">Athlete messaging</dt>
					<dd className="font-medium text-navy">{data.athleteMessagingEnabled ? "Enabled" : "Disabled"}</dd>
				</div>
				{data.reviewReference && (
					<div className="sm:col-span-2">
						<dt className="text-slate-gray">Review reference</dt>
						<dd className="break-words text-navy">{data.reviewReference}</dd>
					</div>
				)}
				{data.reviewedAt && (
					<div className="sm:col-span-2">
						<dt className="text-slate-gray">Reviewed</dt>
						<dd className="text-navy">{new Date(data.reviewedAt).toLocaleString()}</dd>
					</div>
				)}
			</dl>

			{canReview && !showForm && (
				<Button type="button" variant="secondary" className="mt-3" onClick={() => setShowForm(true)}>
					Record review
				</Button>
			)}
			{canReview && showForm && (
				<form onSubmit={(event) => void submit(event)} className="mt-4 flex flex-col gap-3 rounded-md border border-slate-gray/20 bg-pure-white p-3" noValidate>
					<div>
						<label htmlFor="safesport-status" className="text-sm font-medium text-navy">Decision</label>
						<select
							id="safesport-status"
							value={reviewStatus}
							onChange={(event) => {
								const value = event.target.value as MessageSafeSportReviewStatus;
								setReviewStatus(value);
								if (value !== "APPROVED") setAthleteMessagingEnabled(false);
							}}
							className="mt-1 min-h-11 w-full rounded-md border border-slate-gray/30 px-3 py-2 sm:w-64"
						>
							<option value="APPROVED">Approve</option>
							<option value="REJECTED">Reject</option>
						</select>
					</div>
					<div>
						<label htmlFor="safesport-reference" className="text-sm font-medium text-navy">
							Review reference <span aria-hidden>*</span>
						</label>
						<textarea
							id="safesport-reference"
							value={reviewReference}
							onChange={(event) => setReviewReference(event.target.value)}
							minLength={3}
							maxLength={500}
							rows={2}
							placeholder="Durable reference for this decision (e.g. review ticket id, counsel sign-off note)"
							className="mt-1 w-full rounded-md border border-slate-gray/30 px-3 py-2"
						/>
					</div>
					{reviewStatus === "APPROVED" && (
						<label className="flex items-center gap-2 text-sm text-navy">
							<input
								type="checkbox"
								checked={athleteMessagingEnabled}
								onChange={(event) => setAthleteMessagingEnabled(event.target.checked)}
							/>
							Enable athlete-created peer messaging for this organization
						</label>
					)}
					{error && <p role="alert" className="text-sm text-error-red">{error}</p>}
					<div className="flex justify-end gap-2">
						<Button type="button" variant="secondary" onClick={() => { setShowForm(false); setError(null); }}>
							Cancel
						</Button>
						<Button type="submit" disabled={recordReview.isPending || reviewReference.trim().length < 3}>
							{recordReview.isPending ? "Saving…" : "Save review"}
						</Button>
					</div>
				</form>
			)}
		</section>
	);
}

export function GuardianMessageSafetyControls() {
	const participants = useGuardianMessagingParticipants();
	const restrictions = useMyMessageContactRestrictions();
	const createRestriction = useCreateMessageContactRestriction();
	const liftRestriction = useLiftMessageContactRestriction();
	const [participantKey, setParticipantKey] = useState("");
	const [kind, setKind] = useState<MessageContactRestrictionKind>("ADULT_TO_MINOR");
	const [note, setNote] = useState("");
	const [notice, setNotice] = useState<string | null>(null);
	const participant = participants.data?.find((item) => `${item.organizationId}:${item.participantId}` === participantKey) ?? participants.data?.[0];

	async function submit(event: FormEvent) {
		event.preventDefault();
		if (!participant) return;
		setNotice(null);
		try {
			await createRestriction.mutateAsync({ organizationId: participant.organizationId, participantId: participant.participantId, kind, note: note.trim() || undefined });
			setNote(""); setNotice("Communication restriction recorded.");
		} catch (error) { setNotice(error instanceof Error ? error.message : "The restriction could not be recorded."); }
	}

	return <section className="mt-6 rounded-lg border border-slate-gray/20 bg-ice-white p-4" aria-labelledby="guardian-message-safety-heading">
		<h3 id="guardian-message-safety-heading" className="font-heading text-lg font-semibold text-navy">Guardian communication controls</h3>
		<p className="mt-1 text-sm text-slate-gray">Record a request to stop staff messaging to your athlete, or all Rally26 messaging for that athlete. Requests are retained as safety history.</p>
		{participants.isLoading && <LoadingState label="Loading linked athletes…" />}
		{participants.isError && <ErrorState message="Could not load linked athletes." onRetry={() => participants.refetch()} />}
		{participants.data?.length === 0 && <EmptyState title="No linked athletes" description="Communication restrictions appear when your guardian account is linked to an athlete." />}
		{participants.data && participants.data.length > 0 && <form onSubmit={(event) => void submit(event)} className="mt-4 grid gap-3 md:grid-cols-2">
			<div><label htmlFor="restriction-athlete" className="text-sm font-medium text-navy">Athlete</label><select id="restriction-athlete" value={participant ? `${participant.organizationId}:${participant.participantId}` : ""} onChange={(e) => setParticipantKey(e.target.value)} className="mt-1 min-h-11 w-full rounded-md border border-slate-gray/30 px-3 py-2">{participants.data.map((item) => <option key={item.participantId} value={`${item.organizationId}:${item.participantId}`}>{item.displayName}</option>)}</select></div>
			<div><label htmlFor="restriction-kind" className="text-sm font-medium text-navy">Restriction</label><select id="restriction-kind" value={kind} onChange={(e) => setKind(e.target.value as MessageContactRestrictionKind)} className="mt-1 min-h-11 w-full rounded-md border border-slate-gray/30 px-3 py-2"><option value="ADULT_TO_MINOR">Stop staff → athlete messages</option><option value="ALL_MESSAGING">Stop all messaging for athlete</option></select></div>
			<div className="md:col-span-2"><label htmlFor="restriction-note" className="text-sm font-medium text-navy">Optional note</label><textarea id="restriction-note" maxLength={1000} value={note} onChange={(e) => setNote(e.target.value)} rows={2} className="mt-1 w-full rounded-md border border-slate-gray/30 px-3 py-2" /></div>
			<div className="md:col-span-2"><Button type="submit" disabled={createRestriction.isPending}>Record restriction</Button></div>
		</form>}
		{restrictions.data && restrictions.data.length > 0 && <ul className="mt-4 flex flex-col gap-2">{restrictions.data.map((item) => <li key={item.id} className="rounded-md border border-slate-gray/20 bg-pure-white p-3"><div className="flex flex-wrap items-start justify-between gap-2"><div><p className="font-medium text-navy">{item.participantDisplayName}</p><p className="text-xs text-slate-gray">{item.kind === "ALL_MESSAGING" ? "All messaging" : "Staff → athlete"} · {item.status}</p></div>{item.status === "ACTIVE" && <Button type="button" variant="secondary" disabled={liftRestriction.isPending} onClick={() => { const reason=window.prompt("Why are you lifting this restriction?"); if (reason?.trim()) liftRestriction.mutate({ restrictionId: item.id, note: reason.trim() }); }}>Lift</Button>}</div></li>)}</ul>}
		{notice && <p role="status" className="mt-3 text-sm text-slate-gray">{notice}</p>}
	</section>;
}
