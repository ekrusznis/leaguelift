import { useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { ErrorState } from "../../components/states/ErrorState";
import { LoadingState } from "../../components/states/LoadingState";
import { usePlatformAdminUsers } from "../platformAdmin/api";
import { appPaths } from "../../routes/appPaths";
import type { SupportCase, SupportCasePriority, SupportCaseStatus } from "./types";
import { usePlatformSupportCases, useSendSupportCaseEmail, useUpdateSupportCase } from "./api";

const STATUSES: SupportCaseStatus[] = ["OPEN", "IN_PROGRESS", "WAITING_ON_CUSTOMER", "RESOLVED", "CLOSED"];
const PRIORITIES: SupportCasePriority[] = ["LOW", "NORMAL", "HIGH", "URGENT"];

export function PlatformSupportCasesPage() {
	const [query, setQuery] = useState("");
	const [status, setStatus] = useState<SupportCaseStatus | "">("");
	const cases = usePlatformSupportCases({ query, status, size: 100 });
	const users = usePlatformAdminUsers({ page: 0, size: 100 });
	const platformAdmins = useMemo(() => users.data?.items.filter((user) => user.platformAdmin) ?? [], [users.data]);
	const update = useUpdateSupportCase();
	const [drafts, setDrafts] = useState<Record<string, { status: SupportCaseStatus; priority: SupportCasePriority; assignedPlatformUserId: string; resolution: string }>>({});
	function draft(item: SupportCase) { return drafts[item.id] ?? { status: item.status, priority: item.priority, assignedPlatformUserId: item.assignedPlatformUserId ?? "", resolution: item.resolution ?? "" }; }
	function patch(item: SupportCase, values: Partial<ReturnType<typeof draft>>) { setDrafts((current) => ({ ...current, [item.id]: { ...draft(item), ...values } })); }

	// One-way ad-hoc email to the requester, independent of a status change -- no reply
	// thread, matching the "no chat threads" boundary this feature otherwise keeps.
	const sendEmail = useSendSupportCaseEmail();
	const [openEmailCaseId, setOpenEmailCaseId] = useState<string | null>(null);
	const [emailDrafts, setEmailDrafts] = useState<Record<string, { subject: string; body: string }>>({});
	const [sentCaseIds, setSentCaseIds] = useState<Record<string, boolean>>({});
	function emailDraft(item: SupportCase) { return emailDrafts[item.id] ?? { subject: `Re: ${item.subject}`, body: "" }; }
	function patchEmail(item: SupportCase, values: Partial<ReturnType<typeof emailDraft>>) { setEmailDrafts((current) => ({ ...current, [item.id]: { ...emailDraft(item), ...values } })); }
	return (
		<section className="rounded-2xl border border-slate-200 dark:border-[#334155] bg-white dark:bg-[#111827] p-5 shadow-sm sm:p-7">
			<div className="flex flex-wrap items-end justify-between gap-4"><div><h1 className="font-heading text-2xl font-bold text-navy-900 dark:text-[#f8fafc]">Support cases</h1><p className="mt-1 text-sm text-slate-600 dark:text-[#cbd5e1]">Triage, assign, resolve, and close durable support requests.</p></div><div className="flex gap-2"><input aria-label="Search support cases" value={query} onChange={(e) => setQuery(e.target.value)} placeholder="Search cases" className="min-h-11 rounded-lg border border-slate-300 dark:border-[#334155] px-3" /><select aria-label="Filter support cases by status" value={status} onChange={(e) => setStatus(e.target.value as SupportCaseStatus | "")} className="min-h-11 rounded-lg border border-slate-300 dark:border-[#334155] px-3"><option value="">All statuses</option>{STATUSES.map((item) => <option key={item}>{item}</option>)}</select></div></div>
			{cases.isLoading && <LoadingState label="Loading support cases…" />}{cases.isError && <ErrorState message="Support cases could not be loaded." />}
			<div className="mt-6 flex flex-col gap-5">{cases.data?.items.map((item) => { const values = draft(item); const draftEmail = emailDraft(item); return <article key={item.id} className="rounded-xl border border-slate-200 dark:border-[#334155] p-5"><div className="flex flex-wrap justify-between gap-3"><div><p className="text-xs font-semibold uppercase tracking-wide text-green-700">{item.category.replaceAll("_", " ")}</p><h2 className="mt-1 font-heading text-lg font-bold text-navy-900 dark:text-[#f8fafc]">{item.subject}</h2><p className="mt-1 text-sm text-slate-500 dark:text-[#cbd5e1]">{item.requesterName} · {item.requesterEmail}{item.organizationId && item.organizationName ? <> · <Link to={appPaths.platformOrganization(item.organizationId)} className="font-medium text-info-blue hover:underline">{item.organizationName}</Link></> : ""}</p></div><p className="text-xs text-slate-500 dark:text-[#cbd5e1]">{item.id}<br />{new Date(item.createdAt).toLocaleString()}</p></div><p className="mt-4 whitespace-pre-wrap rounded-lg bg-ice-50 dark:bg-[#0f172a] p-4 text-sm leading-6 text-slate-700 dark:text-[#cbd5e1]">{item.description}</p><div className="mt-4 grid gap-3 md:grid-cols-3"><label className="text-sm font-semibold">Status<select value={values.status} onChange={(e) => patch(item, { status: e.target.value as SupportCaseStatus })} className="mt-1 min-h-11 w-full rounded-lg border border-slate-300 dark:border-[#334155] px-3 font-normal">{STATUSES.map((value) => <option key={value}>{value}</option>)}</select></label><label className="text-sm font-semibold">Priority<select value={values.priority} onChange={(e) => patch(item, { priority: e.target.value as SupportCasePriority })} className="mt-1 min-h-11 w-full rounded-lg border border-slate-300 dark:border-[#334155] px-3 font-normal">{PRIORITIES.map((value) => <option key={value}>{value}</option>)}</select></label><label className="text-sm font-semibold">Assigned to<select value={values.assignedPlatformUserId} onChange={(e) => patch(item, { assignedPlatformUserId: e.target.value })} className="mt-1 min-h-11 w-full rounded-lg border border-slate-300 dark:border-[#334155] px-3 font-normal"><option value="">Unassigned</option>{platformAdmins.map((user) => <option key={user.userId} value={user.userId}>{user.displayName}</option>)}</select></label></div><label className="mt-3 block text-sm font-semibold">Resolution note<textarea rows={3} value={values.resolution} onChange={(e) => patch(item, { resolution: e.target.value })} className="mt-1 w-full rounded-lg border border-slate-300 dark:border-[#334155] px-3 py-2 font-normal" /></label><div className="mt-3 flex items-center gap-3"><button onClick={() => update.mutate({ caseId: item.id, status: values.status, priority: values.priority, assignedPlatformUserId: values.assignedPlatformUserId || null, resolution: values.resolution || null })} disabled={update.isPending} className="rounded-lg bg-green-600 px-4 py-2 text-sm font-semibold text-white">Save case</button>{item.resolution && <span className="text-xs text-slate-500 dark:text-[#cbd5e1]">Current resolution recorded</span>}<button type="button" onClick={() => setOpenEmailCaseId(openEmailCaseId === item.id ? null : item.id)} className="ml-auto rounded-lg border border-slate-300 dark:border-[#334155] px-4 py-2 text-sm font-semibold text-navy-900 dark:text-[#f8fafc]">{openEmailCaseId === item.id ? "Cancel" : "Send email"}</button></div>
				{openEmailCaseId === item.id && (
					<div className="mt-3 rounded-lg border border-slate-200 dark:border-[#334155] bg-ice-50 dark:bg-[#0f172a] p-4">
						<p className="text-xs text-slate-500 dark:text-[#cbd5e1]">Sends one message to <strong>{item.requesterEmail}</strong>. This is one-way — there is no reply thread; a further reply from the requester comes back as a new support case, not into this one.</p>
						<label className="mt-3 block text-sm font-semibold">Subject<input value={draftEmail.subject} onChange={(e) => patchEmail(item, { subject: e.target.value })} className="mt-1 min-h-11 w-full rounded-lg border border-slate-300 dark:border-[#334155] px-3 font-normal" /></label>
						<label className="mt-3 block text-sm font-semibold">Message<textarea rows={5} value={draftEmail.body} onChange={(e) => patchEmail(item, { body: e.target.value })} className="mt-1 w-full rounded-lg border border-slate-300 dark:border-[#334155] px-3 py-2 font-normal" /></label>
						<div className="mt-3 flex items-center gap-3">
							<button
								type="button"
								disabled={sendEmail.isPending || draftEmail.subject.trim().length < 3 || draftEmail.body.trim().length < 3}
								onClick={() => sendEmail.mutate({ caseId: item.id, subject: draftEmail.subject, body: draftEmail.body }, { onSuccess: () => { setSentCaseIds((current) => ({ ...current, [item.id]: true })); setOpenEmailCaseId(null); setEmailDrafts((current) => ({ ...current, [item.id]: { subject: `Re: ${item.subject}`, body: "" } })); } })}
								className="rounded-lg bg-green-600 px-4 py-2 text-sm font-semibold text-white disabled:opacity-50"
							>
								{sendEmail.isPending ? "Sending…" : "Send"}
							</button>
							{sendEmail.isError && <span className="text-xs text-error-red">The email could not be sent.</span>}
						</div>
					</div>
				)}
				{sentCaseIds[item.id] && openEmailCaseId !== item.id && <p className="mt-2 text-xs text-green-700">Email sent.</p>}
			</article>; })}</div>
			{update.isError && <div className="mt-5"><ErrorState message="The support case update could not be saved. Resolved and closed cases require a resolution note." /></div>}
		</section>
	);
}
