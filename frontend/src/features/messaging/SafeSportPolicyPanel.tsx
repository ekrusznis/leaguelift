import { useState, type FormEvent } from "react";
import { Button } from "../../components/Button";
import { EmptyState } from "../../components/states/EmptyState";
import { ErrorState } from "../../components/states/ErrorState";
import { LoadingState } from "../../components/states/LoadingState";
import { useCreateMessageContactRestriction, useGuardianMessagingParticipants, useLiftMessageContactRestriction, useMyMessageContactRestrictions } from "./api";
import type { MessageContactRestrictionKind } from "./types";

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
