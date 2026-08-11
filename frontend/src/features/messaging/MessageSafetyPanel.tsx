import { useState, type FormEvent } from "react";
import { Button } from "../../components/Button";
import { EmptyState } from "../../components/states/EmptyState";
import { ErrorState } from "../../components/states/ErrorState";
import { LoadingState } from "../../components/states/LoadingState";
import {
	useManagedMessageReports,
	useModerationEvents,
	useMyMessageReports,
	useReportMessage,
	useReviewMessageReport,
	useSafetyLockMessageThread,
	useSafetyUnlockMessageThread,
} from "./api";
import type { MessageManagementScope, MessageSafetyReportReason, MessageSafetyReportStatus } from "./types";

const REASON_LABELS: Record<MessageSafetyReportReason, string> = {
	HARASSMENT: "Harassment",
	BULLYING: "Bullying",
	INAPPROPRIATE_CONTENT: "Inappropriate content",
	SAFETY_CONCERN: "Safety concern",
	SPAM: "Spam",
	OTHER: "Other",
};

/** Mirrors the backend's TRIAGE_ORDER severity ranking (MessageSafetyRepository.kt, Phase 37 slice 37.4) so the queue's sort order is visually explained, not just applied silently. */
const REASON_SEVERITY_CLASSES: Record<MessageSafetyReportReason, string> = {
	SAFETY_CONCERN: "bg-error-red/10 text-error-red",
	HARASSMENT: "bg-championship-gold/10 text-championship-gold",
	BULLYING: "bg-championship-gold/10 text-championship-gold",
	INAPPROPRIATE_CONTENT: "bg-info-blue/10 text-info-blue",
	SPAM: "bg-slate-gray/10 text-slate-gray",
	OTHER: "bg-slate-gray/10 text-slate-gray",
};

function formatDate(value: string) {
	return new Intl.DateTimeFormat(undefined, { dateStyle: "medium", timeStyle: "short" }).format(new Date(value));
}

export function ReportMessageControl({ messageId }: { messageId: string }) {
	const report = useReportMessage();
	const [reason, setReason] = useState<MessageSafetyReportReason>("SAFETY_CONCERN");
	const [details, setDetails] = useState("");
	const [notice, setNotice] = useState<string | null>(null);

	async function submit(event: FormEvent<HTMLFormElement>) {
		event.preventDefault();
		setNotice(null);
		try {
			await report.mutateAsync({ messageId, reason, details });
			setDetails("");
			setNotice("Report submitted for safety review.");
		} catch (error) {
			setNotice(error instanceof Error ? error.message : "The report could not be submitted.");
		}
	}

	return (
		<details className="mt-3 text-xs text-slate-gray">
			<summary className="cursor-pointer font-semibold text-navy">Report message</summary>
			<form onSubmit={(event) => void submit(event)} className="mt-2 grid gap-2 rounded-md border border-slate-gray/20 bg-pure-white p-3">
				<label>Reason<select value={reason} onChange={(event) => setReason(event.target.value as MessageSafetyReportReason)} className="mt-1 min-h-10 w-full rounded-md border border-slate-gray/30 px-2 py-1 text-sm text-navy">{Object.entries(REASON_LABELS).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label>
				<label>Optional context<textarea value={details} onChange={(event) => setDetails(event.target.value)} maxLength={2000} rows={3} className="mt-1 w-full rounded-md border border-slate-gray/30 px-2 py-1 text-sm text-navy" /></label>
				<div><Button type="submit" variant="secondary" disabled={report.isPending}>{report.isPending ? "Submitting…" : "Submit report"}</Button></div>
				{notice && <p role="status">{notice}</p>}
			</form>
		</details>
	);
}

export function MyMessageReportsPanel() {
	const reports = useMyMessageReports();
	if (reports.isLoading) return <LoadingState label="Loading your message reports…" />;
	if (reports.isError) return <ErrorState message="Could not load your message reports." onRetry={() => reports.refetch()} />;
	if (!reports.data || reports.data.items.length === 0) return null;
	return (
		<section aria-labelledby="my-message-reports-heading" className="rounded-xl border border-slate-gray/20 bg-pure-white p-5">
			<h2 id="my-message-reports-heading" className="font-heading text-xl font-semibold text-navy">Your safety reports</h2>
			<p className="mt-1 text-sm text-slate-gray">Reports remain private to you and authorized safety reviewers.</p>
			<ul className="mt-4 grid gap-3">{reports.data.items.map((item) => <li key={item.id} className="rounded-lg bg-ice-white p-3"><div className="flex flex-wrap justify-between gap-2"><span className="font-semibold text-navy">{item.threadTitle}</span><span className="text-xs font-semibold text-slate-gray">{item.status.replace("_", " ")}</span></div><p className="mt-1 text-xs text-slate-gray">{REASON_LABELS[item.reason]} · {formatDate(item.createdAt)}</p><p className="mt-2 line-clamp-2 text-sm text-navy">{item.messageBody}</p>{item.resolutionNote && <p className="mt-2 text-xs text-slate-gray">Review note: {item.resolutionNote}</p>}</li>)}</ul>
		</section>
	);
}

export function ManagedMessageSafetyPanel({ scope }: { scope: MessageManagementScope | undefined }) {
	const [status, setStatus] = useState<MessageSafetyReportStatus | "">("");
	const reports = useManagedMessageReports(scope?.organizationId, scope?.scopeType, scope?.scopeId, status);
	const [selectedReportId, setSelectedReportId] = useState<string>();
	const selected = reports.data?.items.find((report) => report.id === selectedReportId) ?? reports.data?.items[0];
	const events = useModerationEvents(scope?.organizationId, selected?.id);
	const review = useReviewMessageReport();
	const lock = useSafetyLockMessageThread();
	const unlock = useSafetyUnlockMessageThread();
	const [note, setNote] = useState("");
	const [notice, setNotice] = useState<string | null>(null);

	async function setReviewStatus(next: Exclude<MessageSafetyReportStatus, "OPEN">) {
		if (!scope || !selected) return;
		setNotice(null);
		try {
			await review.mutateAsync({ organizationId: scope.organizationId, reportId: selected.id, status: next, note });
			setNote("");
			setNotice(`Report moved to ${next.replace("_", " ").toLowerCase()}.`);
		} catch (error) {
			setNotice(error instanceof Error ? error.message : "The report could not be updated.");
		}
	}

	async function toggleLock() {
		if (!scope || !selected) return;
		setNotice(null);
		try {
			if (selected.threadSafetyLockedAt) {
				await unlock.mutateAsync({ organizationId: scope.organizationId, threadId: selected.threadId, note: note.trim() || "Safety review completed", reportId: selected.id });
				setNotice("Thread safety lock removed.");
			} else {
				await lock.mutateAsync({ organizationId: scope.organizationId, threadId: selected.threadId, reason: note.trim() || `Safety review: ${REASON_LABELS[selected.reason]}`, reportId: selected.id });
				setNotice("Thread safety-locked. New messages are paused.");
			}
			setNote("");
			await reports.refetch();
		} catch (error) {
			setNotice(error instanceof Error ? error.message : "The thread safety state could not be changed.");
		}
	}

	return (
		<div className="mt-8 border-t border-slate-gray/20 pt-6">
			<div className="flex flex-wrap items-center justify-between gap-3"><div><h3 className="font-heading text-lg font-semibold text-navy">Safety review</h3><p className="mt-1 text-sm text-slate-gray">Open reports sort by severity first, then oldest first. Reported messages and append-only moderation history for this scope.</p></div><label className="text-sm text-slate-gray">Report status<select value={status} onChange={(event) => setStatus(event.target.value as MessageSafetyReportStatus | "")} className="ml-2 min-h-10 rounded-md border border-slate-gray/30 px-2"><option value="">All</option><option value="OPEN">Open</option><option value="IN_REVIEW">In review</option><option value="RESOLVED">Resolved</option><option value="DISMISSED">Dismissed</option></select></label></div>
			{reports.isLoading && <LoadingState label="Loading safety reports…" />}
			{reports.isError && <ErrorState message="Could not load safety reports." onRetry={() => reports.refetch()} />}
			{reports.data?.items.length === 0 && <EmptyState title="No safety reports" description="Reported messages for this scope will appear here." />}
			{reports.data && reports.data.items.length > 0 && <div className="mt-4 grid gap-4 lg:grid-cols-[18rem_minmax(0,1fr)]"><ul className="flex flex-col gap-2">{reports.data.items.map((item) => <li key={item.id}><button type="button" onClick={() => setSelectedReportId(item.id)} className={`w-full rounded-lg border p-3 text-left ${selected?.id === item.id ? "border-victory-green bg-victory-green/5" : "border-slate-gray/20"}`}><div className="flex justify-between gap-2"><span className={`rounded-full px-2 py-0.5 text-xs font-semibold ${REASON_SEVERITY_CLASSES[item.reason]}`}>{REASON_LABELS[item.reason]}</span><span className="text-xs text-slate-gray">{item.status.replace("_", " ")}</span></div><p className="mt-1 text-xs text-slate-gray">{item.reporterDisplayName} · {item.threadTitle}</p><p className="mt-2 line-clamp-2 text-sm text-slate-gray">{item.messageBody}</p></button></li>)}</ul><div className="min-w-0 rounded-lg border border-slate-gray/20 p-4">{selected && <><div className="flex flex-wrap items-start justify-between gap-3"><div><h4 className="font-heading font-semibold text-navy">{selected.threadTitle}</h4><p className="mt-1 text-xs text-slate-gray">Reported {formatDate(selected.createdAt)} · message from {selected.messageSenderDisplayName}</p></div><span className="text-xs font-semibold text-slate-gray">{selected.status.replace("_", " ")}</span></div><blockquote className="mt-4 rounded-lg bg-ice-white p-3 text-sm text-navy">{selected.messageBody}</blockquote>{selected.details && <p className="mt-3 text-sm text-slate-gray">Reporter context: {selected.details}</p>}{selected.threadSafetyLockedAt && <p role="status" className="mt-3 rounded-md border border-slate-gray/30 bg-ice-white p-2 text-sm font-semibold text-navy">Thread is safety-locked. {selected.threadSafetyLockReason}</p>}<label className="mt-4 block text-sm font-medium text-navy">Review / lock note<textarea value={note} onChange={(event) => setNote(event.target.value)} maxLength={2000} rows={3} className="mt-1 w-full rounded-md border border-slate-gray/30 px-3 py-2" /></label><div className="mt-3 flex flex-wrap gap-2">{selected.status === "OPEN" && <Button type="button" variant="secondary" disabled={review.isPending} onClick={() => void setReviewStatus("IN_REVIEW")}>Start review</Button>}<Button type="button" variant="secondary" disabled={lock.isPending || unlock.isPending} onClick={() => void toggleLock()}>{selected.threadSafetyLockedAt ? "Unlock thread" : "Safety-lock thread"}</Button>{selected.status !== "RESOLVED" && <Button type="button" variant="secondary" disabled={review.isPending || !note.trim()} onClick={() => void setReviewStatus("RESOLVED")}>Resolve</Button>}{selected.status !== "DISMISSED" && <Button type="button" variant="secondary" disabled={review.isPending || !note.trim()} onClick={() => void setReviewStatus("DISMISSED")}>Dismiss</Button>}</div>{notice && <p role="status" className="mt-3 text-sm text-slate-gray">{notice}</p>}<div className="mt-5 border-t border-slate-gray/20 pt-4"><p className="text-xs font-semibold uppercase tracking-wide text-slate-gray">Moderation history</p>{events.isLoading && <LoadingState label="Loading moderation history…" />}{events.data && <ul className="mt-2 grid gap-2">{events.data.map((event) => <li key={event.id} className="text-xs text-slate-gray"><span className="font-semibold text-navy">{event.eventType.replace("_", " ")}</span> · {event.actorDisplayName} · {formatDate(event.createdAt)}{event.note ? ` · ${event.note}` : ""}</li>)}</ul>}</div></>}</div></div>}
		</div>
	);
}
