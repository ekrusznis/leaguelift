import { useMemo, useState, type FormEvent } from "react";
import { Button } from "../../components/Button";
import { EmptyState } from "../../components/states/EmptyState";
import { ErrorState } from "../../components/states/ErrorState";
import { LoadingState } from "../../components/states/LoadingState";
import { featureFlags } from "../../lib/featureFlags";
import { useContexts } from "../../authorization/api";
import { Capabilities } from "../../authorization/capabilityConstants";
import { useArchiveAnnouncement, useCreateAnnouncement, useManagedAnnouncements, useMarkAnnouncementRead, useMyAnnouncements, usePublishAnnouncement, useUpdateAnnouncement } from "./api";
import type { AnnouncementAudience, AnnouncementStatus, CommunicationScope } from "./types";

const AUDIENCE_LABELS: Record<AnnouncementAudience, string> = {
	ALL: "Everyone in scope",
	STAFF: "Staff",
	GUARDIANS: "Guardians",
	ATHLETES: "Activated athletes",
};

const STATUS_LABELS: Record<AnnouncementStatus, string> = {
	DRAFT: "Draft",
	PUBLISHED: "Published",
	ARCHIVED: "Archived",
};

function managementScopes(contexts: ReturnType<typeof useContexts>["data"]): CommunicationScope[] {
	if (!contexts) return [];
	const scopes = contexts.flatMap((context): CommunicationScope[] => {
		if (!context.organizationId || !context.resourceId) return [];
		if (context.contextType === "ORGANIZATION" && context.capabilities.includes(Capabilities.ORG_COMMUNICATION_MANAGE)) {
			return [{ organizationId: context.organizationId, scopeType: "ORGANIZATION", scopeId: context.resourceId, label: context.label }];
		}
		if (context.contextType === "TEAM" && context.capabilities.includes(Capabilities.TEAM_COMMUNICATION_MANAGE)) {
			return [{ organizationId: context.organizationId, scopeType: "TEAM", scopeId: context.resourceId, label: context.label }];
		}
		if (context.contextType === "TOURNAMENT" && context.capabilities.includes(Capabilities.TOURNAMENT_COMMUNICATION_MANAGE)) {
			return [{ organizationId: context.organizationId, scopeType: "TOURNAMENT", scopeId: context.resourceId, label: context.label }];
		}
		return [];
	});
	return Array.from(new Map(scopes.map((scope) => [`${scope.scopeType}:${scope.scopeId}`, scope])).values());
}

function formatDate(value: string | null) {
	if (!value) return null;
	return new Intl.DateTimeFormat(undefined, { dateStyle: "medium", timeStyle: "short" }).format(new Date(value));
}

export function AnnouncementsPage() {
	const contexts = useContexts();
	const inbox = useMyAnnouncements();
	const markRead = useMarkAnnouncementRead();
	const scopes = useMemo(() => managementScopes(contexts.data), [contexts.data]);
	const [selectedScopeKey, setSelectedScopeKey] = useState("");
	const [managementStatus, setManagementStatus] = useState<AnnouncementStatus | "">("");
	const selectedScope = scopes.find((scope) => `${scope.scopeType}:${scope.scopeId}` === selectedScopeKey) ?? scopes[0];
	const managed = useManagedAnnouncements(selectedScope?.organizationId, selectedScope?.scopeType, selectedScope?.scopeId, managementStatus);
	const createAnnouncement = useCreateAnnouncement();
	const updateAnnouncement = useUpdateAnnouncement();
	const publishAnnouncement = usePublishAnnouncement();
	const archiveAnnouncement = useArchiveAnnouncement();
	const [editingId, setEditingId] = useState<string | null>(null);
	const [title, setTitle] = useState("");
	const [body, setBody] = useState("");
	const [audience, setAudience] = useState<AnnouncementAudience>("ALL");
	const [emailEnabled, setEmailEnabled] = useState(true);
	const [smsEnabled, setSmsEnabled] = useState(false);
	const [notice, setNotice] = useState<string | null>(null);

	const audienceOptions = selectedScope?.scopeType === "TOURNAMENT"
		? (["STAFF"] as AnnouncementAudience[])
		: (["ALL", "STAFF", "GUARDIANS", "ATHLETES"] as AnnouncementAudience[]);

	async function createDraft(event: FormEvent<HTMLFormElement>) {
		event.preventDefault();
		if (!selectedScope) return;
		setNotice(null);
		try {
			if (editingId) {
				await updateAnnouncement.mutateAsync({
					organizationId: selectedScope.organizationId, announcementId: editingId, title: title.trim(), body: body.trim(),
					audience, emailEnabled, smsEnabled,
				});
			} else {
				await createAnnouncement.mutateAsync({
					...selectedScope,
					idempotencyKey: crypto.randomUUID(),
					title: title.trim(),
					body: body.trim(),
					audience,
					emailEnabled,
					smsEnabled,
				});
			}
			setEditingId(null);
			setTitle("");
			setBody("");
			setAudience("ALL");
			setNotice(editingId ? "Announcement draft updated." : "Announcement draft created. Review it below before publishing.");
		} catch (error) {
			setNotice(error instanceof Error ? error.message : "The announcement draft could not be created.");
		}
	}

	return (
		<div className="flex flex-col gap-8">
			<header>
				<p className="text-sm font-semibold uppercase tracking-wide text-victory-green">One-way communications</p>
				<h1 className="mt-1 font-heading text-3xl font-bold text-navy dark:text-[#f8fafc]">Announcements</h1>
				<p className="mt-2 max-w-3xl text-slate-gray dark:text-[#cbd5e1]">
					Read messages sent to your account. Authorized staff can also prepare and publish organization, team, or tournament announcements. This is not a chat or reply thread.
				</p>
			</header>

			<section aria-labelledby="announcement-inbox-heading" className="rounded-xl border border-slate-gray/20 bg-pure-white dark:bg-[#111827] p-5">
				<div className="flex flex-wrap items-center justify-between gap-3">
					<div>
						<h2 id="announcement-inbox-heading" className="font-heading text-xl font-semibold text-navy dark:text-[#f8fafc]">Your inbox</h2>
						<p className="mt-1 text-sm text-slate-gray dark:text-[#cbd5e1]">Published messages addressed to your activated Rally26 account.</p>
					</div>
					{inbox.data && <span className="text-sm text-slate-gray dark:text-[#cbd5e1]">{inbox.data.totalElements} message{inbox.data.totalElements === 1 ? "" : "s"}</span>}
				</div>
				<div className="mt-4">
					{inbox.isLoading && <LoadingState label="Loading announcements…" />}
					{inbox.isError && <ErrorState message="Could not load announcements." onRetry={() => inbox.refetch()} />}
					{inbox.data?.items.length === 0 && <EmptyState title="No announcements" description="Published organization and team messages will appear here." />}
					{inbox.data && inbox.data.items.length > 0 && (
						<ul className="flex flex-col gap-3" aria-label="Announcement inbox">
							{inbox.data.items.map(({ announcement, readAt }) => (
								<li key={announcement.id} className={`rounded-lg border p-4 ${readAt ? "border-slate-gray/20 bg-ice-white dark:bg-[#0f172a]" : "border-victory-green/40 bg-victory-green/5"}`}>
									<div className="flex flex-wrap items-start justify-between gap-3">
										<div className="min-w-0 flex-1">
											<div className="flex flex-wrap items-center gap-2 text-xs text-slate-gray dark:text-[#cbd5e1]">
												{!readAt && <span className="rounded-full bg-victory-green px-2 py-1 font-semibold text-white">New</span>}
												<span>{announcement.scopeName ?? announcement.scopeType}</span>
												{announcement.publishedAt && <span>{formatDate(announcement.publishedAt)}</span>}
											</div>
											<h3 className="mt-2 font-heading text-lg font-semibold text-navy dark:text-[#f8fafc]">{announcement.title}</h3>
											<p className="mt-2 whitespace-pre-wrap text-sm text-slate-gray dark:text-[#cbd5e1]">{announcement.body}</p>
										</div>
										{!readAt && (
											<Button type="button" variant="secondary" disabled={markRead.isPending} onClick={() => markRead.mutate(announcement.id)}>
												Mark read
											</Button>
										)}
									</div>
								</li>
							))}
						</ul>
					)}
				</div>
			</section>

			{scopes.length > 0 && (
				<section aria-labelledby="announcement-management-heading" className="rounded-xl border border-slate-gray/20 bg-pure-white dark:bg-[#111827] p-5">
					<h2 id="announcement-management-heading" className="font-heading text-xl font-semibold text-navy dark:text-[#f8fafc]">Manage communications</h2>
					<p className="mt-1 text-sm text-slate-gray dark:text-[#cbd5e1]">Create a draft, review the exact content and delivery channels, then publish it once.</p>

					<div className="mt-4 max-w-xl">
						<label htmlFor="announcement-scope" className="text-sm font-medium text-navy dark:text-[#f8fafc]">Communication scope</label>
						<select
							id="announcement-scope"
							value={selectedScope ? `${selectedScope.scopeType}:${selectedScope.scopeId}` : ""}
							onChange={(event) => {
								setSelectedScopeKey(event.target.value);
								const scope = scopes.find((item) => `${item.scopeType}:${item.scopeId}` === event.target.value);
								if (scope?.scopeType === "TOURNAMENT" && audience !== "STAFF") setAudience("STAFF");
							}}
							className="mt-1 min-h-11 w-full rounded-md border border-slate-gray/30 px-3 py-2"
						>
							{scopes.map((scope) => <option key={`${scope.scopeType}:${scope.scopeId}`} value={`${scope.scopeType}:${scope.scopeId}`}>{scope.label} · {scope.scopeType.toLowerCase()}</option>)}
						</select>
						{selectedScope?.scopeType === "TOURNAMENT" && (
							<p className="mt-2 text-xs text-slate-gray dark:text-[#cbd5e1]">Tournament-wide guardian and athlete delivery is unavailable until Rally26 has a participating-team relationship model. Tournament announcements currently resolve staff recipients only.</p>
						)}
					</div>

					<form onSubmit={(event) => void createDraft(event)} className="mt-5 grid gap-4 rounded-lg bg-ice-white dark:bg-[#0f172a] p-4" noValidate>
						<div>
							<label htmlFor="announcement-title" className="text-sm font-medium text-navy dark:text-[#f8fafc]">Title</label>
							<input id="announcement-title" value={title} onChange={(event) => setTitle(event.target.value)} required maxLength={180} className="mt-1 min-h-11 w-full rounded-md border border-slate-gray/30 px-3 py-2" />
						</div>
						<div>
							<label htmlFor="announcement-body" className="text-sm font-medium text-navy dark:text-[#f8fafc]">Message</label>
							<textarea id="announcement-body" value={body} onChange={(event) => setBody(event.target.value)} required maxLength={5000} rows={5} className="mt-1 w-full rounded-md border border-slate-gray/30 px-3 py-2" />
						</div>
						<div className="grid gap-4 sm:grid-cols-2">
							<div>
								<label htmlFor="announcement-audience" className="text-sm font-medium text-navy dark:text-[#f8fafc]">Audience</label>
								<select id="announcement-audience" value={audience} onChange={(event) => setAudience(event.target.value as AnnouncementAudience)} className="mt-1 min-h-11 w-full rounded-md border border-slate-gray/30 px-3 py-2">
									{audienceOptions.map((value) => <option key={value} value={value}>{AUDIENCE_LABELS[value]}</option>)}
								</select>
							</div>
							<fieldset>
								<legend className="text-sm font-medium text-navy dark:text-[#f8fafc]">Delivery</legend>
								<label className="mt-2 flex items-center gap-2 text-sm text-slate-gray dark:text-[#cbd5e1]"><input type="checkbox" checked={emailEnabled} onChange={(event) => setEmailEnabled(event.target.checked)} /> Email</label>
								{featureFlags.smsNotifications && <label className="mt-2 flex items-center gap-2 text-sm text-slate-gray dark:text-[#cbd5e1]"><input type="checkbox" checked={smsEnabled} onChange={(event) => setSmsEnabled(event.target.checked)} /> SMS to opted-in households</label>}
							</fieldset>
						</div>
						<p className="text-xs text-slate-gray dark:text-[#cbd5e1]">Every resolved recipient receives an in-app copy. Household email opt-outs and SMS opt-ins are enforced when recipients are snapshotted at publication.</p>
						<div><Button type="submit" disabled={createAnnouncement.isPending || updateAnnouncement.isPending || !title.trim() || !body.trim()}>{createAnnouncement.isPending || updateAnnouncement.isPending ? "Saving…" : editingId ? "Save draft changes" : "Create draft"}</Button>{editingId && <Button type="button" variant="secondary" className="ml-2" onClick={() => { setEditingId(null); setTitle(""); setBody(""); setAudience("ALL"); }}>Cancel edit</Button>}</div>
						{notice && <p role="status" className="text-sm text-slate-gray dark:text-[#cbd5e1]">{notice}</p>}
					</form>

					<div className="mt-6 flex flex-wrap items-center justify-between gap-3">
						<h3 className="font-heading text-lg font-semibold text-navy dark:text-[#f8fafc]">Scope history</h3>
						<label className="flex items-center gap-2 text-sm text-slate-gray dark:text-[#cbd5e1]">Status
							<select value={managementStatus} onChange={(event) => setManagementStatus(event.target.value as AnnouncementStatus | "")} className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2">
								<option value="">All</option>
								<option value="DRAFT">Draft</option>
								<option value="PUBLISHED">Published</option>
								<option value="ARCHIVED">Archived</option>
							</select>
						</label>
					</div>
					<div className="mt-3">
						{managed.isLoading && <LoadingState label="Loading communication history…" />}
						{managed.isError && <ErrorState message="Could not load communication history." onRetry={() => managed.refetch()} />}
						{managed.data?.items.length === 0 && <EmptyState title="No communications" description="Drafts and published reminders for this scope will appear here." />}
						{managed.data && managed.data.items.length > 0 && (
							<ul className="flex flex-col gap-3" aria-label="Managed announcements">
								{managed.data.items.map((item) => (
									<li key={item.id} className="rounded-lg border border-slate-gray/20 p-4">
										<div className="flex flex-wrap items-start justify-between gap-3">
											<div className="min-w-0 flex-1">
												<div className="flex flex-wrap gap-2 text-xs text-slate-gray dark:text-[#cbd5e1]"><span>{STATUS_LABELS[item.status]}</span><span>{item.kind.replaceAll("_", " ")}</span><span>{AUDIENCE_LABELS[item.audience]}</span></div>
												<h4 className="mt-2 font-heading font-semibold text-navy dark:text-[#f8fafc]">{item.title}</h4>
												<p className="mt-1 whitespace-pre-wrap text-sm text-slate-gray dark:text-[#cbd5e1]">{item.body}</p>
												{item.status === "PUBLISHED" && (
											<p className="mt-2 text-xs text-slate-gray dark:text-[#cbd5e1]">
												{item.recipientCount} recipients · Email {item.emailSentCount} sent / {item.emailFailedCount} failed
												{featureFlags.smsNotifications && <> · SMS {item.smsSentCount} sent / {item.smsFailedCount} failed</>}
											</p>
										)}
											</div>
											<div className="flex shrink-0 flex-wrap gap-2">
												{item.status === "DRAFT" && <Button type="button" variant="secondary" onClick={() => { setEditingId(item.id); setTitle(item.title); setBody(item.body); setAudience(item.audience); setEmailEnabled(item.emailEnabled); setSmsEnabled(item.smsEnabled); window.scrollTo({ top: 0, behavior: "smooth" }); }}>Edit</Button>}
								{item.status === "DRAFT" && <Button type="button" disabled={publishAnnouncement.isPending} onClick={() => publishAnnouncement.mutate({ organizationId: item.organizationId, announcementId: item.id })}>Publish</Button>}
												{item.status !== "ARCHIVED" && <Button type="button" variant="secondary" disabled={archiveAnnouncement.isPending} onClick={() => archiveAnnouncement.mutate({ organizationId: item.organizationId, announcementId: item.id })}>Archive</Button>}
											</div>
										</div>
									</li>
								))}
							</ul>
						)}
					</div>
				</section>
			)}
		</div>
	);
}
