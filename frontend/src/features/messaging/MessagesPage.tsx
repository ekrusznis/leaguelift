import { useMemo, useState, type FormEvent } from "react";
import { Capabilities } from "../../authorization/capabilityConstants";
import { useContexts } from "../../authorization/api";
import { Button } from "../../components/Button";
import { EmptyState } from "../../components/states/EmptyState";
import { ErrorState } from "../../components/states/ErrorState";
import { LoadingState } from "../../components/states/LoadingState";
import { useArchiveMessageThread, useCreateMessageThread, useManagedMessageThreads, useManagedThreadMessages, useMarkMessageRead, useMyMessageThreads, useMyThreadMessages, useSendBroadcastMessage } from "./api";
import type { MessageAudience, MessageManagementScope, MessageThreadStatus } from "./types";

const AUDIENCE_LABELS: Record<MessageAudience, string> = {
	ALL: "Everyone in scope",
	STAFF: "Staff",
	GUARDIANS: "Guardians",
	ATHLETES: "Activated athletes",
};

function managementScopes(contexts: ReturnType<typeof useContexts>["data"]): MessageManagementScope[] {
	if (!contexts) return [];
	const scopes = contexts.flatMap((context): MessageManagementScope[] => {
		if (!context.organizationId || !context.resourceId) return [];
		if (context.contextType === "ORGANIZATION" && context.capabilities.includes(Capabilities.ORG_COMMUNICATION_MANAGE)) {
			return [{ organizationId: context.organizationId, scopeType: "ORGANIZATION", scopeId: context.resourceId, label: context.label }];
		}
		if (context.contextType === "TEAM" && context.capabilities.includes(Capabilities.TEAM_COMMUNICATION_MANAGE)) {
			return [{ organizationId: context.organizationId, scopeType: "TEAM", scopeId: context.resourceId, label: context.label }];
		}
		return [];
	});
	return Array.from(new Map(scopes.map((scope) => [`${scope.scopeType}:${scope.scopeId}`, scope])).values());
}

function formatDate(value: string) {
	return new Intl.DateTimeFormat(undefined, { dateStyle: "medium", timeStyle: "short" }).format(new Date(value));
}

export function MessagesPage() {
	const contexts = useContexts();
	const inbox = useMyMessageThreads();
	const scopes = useMemo(() => managementScopes(contexts.data), [contexts.data]);
	const [selectedInboxThreadId, setSelectedInboxThreadId] = useState<string | undefined>();
	const inboxThread = selectedInboxThreadId ?? inbox.data?.items[0]?.thread.id;
	const inboxMessages = useMyThreadMessages(inboxThread);
	const markRead = useMarkMessageRead();

	const [selectedScopeKey, setSelectedScopeKey] = useState("");
	const selectedScope = scopes.find((scope) => `${scope.scopeType}:${scope.scopeId}` === selectedScopeKey) ?? scopes[0];
	const [status, setStatus] = useState<MessageThreadStatus | "">("");
	const managed = useManagedMessageThreads(selectedScope?.organizationId, selectedScope?.scopeType, selectedScope?.scopeId, status);
	const [selectedManagedThreadId, setSelectedManagedThreadId] = useState<string | undefined>();
	const managedThreadId = selectedManagedThreadId ?? managed.data?.items[0]?.id;
	const managedMessages = useManagedThreadMessages(selectedScope?.organizationId, managedThreadId);
	const selectedManagedThread = managed.data?.items.find((thread) => thread.id === managedThreadId);

	const createThread = useCreateMessageThread();
	const sendMessage = useSendBroadcastMessage();
	const archiveThread = useArchiveMessageThread();
	const [title, setTitle] = useState("");
	const [audience, setAudience] = useState<MessageAudience>("ALL");
	const [emailEnabled, setEmailEnabled] = useState(true);
	const [smsEnabled, setSmsEnabled] = useState(false);
	const [messageBody, setMessageBody] = useState("");
	const [notice, setNotice] = useState<string | null>(null);

	async function create(event: FormEvent<HTMLFormElement>) {
		event.preventDefault();
		if (!selectedScope || !title.trim()) return;
		setNotice(null);
		try {
			const thread = await createThread.mutateAsync({
				...selectedScope,
				idempotencyKey: crypto.randomUUID(),
				title: title.trim(),
				audience,
				emailEnabled,
				smsEnabled,
			});
			setSelectedManagedThreadId(thread.id);
			setTitle("");
			setNotice("Broadcast thread created. Send the first update below when ready.");
		} catch (error) {
			setNotice(error instanceof Error ? error.message : "The broadcast thread could not be created.");
		}
	}

	async function send(event: FormEvent<HTMLFormElement>) {
		event.preventDefault();
		if (!selectedScope || !managedThreadId || !messageBody.trim()) return;
		setNotice(null);
		try {
			await sendMessage.mutateAsync({ organizationId: selectedScope.organizationId, threadId: managedThreadId, body: messageBody.trim() });
			setMessageBody("");
			setNotice("Message sent and recipient snapshot recorded.");
		} catch (error) {
			setNotice(error instanceof Error ? error.message : "The message could not be sent.");
		}
	}

	return (
		<div className="flex flex-col gap-8">
			<header>
				<p className="text-sm font-semibold uppercase tracking-wide text-victory-green">Phase 25 · Broadcast messaging</p>
				<h1 className="mt-1 font-heading text-3xl font-bold text-navy">Messages</h1>
				<p className="mt-2 max-w-3xl text-slate-gray">Organization and team broadcast threads keep related updates together. This first tier is one-way: authorized staff send; recipients read. Coach-to-family replies are a later Phase 25 slice.</p>
			</header>

			<section aria-labelledby="message-inbox-heading" className="rounded-xl border border-slate-gray/20 bg-pure-white p-5">
				<div className="flex flex-wrap items-center justify-between gap-3">
					<div><h2 id="message-inbox-heading" className="font-heading text-xl font-semibold text-navy">Your message threads</h2><p className="mt-1 text-sm text-slate-gray">Only messages whose immutable recipient snapshot includes your account appear here.</p></div>
					{inbox.data && <span className="text-sm text-slate-gray">{inbox.data.totalElements} thread{inbox.data.totalElements === 1 ? "" : "s"}</span>}
				</div>
				{inbox.isLoading && <LoadingState label="Loading message threads…" />}
				{inbox.isError && <ErrorState message="Could not load message threads." onRetry={() => inbox.refetch()} />}
				{inbox.data?.items.length === 0 && <EmptyState title="No messages" description="Organization and team broadcast threads sent to your account will appear here." />}
				{inbox.data && inbox.data.items.length > 0 && (
					<div className="mt-4 grid gap-4 lg:grid-cols-[18rem_minmax(0,1fr)]">
						<ul className="flex flex-col gap-2" aria-label="Message threads">
							{inbox.data.items.map((item) => (
								<li key={item.thread.id}><button type="button" onClick={() => setSelectedInboxThreadId(item.thread.id)} className={`w-full rounded-lg border p-3 text-left ${inboxThread === item.thread.id ? "border-victory-green bg-victory-green/5" : "border-slate-gray/20 bg-ice-white"}`}>
									<div className="flex items-start justify-between gap-2"><span className="font-semibold text-navy">{item.thread.title}</span>{item.unreadCount > 0 && <span className="rounded-full bg-victory-green px-2 py-0.5 text-xs font-semibold text-white">{item.unreadCount}</span>}</div>
									<p className="mt-1 text-xs text-slate-gray">{item.thread.scopeName ?? item.thread.scopeType} · {formatDate(item.lastMessageAt)}</p>
									<p className="mt-2 line-clamp-2 text-sm text-slate-gray">{item.lastMessagePreview}</p>
								</button></li>
							))}
						</ul>
						<div className="min-w-0 rounded-lg border border-slate-gray/20 p-4">
							{inboxMessages.isLoading && <LoadingState label="Loading thread messages…" />}
							{inboxMessages.isError && <ErrorState message="Could not load this thread." onRetry={() => inboxMessages.refetch()} />}
							{inboxMessages.data && <ul className="flex flex-col gap-3" aria-label="Messages in selected thread">{inboxMessages.data.items.map(({ message, readAt, accessReason }) => (
								<li key={message.id} className={`rounded-lg border p-4 ${readAt ? "border-slate-gray/20 bg-ice-white" : "border-victory-green/40 bg-victory-green/5"}`}>
									<div className="flex flex-wrap items-start justify-between gap-3"><div className="min-w-0 flex-1"><p className="text-xs text-slate-gray">{message.senderDisplayName} · {formatDate(message.sentAt)}</p><p className="mt-2 whitespace-pre-wrap text-sm text-navy">{message.body}</p>{accessReason === "GUARDIAN_VISIBILITY" && <p className="mt-2 text-xs text-slate-gray">Guardian visibility copy for an athlete-linked thread.</p>}</div>{!readAt && <Button type="button" variant="secondary" disabled={markRead.isPending} onClick={() => markRead.mutate(message.id)}>Mark read</Button>}</div>
								</li>
							))}</ul>}
						</div>
					</div>
				)}
			</section>

			{scopes.length > 0 && <section aria-labelledby="message-management-heading" className="rounded-xl border border-slate-gray/20 bg-pure-white p-5">
				<h2 id="message-management-heading" className="font-heading text-xl font-semibold text-navy">Manage broadcast threads</h2>
				<p className="mt-1 text-sm text-slate-gray">The audience and delivery channels are fixed for the thread. Each send re-resolves the current roster and snapshots that message's recipients.</p>
				<div className="mt-4 max-w-xl"><label htmlFor="message-scope" className="text-sm font-medium text-navy">Scope</label><select id="message-scope" value={selectedScope ? `${selectedScope.scopeType}:${selectedScope.scopeId}` : ""} onChange={(event) => { setSelectedScopeKey(event.target.value); setSelectedManagedThreadId(undefined); }} className="mt-1 min-h-11 w-full rounded-md border border-slate-gray/30 px-3 py-2">{scopes.map((scope) => <option key={`${scope.scopeType}:${scope.scopeId}`} value={`${scope.scopeType}:${scope.scopeId}`}>{scope.label} · {scope.scopeType.toLowerCase()}</option>)}</select></div>
				<form onSubmit={(event) => void create(event)} className="mt-5 grid gap-4 rounded-lg bg-ice-white p-4">
					<div><label htmlFor="thread-title" className="text-sm font-medium text-navy">New thread title</label><input id="thread-title" required maxLength={180} value={title} onChange={(event) => setTitle(event.target.value)} className="mt-1 min-h-11 w-full rounded-md border border-slate-gray/30 px-3 py-2" /></div>
					<div className="grid gap-4 sm:grid-cols-2"><div><label htmlFor="thread-audience" className="text-sm font-medium text-navy">Audience</label><select id="thread-audience" value={audience} onChange={(event) => setAudience(event.target.value as MessageAudience)} className="mt-1 min-h-11 w-full rounded-md border border-slate-gray/30 px-3 py-2">{Object.entries(AUDIENCE_LABELS).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></div><fieldset><legend className="text-sm font-medium text-navy">Delivery fallback</legend><label className="mt-2 flex items-center gap-2 text-sm text-slate-gray"><input type="checkbox" checked={emailEnabled} onChange={(event) => setEmailEnabled(event.target.checked)} /> Email</label><label className="mt-2 flex items-center gap-2 text-sm text-slate-gray"><input type="checkbox" checked={smsEnabled} onChange={(event) => setSmsEnabled(event.target.checked)} /> SMS to opted-in households</label></fieldset></div>
					<p className="text-xs text-slate-gray">Athlete-targeted broadcasts automatically give linked guardians an in-app read-only visibility copy; that transparency copy does not add guardian email/SMS delivery.</p>
					<div><Button type="submit" disabled={createThread.isPending || !selectedScope || !title.trim()}>{createThread.isPending ? "Creating…" : "Create broadcast thread"}</Button></div>
				</form>

				<div className="mt-6 flex flex-wrap items-center justify-between gap-3"><h3 className="font-heading text-lg font-semibold text-navy">Threads</h3><label className="flex items-center gap-2 text-sm text-slate-gray">Status<select value={status} onChange={(event) => setStatus(event.target.value as MessageThreadStatus | "")} className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2"><option value="">All</option><option value="OPEN">Open</option><option value="ARCHIVED">Archived</option></select></label></div>
				{managed.isLoading && <LoadingState label="Loading broadcast threads…" />}
				{managed.isError && <ErrorState message="Could not load broadcast threads." onRetry={() => managed.refetch()} />}
				{managed.data?.items.length === 0 && <EmptyState title="No broadcast threads" description="Create the first thread for this scope above." />}
				{managed.data && managed.data.items.length > 0 && <div className="mt-3 grid gap-4 lg:grid-cols-[18rem_minmax(0,1fr)]"><ul className="flex flex-col gap-2">{managed.data.items.map((thread) => <li key={thread.id}><button type="button" onClick={() => setSelectedManagedThreadId(thread.id)} className={`w-full rounded-lg border p-3 text-left ${managedThreadId === thread.id ? "border-victory-green bg-victory-green/5" : "border-slate-gray/20"}`}><div className="flex justify-between gap-2"><span className="font-semibold text-navy">{thread.title}</span><span className="text-xs text-slate-gray">{thread.status}</span></div><p className="mt-1 text-xs text-slate-gray">{AUDIENCE_LABELS[thread.audience]} · {thread.messageCount} messages</p></button></li>)}</ul>
					<div className="min-w-0 rounded-lg border border-slate-gray/20 p-4">{selectedManagedThread && <div className="flex flex-wrap items-start justify-between gap-3"><div><h4 className="font-heading font-semibold text-navy">{selectedManagedThread.title}</h4><p className="mt-1 text-xs text-slate-gray">{AUDIENCE_LABELS[selectedManagedThread.audience]} · {selectedManagedThread.recipientCount} unique snapshotted recipients</p></div>{selectedManagedThread.status === "OPEN" && <Button type="button" variant="secondary" disabled={archiveThread.isPending} onClick={() => archiveThread.mutate({ organizationId: selectedManagedThread.organizationId, threadId: selectedManagedThread.id })}>Archive thread</Button>}</div>}
						{managedMessages.isLoading && <LoadingState label="Loading sent messages…" />}{managedMessages.isError && <ErrorState message="Could not load sent messages." onRetry={() => managedMessages.refetch()} />}{managedMessages.data && <ul className="mt-4 flex flex-col gap-3">{managedMessages.data.items.map((message) => <li key={message.id} className="rounded-lg bg-ice-white p-3"><p className="text-xs text-slate-gray">{message.senderDisplayName} · {formatDate(message.sentAt)}</p><p className="mt-2 whitespace-pre-wrap text-sm text-navy">{message.body}</p><p className="mt-2 text-xs text-slate-gray">{message.recipientCount} recipients · Email {message.emailSentCount} sent / {message.emailFailedCount} failed · SMS {message.smsSentCount} sent / {message.smsFailedCount} failed</p></li>)}</ul>}
						{selectedManagedThread?.status === "OPEN" && <form onSubmit={(event) => void send(event)} className="mt-4 border-t border-slate-gray/20 pt-4"><label htmlFor="broadcast-message-body" className="text-sm font-medium text-navy">Send update</label><textarea id="broadcast-message-body" value={messageBody} onChange={(event) => setMessageBody(event.target.value)} required maxLength={5000} rows={5} className="mt-1 w-full rounded-md border border-slate-gray/30 px-3 py-2" /><div className="mt-3"><Button type="submit" disabled={sendMessage.isPending || !messageBody.trim()}>{sendMessage.isPending ? "Sending…" : "Send to current audience"}</Button></div></form>}
					</div></div>}
				{notice && <p role="status" className="mt-4 text-sm text-slate-gray">{notice}</p>}
			</section>}
		</div>
	);
}
