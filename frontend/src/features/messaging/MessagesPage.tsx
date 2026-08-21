import { useMemo, useState, type FormEvent } from "react";
import { Capabilities } from "../../authorization/capabilityConstants";
import { useContexts } from "../../authorization/api";
import { useAuth } from "../../auth/AuthContext";
import { Button } from "../../components/Button";
import { EmptyState } from "../../components/states/EmptyState";
import { ErrorState } from "../../components/states/ErrorState";
import { LoadingState } from "../../components/states/LoadingState";
import { featureFlags } from "../../lib/featureFlags";
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
import { dateSeparatorLabel, formatRelativeListTime, isDifferentDay, shouldGroupWithPrevious } from "./dateFormat";
import { ManagedMessageSafetyPanel, MyMessageReportsPanel, ReportMessageControl } from "./MessageSafetyPanel";
import { GuardianMessageSafetyControls } from "./SafeSportPolicyPanel";
import { AthleteMessagingComposer } from "./AthleteMessagingComposer";
import type { MessageAudience, MessageManagementScope, MessageThreadStatus, MessageThreadType, MyBroadcastMessage, MyMessageThread } from "./types";

const AUDIENCE_LABELS: Record<MessageAudience, string> = {
	ALL: "Everyone in scope",
	STAFF: "Staff",
	GUARDIANS: "Guardians",
	ATHLETES: "Activated athletes",
	SELECTED: "Selected conversation members",
};

const THREAD_TYPE_LABEL: Record<MessageThreadType, string> = {
	BROADCAST: "Broadcast",
	CONVERSATION: "Conversation",
	ATHLETE_CONVERSATION: "Athlete conversation",
};

type FilterKey = "ALL" | "UNREAD" | "TEAMS" | "DIRECT";
const FILTERS: { key: FilterKey; label: string }[] = [
	{ key: "ALL", label: "All" },
	{ key: "UNREAD", label: "Unread" },
	{ key: "TEAMS", label: "Teams" },
	{ key: "DIRECT", label: "Direct" },
];

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

/** A thread with more than two total recipients reads as a team/group; the closest "Direct" heuristic derivable without a new backend field. */
function isDirectThread(item: MyMessageThread): boolean {
	return item.thread.threadType !== "BROADCAST" && item.thread.recipientCount <= 2;
}

function matchesFilter(item: MyMessageThread, filter: FilterKey): boolean {
	switch (filter) {
		case "UNREAD":
			return item.unreadCount > 0;
		case "TEAMS":
			return !isDirectThread(item);
		case "DIRECT":
			return isDirectThread(item);
		default:
			return true;
	}
}

function initials(label: string): string {
	const words = label.trim().split(/\s+/).filter(Boolean);
	if (words.length === 0) return "?";
	if (words.length === 1) return words[0].slice(0, 2).toUpperCase();
	return (words[0][0] + words[1][0]).toUpperCase();
}

function ThreadAvatar({ item }: { item: MyMessageThread }) {
	if (item.thread.threadType === "BROADCAST") {
		return (
			<span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-championship-gold text-sm font-bold text-navy" aria-hidden>
				📣
			</span>
		);
	}
	return (
		<span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-navy text-sm font-bold text-white" aria-hidden>
			{initials(item.thread.scopeName ?? item.thread.title)}
		</span>
	);
}

/** Generalized "why can't I reply here" copy — branches on thread type / access reason / safety lock instead of assuming guardian-visibility is the only reason. */
function readOnlyReason(item: MyMessageThread): string | null {
	if (item.thread.threadType === "BROADCAST") return "Announcements only — replies aren't enabled for this broadcast.";
	if (item.canReply) return null;
	if (item.accessReason === "GUARDIAN_VISIBILITY") return "You have read-only guardian visibility for this conversation.";
	return "You have read-only access to this conversation.";
}

function MessageBubbleList({ messages, myDisplayName }: { messages: MyBroadcastMessage[]; myDisplayName: string | undefined }) {
	const rows = useMemo(() => {
		type Row = { kind: "separator"; label: string } | { kind: "message"; entry: MyBroadcastMessage; showSender: boolean };
		const out: Row[] = [];
		messages.forEach((entry, index) => {
			const previous = messages[index - 1];
			if (!previous || isDifferentDay(previous.message.sentAt, entry.message.sentAt)) {
				out.push({ kind: "separator", label: dateSeparatorLabel(entry.message.sentAt) });
			}
			const showSender = !shouldGroupWithPrevious(
				{ senderUserId: entry.message.senderUserId, sentAt: entry.message.sentAt },
				previous ? { senderUserId: previous.message.senderUserId, sentAt: previous.message.sentAt } : undefined,
			);
			out.push({ kind: "message", entry, showSender });
		});
		return out;
	}, [messages]);

	return (
		<ul className="flex flex-col gap-1" aria-label="Messages in selected thread">
			{rows.map((row, index) =>
				row.kind === "separator" ? (
					<li key={`sep-${index}`} className="my-2 flex items-center justify-center">
						<span className="rounded-full bg-slate-gray/10 px-3 py-1 text-xs font-semibold tracking-wide text-slate-gray dark:text-[#cbd5e1]">{row.label}</span>
					</li>
				) : (
					<MessageBubbleRow key={row.entry.message.id} entry={row.entry} showSender={row.showSender} mine={row.entry.message.senderDisplayName === myDisplayName} />
				),
			)}
		</ul>
	);
}

/** Sent (mine) vs. received bubbles use distinct colors, phone-messaging-style, so a glance at the thread shows who said what without reading every sender label. */
function ReadReceipt({ message }: { message: MyBroadcastMessage["message"] }) {
	if (message.recipientCount === 0) return null;
	const allRead = message.readRecipientCount >= message.recipientCount;
	return (
		<span className={`text-xs font-semibold ${allRead ? "text-success-700" : "text-slate-gray dark:text-[#cbd5e1]"}`} title={allRead ? "Read by all recipients" : `Sent · read by ${message.readRecipientCount} of ${message.recipientCount}`}>
			{allRead ? "✓✓" : "✓"}
		</span>
	);
}

function MessageBubbleRow({ entry, showSender, mine }: { entry: MyBroadcastMessage; showSender: boolean; mine: boolean }) {
	const { message, readAt, accessReason } = entry;
	const markRead = useMarkMessageRead(message.threadId);
	return (
		<li className={`flex flex-col ${mine ? "items-end" : "items-start"} ${showSender ? "mt-2" : "mt-0.5"}`}>
			{showSender && !mine && <span className="mb-1 px-1 text-xs text-slate-gray dark:text-[#cbd5e1]">{message.senderDisplayName}</span>}
			<div
				className={`max-w-[85%] rounded-2xl px-4 py-2 ${
					mine ? "rounded-br-sm bg-victory-green text-white" : `rounded-bl-sm ${readAt ? "bg-info-blue" : "bg-info-blue/70"} text-white`
				}`}
			>
				<p className="whitespace-pre-wrap text-sm">{message.body}</p>
			</div>
			<div className="mt-1 flex items-center gap-2 px-1">
				<span className="text-xs text-slate-gray dark:text-[#cbd5e1]">{formatRelativeListTime(message.sentAt)}</span>
				{mine && <ReadReceipt message={message} />}
				{accessReason === "GUARDIAN_VISIBILITY" && <span className="text-xs text-slate-gray dark:text-[#cbd5e1]">· guardian visibility</span>}
				{!mine && !readAt && (
					<button type="button" disabled={markRead.isPending} onClick={() => markRead.mutate(message.id)} className="text-xs font-semibold text-victory-green">
						Mark read
					</button>
				)}
				<ReportMessageControl messageId={message.id} />
			</div>
		</li>
	);
}

export function MessagesPage() {
	const contexts = useContexts();
	const { user } = useAuth();
	const scopes = useMemo(() => managementScopes(contexts.data), [contexts.data]);
	const teamScopes = useMemo(() => scopes.filter((scope) => scope.scopeType === "TEAM"), [scopes]);

	const inbox = useMyMessageThreads();
	const [selectedInboxThreadId, setSelectedInboxThreadId] = useState<string>();
	const [inboxQuery, setInboxQuery] = useState("");
	const [inboxFilter, setInboxFilter] = useState<FilterKey>("ALL");
	const [mobileDetailOpen, setMobileDetailOpen] = useState(false);
	const inboxItems = useMemo(() => inbox.data?.items ?? [], [inbox.data]);
	const filteredInboxItems = useMemo(() => {
		const q = inboxQuery.trim().toLowerCase();
		return inboxItems.filter((item) => matchesFilter(item, inboxFilter)).filter((item) => !q || item.thread.title.toLowerCase().includes(q) || (item.thread.scopeName ?? "").toLowerCase().includes(q));
	}, [inboxItems, inboxQuery, inboxFilter]);
	const noInboxResultsFromFilter = inboxItems.length > 0 && filteredInboxItems.length === 0;
	const inboxThreadId = selectedInboxThreadId ?? inbox.data?.items[0]?.thread.id;
	const selectedInboxItem = inbox.data?.items.find((item) => item.thread.id === inboxThreadId) ?? inbox.data?.items[0];
	const selectedInboxIsConversation = selectedInboxItem?.thread.threadType === "CONVERSATION" || selectedInboxItem?.thread.threadType === "ATHLETE_CONVERSATION";
	const inboxMessages = useMyThreadMessages(inboxThreadId);
	const inboxMembers = useMyThreadMembers(inboxThreadId, selectedInboxIsConversation);
	const reply = useReplyToConversation();
	const [replyBody, setReplyBody] = useState("");

	function selectInboxThread(threadId: string) {
		setSelectedInboxThreadId(threadId);
		setMobileDetailOpen(true);
	}

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
			selectInboxThread(thread.id);
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
				<h1 className="font-heading text-3xl font-bold text-navy dark:text-[#f8fafc]">Messages</h1>
				<p className="mt-2 max-w-3xl text-slate-gray dark:text-[#cbd5e1]">Broadcasts, coach/family conversations, and gated athlete peer messaging, with mandatory guardian visibility, message-level reporting, safety locks, and guardian communication controls.</p>
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
					<div className="mt-4 grid gap-4 lg:grid-cols-[20rem_minmax(0,1fr)]">
						<div className={`${mobileDetailOpen ? "hidden lg:flex" : "flex"} flex-col gap-3`}>
							<div className="flex items-center gap-2 rounded-lg border border-slate-gray/20 bg-ice-white dark:bg-[#0f172a] px-3 py-2">
								<span aria-hidden className="text-slate-gray dark:text-[#cbd5e1]">🔍</span>
								<input
									value={inboxQuery}
									onChange={(event) => setInboxQuery(event.target.value)}
									placeholder="Search conversations…"
									aria-label="Search conversations"
									className="w-full bg-transparent text-sm text-navy outline-none dark:text-[#f8fafc]"
								/>
							</div>
							<div className="flex flex-wrap gap-2">
								{FILTERS.map((f) => (
									<button
										key={f.key}
										type="button"
										onClick={() => setInboxFilter(f.key)}
										className={`rounded-full px-3 py-1 text-xs font-semibold ${inboxFilter === f.key ? "bg-victory-green text-white" : "bg-ice-white text-slate-gray dark:bg-[#0f172a] dark:text-[#cbd5e1]"}`}
									>
										{f.label}
									</button>
								))}
							</div>
							{noInboxResultsFromFilter && <EmptyState title="No matching conversations" description="Try a different search or filter." />}
							<ul className="flex flex-col gap-2" aria-label="Message threads">
								{filteredInboxItems.map((item) => (
									<li key={item.thread.id}>
										<button type="button" onClick={() => selectInboxThread(item.thread.id)} className={`flex w-full items-start gap-3 rounded-lg border p-3 text-left ${inboxThreadId === item.thread.id ? "border-victory-green bg-victory-green/5" : "border-slate-gray/20 bg-ice-white dark:bg-[#0f172a]"}`}>
											<ThreadAvatar item={item} />
											<div className="min-w-0 flex-1">
												<div className="flex items-start justify-between gap-2">
													<span className="truncate font-semibold text-navy dark:text-[#f8fafc]">{item.thread.title}</span>
													{item.lastMessageAt && <span className="shrink-0 text-xs text-slate-gray dark:text-[#cbd5e1]">{formatRelativeListTime(item.lastMessageAt)}</span>}
												</div>
												<p className="mt-1 line-clamp-1 text-sm text-slate-gray dark:text-[#cbd5e1]">{item.lastMessagePreview}</p>
												<div className="mt-1 flex items-center justify-between gap-2">
													<span className="text-xs text-slate-gray dark:text-[#cbd5e1]">{THREAD_TYPE_LABEL[item.thread.threadType]} · {item.thread.scopeName ?? item.thread.scopeType}</span>
													{item.unreadCount > 0 && <span className="rounded-full bg-victory-green px-2 py-0.5 text-xs font-semibold text-white">{item.unreadCount}</span>}
												</div>
											</div>
										</button>
									</li>
								))}
							</ul>
						</div>
						<div className={`min-w-0 flex-col rounded-lg border border-slate-gray/20 p-4 ${mobileDetailOpen ? "flex" : "hidden lg:flex"}`}>
							<button type="button" onClick={() => setMobileDetailOpen(false)} className="mb-3 self-start text-sm font-semibold text-victory-green lg:hidden">
								← Back to threads
							</button>
							{selectedInboxIsConversation && inboxMembers.data && <div className="mb-4 border-b border-slate-gray/20 pb-3"><p className="text-xs font-semibold uppercase tracking-wide text-slate-gray dark:text-[#cbd5e1]">Conversation members</p><div className="mt-2 flex flex-wrap gap-2">{inboxMembers.data.map((member) => <span key={member.userId} className="rounded-full bg-ice-white dark:bg-[#0f172a] px-3 py-1 text-xs text-navy dark:text-[#f8fafc]">{member.displayName}{member.accessReason === "GUARDIAN_VISIBILITY" ? " · guardian observer" : ""}</span>)}</div></div>}
							{inboxMessages.isLoading && <LoadingState label="Loading thread messages…" />}
							{inboxMessages.isError && <ErrorState message="Could not load this thread." onRetry={() => inboxMessages.refetch()} />}
							{inboxMessages.data && <MessageBubbleList messages={inboxMessages.data.items} myDisplayName={user?.displayName} />}
							{selectedInboxItem?.thread.safetyLockedAt && <p role="status" className="mt-4 rounded-md border border-slate-gray/30 bg-ice-white dark:bg-[#0f172a] p-3 text-sm font-semibold text-navy dark:text-[#f8fafc]">This thread is temporarily locked for safety review. New messages are paused.</p>}
							{selectedInboxItem && selectedInboxIsConversation && selectedInboxItem.thread.status === "OPEN" && !selectedInboxItem.thread.safetyLockedAt && selectedInboxItem.canReply && (
								<form onSubmit={(event) => void submitReply(event)} className="mt-4 border-t border-slate-gray/20 pt-4"><label htmlFor="conversation-reply" className="text-sm font-medium text-navy dark:text-[#f8fafc]">Reply</label><textarea id="conversation-reply" value={replyBody} onChange={(event) => setReplyBody(event.target.value)} required maxLength={5000} rows={3} className="mt-1 w-full rounded-md border border-slate-gray/30 px-3 py-2" /><div className="mt-3"><Button type="submit" disabled={reply.isPending || !replyBody.trim()}>{reply.isPending ? "Sending…" : "Send reply"}</Button></div></form>
							)}
							{selectedInboxItem && !selectedInboxItem.thread.safetyLockedAt && readOnlyReason(selectedInboxItem) && (selectedInboxItem.thread.threadType === "BROADCAST" || !selectedInboxItem.canReply) && (
								<p className="mt-4 border-t border-slate-gray/20 pt-4 text-sm text-slate-gray dark:text-[#cbd5e1]">{readOnlyReason(selectedInboxItem)}</p>
							)}
						</div>
					</div>
				)}
			</section>

			<MyMessageReportsPanel />
			<GuardianMessageSafetyControls />
			<AthleteMessagingComposer onCreated={(threadId) => selectInboxThread(threadId)} />

			{teamScopes.length > 0 && <section aria-labelledby="new-conversation-heading" className="rounded-xl border border-slate-gray/20 bg-pure-white dark:bg-[#111827] p-5">
				<h2 id="new-conversation-heading" className="font-heading text-xl font-semibold text-navy dark:text-[#f8fafc]">Start a family conversation</h2>
				<p className="mt-1 text-sm text-slate-gray dark:text-[#cbd5e1]">Choose active guardians or athletes connected to one team. Selected members can reply; guardians automatically observing an athlete are read-only unless selected too.</p>
				<form onSubmit={(event) => void submitConversation(event)} className="mt-4 grid gap-4">
					<div className="max-w-xl"><label htmlFor="conversation-team" className="text-sm font-medium text-navy dark:text-[#f8fafc]">Team</label><select id="conversation-team" value={conversationTeam ? `${conversationTeam.organizationId}:${conversationTeam.scopeId}` : ""} onChange={(event) => { setConversationTeamKey(event.target.value); setSelectedTargets(new Set()); }} className="mt-1 min-h-11 w-full rounded-md border border-slate-gray/30 px-3 py-2">{teamScopes.map((scope) => <option key={`${scope.organizationId}:${scope.scopeId}`} value={`${scope.organizationId}:${scope.scopeId}`}>{scope.label}</option>)}</select></div>
					<div className="grid gap-4 md:grid-cols-2"><div><label htmlFor="conversation-title" className="text-sm font-medium text-navy dark:text-[#f8fafc]">Conversation title</label><input id="conversation-title" value={conversationTitle} onChange={(event) => setConversationTitle(event.target.value)} required maxLength={180} className="mt-1 min-h-11 w-full rounded-md border border-slate-gray/30 px-3 py-2" placeholder="e.g. '26 9U Dragons" /></div><fieldset><legend className="text-sm font-medium text-navy dark:text-[#f8fafc]">Fallback delivery</legend><label className="mt-2 flex items-center gap-2 text-sm text-slate-gray dark:text-[#cbd5e1]"><input type="checkbox" checked={conversationEmail} onChange={(event) => setConversationEmail(event.target.checked)} /> Email</label>{featureFlags.smsNotifications && <label className="mt-2 flex items-center gap-2 text-sm text-slate-gray dark:text-[#cbd5e1]"><input type="checkbox" checked={conversationSms} onChange={(event) => setConversationSms(event.target.checked)} /> SMS to opted-in guardians</label>}</fieldset></div>
					{/* oxlint-disable-next-line jsx-a11y/label-has-associated-control -- the recipient label's text is nested two spans deep, which the linter's shallow text check misses; browsers compute the accessible name from all descendant text regardless of depth, so this is accessible as written */}
					<div><div className="flex flex-wrap items-center justify-between gap-2"><p className="text-sm font-medium text-navy dark:text-[#f8fafc]">Recipients</p>{contacts.data && contacts.data.length > 0 && <button type="button" className="text-sm font-semibold text-victory-green" onClick={() => setSelectedTargets(selectedTargets.size === contacts.data.length ? new Set() : new Set(contacts.data.map((contact) => contact.userId)))}>{selectedTargets.size === contacts.data.length ? "Clear all" : "Select all"}</button>}</div>{contacts.isLoading && <LoadingState label="Loading team contacts…" />}{contacts.isError && <ErrorState message="Could not load eligible team contacts." onRetry={() => contacts.refetch()} />}{contacts.data?.length === 0 && <EmptyState title="No activated family contacts" description="Invite guardians or eligible athletes before starting a two-way conversation." />}{contacts.data && contacts.data.length > 0 && <div className="mt-2 max-h-64 overflow-y-auto rounded-lg border border-slate-gray/20 p-2">{contacts.data.map((contact) => <label key={contact.userId} className="flex items-center gap-3 rounded-md px-2 py-2 hover:bg-ice-white hover:dark:bg-[#0f172a]"><input type="checkbox" checked={selectedTargets.has(contact.userId)} onChange={() => toggleTarget(contact.userId)} /><span className="min-w-0"><span className="block font-medium text-navy dark:text-[#f8fafc]">{contact.displayName}</span><span className="block text-xs text-slate-gray dark:text-[#cbd5e1]">{contact.recipientType === "GUARDIAN" ? "Guardian" : "Athlete"}</span></span></label>)}</div>}</div>
					<div><label htmlFor="conversation-first-message" className="text-sm font-medium text-navy dark:text-[#f8fafc]">First message</label><textarea id="conversation-first-message" value={conversationBody} onChange={(event) => setConversationBody(event.target.value)} required maxLength={5000} rows={5} className="mt-1 w-full rounded-md border border-slate-gray/30 px-3 py-2" /></div>
					<div><Button type="submit" disabled={createConversation.isPending || selectedTargets.size === 0 || !conversationTitle.trim() || !conversationBody.trim()}>{createConversation.isPending ? "Starting…" : "Start conversation"}</Button></div>
				</form>
			</section>}

			{scopes.length > 0 && <section aria-labelledby="message-management-heading" className="rounded-xl border border-slate-gray/20 bg-pure-white dark:bg-[#111827] p-5">
				<h2 id="message-management-heading" className="font-heading text-xl font-semibold text-navy dark:text-[#f8fafc]">Manage messaging</h2>
				<p className="mt-1 text-sm text-slate-gray dark:text-[#cbd5e1]">Every broadcast and conversation for the scopes you manage, including conversations between other members — visible here for safety oversight, not just the threads you personally sent. Create one-way broadcasts here and review/archive threads below.</p>
				<div className="mt-4 max-w-xl"><label htmlFor="message-scope" className="text-sm font-medium text-navy dark:text-[#f8fafc]">Scope</label><select id="message-scope" value={selectedScope ? `${selectedScope.scopeType}:${selectedScope.scopeId}` : ""} onChange={(event) => { setSelectedScopeKey(event.target.value); setSelectedManagedThreadId(undefined); }} className="mt-1 min-h-11 w-full rounded-md border border-slate-gray/30 px-3 py-2">{scopes.map((scope) => <option key={`${scope.scopeType}:${scope.scopeId}`} value={`${scope.scopeType}:${scope.scopeId}`}>{scope.label} · {scope.scopeType.toLowerCase()}</option>)}</select></div>
				<form onSubmit={(event) => void createBroadcastThread(event)} className="mt-5 grid gap-4 rounded-lg bg-ice-white dark:bg-[#0f172a] p-4">
					<div><label htmlFor="thread-title" className="text-sm font-medium text-navy dark:text-[#f8fafc]">New broadcast title</label><input id="thread-title" required minLength={3} maxLength={180} value={title} onChange={(event) => setTitle(event.target.value)} className="mt-1 min-h-11 w-full rounded-md border border-slate-gray/30 px-3 py-2" /></div>
					<div className="grid gap-4 md:grid-cols-2"><div><label htmlFor="thread-audience" className="text-sm font-medium text-navy dark:text-[#f8fafc]">Audience</label><select id="thread-audience" value={audience} onChange={(event) => setAudience(event.target.value as Exclude<MessageAudience, "SELECTED">)} className="mt-1 min-h-11 w-full rounded-md border border-slate-gray/30 px-3 py-2"><option value="ALL">Everyone in scope</option><option value="STAFF">Staff</option><option value="GUARDIANS">Guardians</option><option value="ATHLETES">Activated athletes</option></select></div><fieldset><legend className="text-sm font-medium text-navy dark:text-[#f8fafc]">Fallback delivery</legend><label className="mt-2 flex items-center gap-2 text-sm text-slate-gray dark:text-[#cbd5e1]"><input type="checkbox" checked={emailEnabled} onChange={(event) => setEmailEnabled(event.target.checked)} /> Email</label>{featureFlags.smsNotifications && <label className="mt-2 flex items-center gap-2 text-sm text-slate-gray dark:text-[#cbd5e1]"><input type="checkbox" checked={smsEnabled} onChange={(event) => setSmsEnabled(event.target.checked)} /> SMS to opted-in households</label>}</fieldset></div>
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
						{selectedManagedThread && selectedManagedThread.threadType !== "BROADCAST" && <p className="mt-4 border-t border-slate-gray/20 pt-4 text-sm text-slate-gray dark:text-[#cbd5e1]">Viewing for oversight — conversation replies are sent from each member's own inbox, not from here.</p>}
					</div></div>}
				<ManagedMessageSafetyPanel scope={selectedScope} />
				{notice && <p role="status" className="mt-4 text-sm text-slate-gray dark:text-[#cbd5e1]">{notice}</p>}
			</section>}
		</div>
	);
}
