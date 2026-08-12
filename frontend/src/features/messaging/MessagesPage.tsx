import { useMemo, useState, type FormEvent } from "react";
import { Capabilities } from "../../authorization/capabilityConstants";
import { useContexts } from "../../authorization/api";
import { Button } from "../../components/Button";
import { EmptyState } from "../../components/states/EmptyState";
import { ErrorState } from "../../components/states/ErrorState";
import { LoadingState } from "../../components/states/LoadingState";
import {
	useArchiveMessageThread,
	useConversationContacts,
	useCreateConversation,
	useCreateMessageThread,
	useManagedMessageThreads,
	useManagedThreadMessages,
	useMarkMessageRead,
	useMyMessageThreads,
	useMyThreadMembers,
	useMyThreadMessages,
	useReplyToConversation,
	useSendBroadcastMessage,
} from "./api";
import { ManagedMessageSafetyPanel, MyMessageReportsPanel, ReportMessageControl } from "./MessageSafetyPanel";
import { GuardianMessageSafetyControls } from "./SafeSportPolicyPanel";
import { AthleteMessagingComposer } from "./AthleteMessagingComposer";
import type { MessageAudience, MessageManagementScope, MessageThreadStatus } from "./types";

const AUDIENCE_LABELS: Record<MessageAudience, string> = {
	ALL: "Everyone in scope",
	STAFF: "Staff",
	GUARDIANS: "Guardians",
	ATHLETES: "Activated athletes",
	SELECTED: "Selected conversation members",
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
	const scopes = useMemo(() => managementScopes(contexts.data), [contexts.data]);
	const teamScopes = useMemo(() => scopes.filter((scope) => scope.scopeType === "TEAM"), [scopes]);

	const inbox = useMyMessageThreads();
	const [selectedInboxThreadId, setSelectedInboxThreadId] = useState<string>();
	const inboxThreadId = selectedInboxThreadId ?? inbox.data?.items[0]?.thread.id;
	const selectedInboxItem = inbox.data?.items.find((item) => item.thread.id === inboxThreadId) ?? inbox.data?.items[0];
	const selectedInboxIsConversation = selectedInboxItem?.thread.threadType === "CONVERSATION" || selectedInboxItem?.thread.threadType === "ATHLETE_CONVERSATION";
	const inboxMessages = useMyThreadMessages(inboxThreadId);
	const inboxMembers = useMyThreadMembers(inboxThreadId, selectedInboxIsConversation);
	const markRead = useMarkMessageRead();
	const reply = useReplyToConversation();
	const [replyBody, setReplyBody] = useState("");

	const [conversationTeamKey, setConversationTeamKey] = useState("");
	const conversationTeam = teamScopes.find((scope) => `${scope.organizationId}:${scope.scopeId}` === conversationTeamKey) ?? teamScopes[0];
	const contacts = useConversationContacts(conversationTeam?.organizationId, conversationTeam?.scopeId);
	const createConversation = useCreateConversation();
	const [conversationTitle, setConversationTitle] = useState("");
	const [conversationBody, setConversationBody] = useState("");
	const [conversationEmail, setConversationEmail] = useState(true);
	const [conversationSms, setConversationSms] = useState(false);
	const [selectedTargets, setSelectedTargets] = useState<Set<string>>(new Set());

	const [selectedScopeKey, setSelectedScopeKey] = useState("");
	const selectedScope = scopes.find((scope) => `${scope.scopeType}:${scope.scopeId}` === selectedScopeKey) ?? scopes[0];
	const [status, setStatus] = useState<MessageThreadStatus | "">("");
	const managed = useManagedMessageThreads(selectedScope?.organizationId, selectedScope?.scopeType, selectedScope?.scopeId, status);
	const [selectedManagedThreadId, setSelectedManagedThreadId] = useState<string>();
	const managedThreadId = selectedManagedThreadId ?? managed.data?.items[0]?.id;
	const managedMessages = useManagedThreadMessages(selectedScope?.organizationId, managedThreadId);
	const selectedManagedThread = managed.data?.items.find((thread) => thread.id === managedThreadId) ?? managed.data?.items[0];

	const createBroadcast = useCreateMessageThread();
	const sendBroadcast = useSendBroadcastMessage();
	const archiveThread = useArchiveMessageThread();
	const [title, setTitle] = useState("");
	const [audience, setAudience] = useState<Exclude<MessageAudience, "SELECTED">>("ALL");
	const [emailEnabled, setEmailEnabled] = useState(true);
	const [smsEnabled, setSmsEnabled] = useState(false);
	const [messageBody, setMessageBody] = useState("");
	const [notice, setNotice] = useState<string | null>(null);

	function toggleTarget(userId: string) {
		setSelectedTargets((current) => {
			const next = new Set(current);
			if (next.has(userId)) next.delete(userId);
			else next.add(userId);
			return next;
		});
	}

	async function submitReply(event: FormEvent<HTMLFormElement>) {
		event.preventDefault();
		if (!inboxThreadId || !replyBody.trim()) return;
		setNotice(null);
		try {
			await reply.mutateAsync({ threadId: inboxThreadId, body: replyBody.trim() });
			setReplyBody("");
			setNotice("Reply sent.");
		} catch (error) {
			setNotice(error instanceof Error ? error.message : "The reply could not be sent.");
		}
	}

	async function submitConversation(event: FormEvent<HTMLFormElement>) {
		event.preventDefault();
		if (!conversationTeam || !conversationTitle.trim() || !conversationBody.trim() || selectedTargets.size === 0) return;
		setNotice(null);
		try {
			const thread = await createConversation.mutateAsync({
				organizationId: conversationTeam.organizationId,
				teamId: conversationTeam.scopeId,
				title: conversationTitle.trim(),
				targetUserIds: Array.from(selectedTargets),
				emailEnabled: conversationEmail,
				smsEnabled: conversationSms,
				initialMessage: conversationBody.trim(),
			});
			setConversationTitle("");
			setConversationBody("");
			setSelectedTargets(new Set());
			setSelectedInboxThreadId(thread.id);
			setNotice("Conversation created and first message sent.");
		} catch (error) {
			setNotice(error instanceof Error ? error.message : "The conversation could not be created.");
		}
	}

	async function createBroadcastThread(event: FormEvent<HTMLFormElement>) {
		event.preventDefault();
		if (!selectedScope || !title.trim()) return;
		setNotice(null);
		try {
			const thread = await createBroadcast.mutateAsync({
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

	async function sendBroadcastUpdate(event: FormEvent<HTMLFormElement>) {
		event.preventDefault();
		if (!selectedScope || !managedThreadId || !messageBody.trim()) return;
		setNotice(null);
		try {
			await sendBroadcast.mutateAsync({ organizationId: selectedScope.organizationId, threadId: managedThreadId, body: messageBody.trim() });
			setMessageBody("");
			setNotice("Broadcast sent and recipient snapshot recorded.");
		} catch (error) {
			setNotice(error instanceof Error ? error.message : "The broadcast could not be sent.");
		}
	}

	return (
		<div className="flex flex-col gap-8">
			<header>
				<p className="text-sm font-semibold uppercase tracking-wide text-victory-green">Phase 25 · Messaging</p>
				<h1 className="mt-1 font-heading text-3xl font-bold text-navy dark:text-[#f8fafc]">Messages</h1>
				<p className="mt-2 max-w-3xl text-slate-gray dark:text-[#cbd5e1]">Phase 25 messaging includes one-way broadcasts, coach/family conversations, gated athlete peer messaging, mandatory guardian visibility, message-level reporting, retained moderation history, safety locks, and guardian communication controls.</p>
			</header>

			<section aria-labelledby="message-inbox-heading" className="rounded-xl border border-slate-gray/20 bg-pure-white dark:bg-[#111827] p-5">
				<div className="flex flex-wrap items-center justify-between gap-3">
					<div><h2 id="message-inbox-heading" className="font-heading text-xl font-semibold text-navy dark:text-[#f8fafc]">Your message threads</h2><p className="mt-1 text-sm text-slate-gray dark:text-[#cbd5e1]">Broadcasts and conversations visible to your account appear here.</p></div>
					{inbox.data && <span className="text-sm text-slate-gray dark:text-[#cbd5e1]">{inbox.data.totalElements} thread{inbox.data.totalElements === 1 ? "" : "s"}</span>}
				</div>
				{inbox.isLoading && <LoadingState label="Loading message threads…" />}
				{inbox.isError && <ErrorState message="Could not load message threads." onRetry={() => inbox.refetch()} />}
				{inbox.data?.items.length === 0 && <EmptyState title="No messages" description="Organization/team broadcasts and family conversations will appear here." />}
				{inbox.data && inbox.data.items.length > 0 && (
					<div className="mt-4 grid gap-4 lg:grid-cols-[18rem_minmax(0,1fr)]">
						<ul className="flex flex-col gap-2" aria-label="Message threads">
							{inbox.data.items.map((item) => (
								<li key={item.thread.id}><button type="button" onClick={() => setSelectedInboxThreadId(item.thread.id)} className={`w-full rounded-lg border p-3 text-left ${inboxThreadId === item.thread.id ? "border-victory-green bg-victory-green/5" : "border-slate-gray/20 bg-ice-white dark:bg-[#0f172a]"}`}>
									<div className="flex items-start justify-between gap-2"><span className="font-semibold text-navy dark:text-[#f8fafc]">{item.thread.title}</span>{item.unreadCount > 0 && <span className="rounded-full bg-victory-green px-2 py-0.5 text-xs font-semibold text-white">{item.unreadCount}</span>}</div>
									<p className="mt-1 text-xs text-slate-gray dark:text-[#cbd5e1]">{item.thread.threadType === "ATHLETE_CONVERSATION" ? "Athlete conversation" : item.thread.threadType === "CONVERSATION" ? "Conversation" : "Broadcast"} · {item.thread.scopeName ?? item.thread.scopeType} · {formatDate(item.lastMessageAt)}</p>
									<p className="mt-2 line-clamp-2 text-sm text-slate-gray dark:text-[#cbd5e1]">{item.lastMessagePreview}</p>
								</button></li>
							))}
						</ul>
						<div className="min-w-0 rounded-lg border border-slate-gray/20 p-4">
							{selectedInboxIsConversation && inboxMembers.data && <div className="mb-4 border-b border-slate-gray/20 pb-3"><p className="text-xs font-semibold uppercase tracking-wide text-slate-gray dark:text-[#cbd5e1]">Conversation members</p><div className="mt-2 flex flex-wrap gap-2">{inboxMembers.data.map((member) => <span key={member.userId} className="rounded-full bg-ice-white dark:bg-[#0f172a] px-3 py-1 text-xs text-navy dark:text-[#f8fafc]">{member.displayName}{member.accessReason === "GUARDIAN_VISIBILITY" ? " · guardian observer" : ""}</span>)}</div></div>}
							{inboxMessages.isLoading && <LoadingState label="Loading thread messages…" />}
							{inboxMessages.isError && <ErrorState message="Could not load this thread." onRetry={() => inboxMessages.refetch()} />}
							{inboxMessages.data && <ul className="flex flex-col gap-3" aria-label="Messages in selected thread">{inboxMessages.data.items.map(({ message, readAt, accessReason }) => (
								<li key={message.id} className={`rounded-lg border p-4 ${readAt ? "border-slate-gray/20 bg-ice-white dark:bg-[#0f172a]" : "border-victory-green/40 bg-victory-green/5"}`}>
									<div className="flex flex-wrap items-start justify-between gap-3"><div className="min-w-0 flex-1"><p className="text-xs text-slate-gray dark:text-[#cbd5e1]">{message.senderDisplayName} · {formatDate(message.sentAt)}</p><p className="mt-2 whitespace-pre-wrap text-sm text-navy dark:text-[#f8fafc]">{message.body}</p>{accessReason === "GUARDIAN_VISIBILITY" && <p className="mt-2 text-xs text-slate-gray dark:text-[#cbd5e1]">Guardian visibility access · read-only unless you were also explicitly selected.</p>}<ReportMessageControl messageId={message.id} /></div>{!readAt && <Button type="button" variant="secondary" disabled={markRead.isPending} onClick={() => markRead.mutate(message.id)}>Mark read</Button>}</div>
								</li>
							))}</ul>}
							{selectedInboxItem?.thread.safetyLockedAt && <p role="status" className="mt-4 rounded-md border border-slate-gray/30 bg-ice-white dark:bg-[#0f172a] p-3 text-sm font-semibold text-navy dark:text-[#f8fafc]">This thread is temporarily locked for safety review. New messages are paused.</p>}
							{selectedInboxItem && selectedInboxIsConversation && selectedInboxItem.thread.status === "OPEN" && !selectedInboxItem.thread.safetyLockedAt && selectedInboxItem.canReply && (
								<form onSubmit={(event) => void submitReply(event)} className="mt-4 border-t border-slate-gray/20 pt-4"><label htmlFor="conversation-reply" className="text-sm font-medium text-navy dark:text-[#f8fafc]">Reply</label><textarea id="conversation-reply" value={replyBody} onChange={(event) => setReplyBody(event.target.value)} required maxLength={5000} rows={4} className="mt-1 w-full rounded-md border border-slate-gray/30 px-3 py-2" /><div className="mt-3"><Button type="submit" disabled={reply.isPending || !replyBody.trim()}>{reply.isPending ? "Sending…" : "Send reply"}</Button></div></form>
							)}
							{selectedInboxItem && selectedInboxIsConversation && !selectedInboxItem.canReply && <p className="mt-4 border-t border-slate-gray/20 pt-4 text-sm text-slate-gray dark:text-[#cbd5e1]">You have read-only guardian visibility for this conversation.</p>}
						</div>
					</div>
				)}
			</section>

			<MyMessageReportsPanel />
			<GuardianMessageSafetyControls />
			<AthleteMessagingComposer onCreated={(threadId) => setSelectedInboxThreadId(threadId)} />

			{teamScopes.length > 0 && <section aria-labelledby="new-conversation-heading" className="rounded-xl border border-slate-gray/20 bg-pure-white dark:bg-[#111827] p-5">
				<h2 id="new-conversation-heading" className="font-heading text-xl font-semibold text-navy dark:text-[#f8fafc]">Start a family conversation</h2>
				<p className="mt-1 text-sm text-slate-gray dark:text-[#cbd5e1]">Choose active guardians or athletes connected to one team. Selected members can reply; guardians automatically observing an athlete are read-only unless selected too.</p>
				<form onSubmit={(event) => void submitConversation(event)} className="mt-4 grid gap-4">
					<div className="max-w-xl"><label htmlFor="conversation-team" className="text-sm font-medium text-navy dark:text-[#f8fafc]">Team</label><select id="conversation-team" value={conversationTeam ? `${conversationTeam.organizationId}:${conversationTeam.scopeId}` : ""} onChange={(event) => { setConversationTeamKey(event.target.value); setSelectedTargets(new Set()); }} className="mt-1 min-h-11 w-full rounded-md border border-slate-gray/30 px-3 py-2">{teamScopes.map((scope) => <option key={`${scope.organizationId}:${scope.scopeId}`} value={`${scope.organizationId}:${scope.scopeId}`}>{scope.label}</option>)}</select></div>
					<div className="grid gap-4 md:grid-cols-2"><div><label htmlFor="conversation-title" className="text-sm font-medium text-navy dark:text-[#f8fafc]">Conversation title</label><input id="conversation-title" value={conversationTitle} onChange={(event) => setConversationTitle(event.target.value)} required maxLength={180} className="mt-1 min-h-11 w-full rounded-md border border-slate-gray/30 px-3 py-2" /></div><fieldset><legend className="text-sm font-medium text-navy dark:text-[#f8fafc]">Fallback delivery</legend><label className="mt-2 flex items-center gap-2 text-sm text-slate-gray dark:text-[#cbd5e1]"><input type="checkbox" checked={conversationEmail} onChange={(event) => setConversationEmail(event.target.checked)} /> Email</label><label className="mt-2 flex items-center gap-2 text-sm text-slate-gray dark:text-[#cbd5e1]"><input type="checkbox" checked={conversationSms} onChange={(event) => setConversationSms(event.target.checked)} /> SMS to opted-in guardians</label></fieldset></div>
					<div><div className="flex flex-wrap items-center justify-between gap-2"><p className="text-sm font-medium text-navy dark:text-[#f8fafc]">Recipients</p>{contacts.data && contacts.data.length > 0 && <button type="button" className="text-sm font-semibold text-victory-green" onClick={() => setSelectedTargets(selectedTargets.size === contacts.data.length ? new Set() : new Set(contacts.data.map((contact) => contact.userId)))}>{selectedTargets.size === contacts.data.length ? "Clear all" : "Select all"}</button>}</div>{contacts.isLoading && <LoadingState label="Loading team contacts…" />}{contacts.isError && <ErrorState message="Could not load eligible team contacts." onRetry={() => contacts.refetch()} />}{contacts.data?.length === 0 && <EmptyState title="No activated family contacts" description="Invite guardians or eligible athletes before starting a two-way conversation." />}{contacts.data && contacts.data.length > 0 && <div className="mt-2 max-h-64 overflow-y-auto rounded-lg border border-slate-gray/20 p-2">{contacts.data.map((contact) => <label key={contact.userId} className="flex items-center gap-3 rounded-md px-2 py-2 hover:bg-ice-white hover:dark:bg-[#0f172a]"><input type="checkbox" checked={selectedTargets.has(contact.userId)} onChange={() => toggleTarget(contact.userId)} /><span className="min-w-0"><span className="block font-medium text-navy dark:text-[#f8fafc]">{contact.displayName}</span><span className="block text-xs text-slate-gray dark:text-[#cbd5e1]">{contact.recipientType === "GUARDIAN" ? "Guardian" : "Athlete"}</span></span></label>)}</div>}</div>
					<div><label htmlFor="conversation-first-message" className="text-sm font-medium text-navy dark:text-[#f8fafc]">First message</label><textarea id="conversation-first-message" value={conversationBody} onChange={(event) => setConversationBody(event.target.value)} required maxLength={5000} rows={5} className="mt-1 w-full rounded-md border border-slate-gray/30 px-3 py-2" /></div>
					<div><Button type="submit" disabled={createConversation.isPending || selectedTargets.size === 0 || !conversationTitle.trim() || !conversationBody.trim()}>{createConversation.isPending ? "Starting…" : "Start conversation"}</Button></div>
				</form>
			</section>}

			{scopes.length > 0 && <section aria-labelledby="message-management-heading" className="rounded-xl border border-slate-gray/20 bg-pure-white dark:bg-[#111827] p-5">
				<h2 id="message-management-heading" className="font-heading text-xl font-semibold text-navy dark:text-[#f8fafc]">Manage messaging</h2>
				<p className="mt-1 text-sm text-slate-gray dark:text-[#cbd5e1]">Create one-way broadcasts here and review/archive both broadcast and conversation threads for the scopes you manage.</p>
				<div className="mt-4 max-w-xl"><label htmlFor="message-scope" className="text-sm font-medium text-navy dark:text-[#f8fafc]">Scope</label><select id="message-scope" value={selectedScope ? `${selectedScope.scopeType}:${selectedScope.scopeId}` : ""} onChange={(event) => { setSelectedScopeKey(event.target.value); setSelectedManagedThreadId(undefined); }} className="mt-1 min-h-11 w-full rounded-md border border-slate-gray/30 px-3 py-2">{scopes.map((scope) => <option key={`${scope.scopeType}:${scope.scopeId}`} value={`${scope.scopeType}:${scope.scopeId}`}>{scope.label} · {scope.scopeType.toLowerCase()}</option>)}</select></div>
				<form onSubmit={(event) => void createBroadcastThread(event)} className="mt-5 grid gap-4 rounded-lg bg-ice-white dark:bg-[#0f172a] p-4">
					<div><label htmlFor="thread-title" className="text-sm font-medium text-navy dark:text-[#f8fafc]">New broadcast title</label><input id="thread-title" required minLength={3} maxLength={180} value={title} onChange={(event) => setTitle(event.target.value)} className="mt-1 min-h-11 w-full rounded-md border border-slate-gray/30 px-3 py-2" /></div>
					<div className="grid gap-4 md:grid-cols-2"><div><label htmlFor="thread-audience" className="text-sm font-medium text-navy dark:text-[#f8fafc]">Audience</label><select id="thread-audience" value={audience} onChange={(event) => setAudience(event.target.value as Exclude<MessageAudience, "SELECTED">)} className="mt-1 min-h-11 w-full rounded-md border border-slate-gray/30 px-3 py-2"><option value="ALL">Everyone in scope</option><option value="STAFF">Staff</option><option value="GUARDIANS">Guardians</option><option value="ATHLETES">Activated athletes</option></select></div><fieldset><legend className="text-sm font-medium text-navy dark:text-[#f8fafc]">Fallback delivery</legend><label className="mt-2 flex items-center gap-2 text-sm text-slate-gray dark:text-[#cbd5e1]"><input type="checkbox" checked={emailEnabled} onChange={(event) => setEmailEnabled(event.target.checked)} /> Email</label><label className="mt-2 flex items-center gap-2 text-sm text-slate-gray dark:text-[#cbd5e1]"><input type="checkbox" checked={smsEnabled} onChange={(event) => setSmsEnabled(event.target.checked)} /> SMS to opted-in households</label></fieldset></div>
					<div><Button type="submit" disabled={createBroadcast.isPending || !selectedScope || !title.trim()}>{createBroadcast.isPending ? "Creating…" : "Create broadcast thread"}</Button></div>
				</form>

				<div className="mt-6 flex flex-wrap items-center justify-between gap-3"><h3 className="font-heading text-lg font-semibold text-navy dark:text-[#f8fafc]">Threads</h3><label className="flex items-center gap-2 text-sm text-slate-gray dark:text-[#cbd5e1]">Status<select value={status} onChange={(event) => setStatus(event.target.value as MessageThreadStatus | "")} className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2"><option value="">All</option><option value="OPEN">Open</option><option value="ARCHIVED">Archived</option></select></label></div>
				{managed.isLoading && <LoadingState label="Loading message threads…" />}
				{managed.isError && <ErrorState message="Could not load message threads." onRetry={() => managed.refetch()} />}
				{managed.data?.items.length === 0 && <EmptyState title="No message threads" description="Create a broadcast or family conversation for this scope." />}
				{managed.data && managed.data.items.length > 0 && <div className="mt-3 grid gap-4 lg:grid-cols-[18rem_minmax(0,1fr)]"><ul className="flex flex-col gap-2">{managed.data.items.map((thread) => <li key={thread.id}><button type="button" onClick={() => setSelectedManagedThreadId(thread.id)} className={`w-full rounded-lg border p-3 text-left ${managedThreadId === thread.id ? "border-victory-green bg-victory-green/5" : "border-slate-gray/20"}`}><div className="flex justify-between gap-2"><span className="font-semibold text-navy dark:text-[#f8fafc]">{thread.title}</span><span className="text-xs text-slate-gray dark:text-[#cbd5e1]">{thread.status}</span></div><p className="mt-1 text-xs text-slate-gray dark:text-[#cbd5e1]">{thread.threadType === "ATHLETE_CONVERSATION" ? "Athlete conversation" : thread.threadType === "CONVERSATION" ? "Conversation" : AUDIENCE_LABELS[thread.audience]} · {thread.messageCount} messages</p></button></li>)}</ul>
					<div className="min-w-0 rounded-lg border border-slate-gray/20 p-4">{selectedManagedThread && <div className="flex flex-wrap items-start justify-between gap-3"><div><h4 className="font-heading font-semibold text-navy dark:text-[#f8fafc]">{selectedManagedThread.title}</h4><p className="mt-1 text-xs text-slate-gray dark:text-[#cbd5e1]">{selectedManagedThread.threadType === "ATHLETE_CONVERSATION" ? `${selectedManagedThread.recipientCount} athlete/guardian members` : selectedManagedThread.threadType === "CONVERSATION" ? `${selectedManagedThread.recipientCount} conversation members` : `${AUDIENCE_LABELS[selectedManagedThread.audience]} · ${selectedManagedThread.recipientCount} unique snapshotted recipients`}</p>{selectedManagedThread.safetyLockedAt && <p className="mt-1 text-xs font-semibold text-navy dark:text-[#f8fafc]">Safety-locked · new messages paused</p>}</div>{selectedManagedThread.status === "OPEN" && <Button type="button" variant="secondary" disabled={archiveThread.isPending} onClick={() => archiveThread.mutate({ organizationId: selectedManagedThread.organizationId, threadId: selectedManagedThread.id })}>Archive thread</Button>}</div>}
						{managedMessages.isLoading && <LoadingState label="Loading sent messages…" />}{managedMessages.isError && <ErrorState message="Could not load sent messages." onRetry={() => managedMessages.refetch()} />}{managedMessages.data && <ul className="mt-4 flex flex-col gap-3">{managedMessages.data.items.map((message) => <li key={message.id} className="rounded-lg bg-ice-white dark:bg-[#0f172a] p-3"><p className="text-xs text-slate-gray dark:text-[#cbd5e1]">{message.senderDisplayName} · {formatDate(message.sentAt)}</p><p className="mt-2 whitespace-pre-wrap text-sm text-navy dark:text-[#f8fafc]">{message.body}</p><p className="mt-2 text-xs text-slate-gray dark:text-[#cbd5e1]">{message.recipientCount} recipients · Email {message.emailSentCount} sent / {message.emailFailedCount} failed · SMS {message.smsSentCount} sent / {message.smsFailedCount} failed</p></li>)}</ul>}
						{selectedManagedThread?.threadType === "BROADCAST" && selectedManagedThread.status === "OPEN" && !selectedManagedThread.safetyLockedAt && <form onSubmit={(event) => void sendBroadcastUpdate(event)} className="mt-4 border-t border-slate-gray/20 pt-4"><label htmlFor="broadcast-message-body" className="text-sm font-medium text-navy dark:text-[#f8fafc]">Send update</label><textarea id="broadcast-message-body" value={messageBody} onChange={(event) => setMessageBody(event.target.value)} required maxLength={5000} rows={5} className="mt-1 w-full rounded-md border border-slate-gray/30 px-3 py-2" /><div className="mt-3"><Button type="submit" disabled={sendBroadcast.isPending || !messageBody.trim()}>{sendBroadcast.isPending ? "Sending…" : "Send to current audience"}</Button></div></form>}
						{selectedManagedThread && selectedManagedThread.threadType !== "BROADCAST" && <p className="mt-4 border-t border-slate-gray/20 pt-4 text-sm text-slate-gray dark:text-[#cbd5e1]">Conversation replies are sent from each member's inbox. Managers can review and archive the thread here.</p>}
					</div></div>}
				<ManagedMessageSafetyPanel scope={selectedScope} />
				{notice && <p role="status" className="mt-4 text-sm text-slate-gray dark:text-[#cbd5e1]">{notice}</p>}
			</section>}
		</div>
	);
}
