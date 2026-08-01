import { useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { useAuth } from "../../auth/AuthContext";
import { ErrorState } from "../../components/states/ErrorState";
import { useOrganizations } from "../organizations/api";
import { useCreateSupportCase, useMySupportCases } from "./api";
import { SUPPORT_CASE_CATEGORIES, type SupportCaseCategory } from "./types";

function newKey() {
	return typeof crypto !== "undefined" && "randomUUID" in crypto ? crypto.randomUUID() : `case-${Date.now()}-${Math.random().toString(36).slice(2)}`;
}

export function SupportRequestPage({ authenticated = false }: { authenticated?: boolean }) {
	const { user } = useAuth();
	const organizations = useOrganizations(0, 100, authenticated);
	const mine = useMySupportCases(authenticated);
	const createCase = useCreateSupportCase(authenticated);
	const [idempotencyKey, setIdempotencyKey] = useState(newKey);
	const [requesterName, setRequesterName] = useState("");
	const [requesterEmail, setRequesterEmail] = useState("");
	const [organizationId, setOrganizationId] = useState("");
	const [category, setCategory] = useState<SupportCaseCategory>("TECHNICAL_PROBLEM");
	const [subject, setSubject] = useState("");
	const [description, setDescription] = useState("");
	const base = authenticated ? "/app/help" : "/help";
	const organizationOptions = useMemo(() => organizations.data?.items ?? [], [organizations.data]);

	async function submit(event: React.FormEvent) {
		event.preventDefault();
		const result = await createCase.mutateAsync({
			idempotencyKey,
			organizationId: organizationId || null,
			requesterName: authenticated ? undefined : requesterName,
			requesterEmail: authenticated ? undefined : requesterEmail,
			category,
			subject,
			description,
		});
		setSubject("");
		setDescription("");
		setIdempotencyKey(newKey());
		return result;
	}

	return (
		<div className="mx-auto max-w-4xl px-4 py-10 sm:py-14">
			<Link to={base} className="text-sm font-semibold text-info-blue hover:underline">← Back to Help Center</Link>
			<div className="mt-6 grid gap-7 lg:grid-cols-[1fr_0.75fr]">
				<section className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm sm:p-8">
					<h1 className="font-heading text-3xl font-extrabold text-navy-900">Contact LeagueLift Support</h1>
					<p className="mt-3 text-slate-600">Submitting creates a durable support case before email is attempted. This is ticket/email support, not live chat, and no response-time promise is implied.</p>
					{authenticated && <p className="mt-4 rounded-lg bg-ice-50 p-3 text-sm text-slate-700">Submitting as <strong>{user?.displayName}</strong> ({user?.email}).</p>}
					<form onSubmit={submit} className="mt-6 flex flex-col gap-4">
						{!authenticated && <>
							<label className="text-sm font-semibold text-navy-900">Your name<input required minLength={2} maxLength={120} value={requesterName} onChange={(e) => setRequesterName(e.target.value)} className="mt-1 min-h-11 w-full rounded-lg border border-slate-300 px-3 font-normal" /></label>
							<label className="text-sm font-semibold text-navy-900">Email<input required type="email" maxLength={320} value={requesterEmail} onChange={(e) => setRequesterEmail(e.target.value)} className="mt-1 min-h-11 w-full rounded-lg border border-slate-300 px-3 font-normal" /></label>
						</>}
						{authenticated && organizationOptions.length > 0 && <label className="text-sm font-semibold text-navy-900">Organization (optional)<select value={organizationId} onChange={(e) => setOrganizationId(e.target.value)} className="mt-1 min-h-11 w-full rounded-lg border border-slate-300 px-3 font-normal"><option value="">No organization selected</option>{organizationOptions.map((org) => <option key={org.id} value={org.id}>{org.name}</option>)}</select></label>}
						<label className="text-sm font-semibold text-navy-900">Category<select value={category} onChange={(e) => setCategory(e.target.value as SupportCaseCategory)} className="mt-1 min-h-11 w-full rounded-lg border border-slate-300 px-3 font-normal">{SUPPORT_CASE_CATEGORIES.map((item) => <option key={item.value} value={item.value}>{item.label}</option>)}</select></label>
						<label className="text-sm font-semibold text-navy-900">Subject<input required minLength={5} maxLength={200} value={subject} onChange={(e) => setSubject(e.target.value)} className="mt-1 min-h-11 w-full rounded-lg border border-slate-300 px-3 font-normal" /></label>
						<label className="text-sm font-semibold text-navy-900">What happened?<textarea required minLength={20} maxLength={5000} rows={7} value={description} onChange={(e) => setDescription(e.target.value)} className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 font-normal" placeholder="Include the page, action, what you expected, and what happened instead." /></label>
						{createCase.isError && <ErrorState message="The support case could not be submitted. Your details remain in the form so you can retry." />}
						{createCase.isSuccess && <div role="status" className="rounded-lg border border-green-300 bg-green-50 p-4 text-sm text-green-900">Support case <strong>{createCase.data.id}</strong> was created. A confirmation email will be attempted through the notification outbox.</div>}
						<button disabled={createCase.isPending} className="min-h-11 rounded-lg bg-green-600 px-4 font-semibold text-white hover:bg-green-700 disabled:opacity-60">{createCase.isPending ? "Submitting…" : "Submit support case"}</button>
					</form>
				</section>

				<aside className="rounded-2xl bg-navy-950 p-6 text-white sm:p-7">
					<h2 className="font-heading text-xl font-bold">Before submitting</h2>
					<ul className="mt-4 list-disc space-y-3 pl-5 text-sm leading-6 text-slate-300"><li>Do not include passwords, payment card numbers, medical details, or other sensitive youth information.</li><li>Include the organization or team only when it is relevant.</li><li>For immediate safety or emergency concerns, contact the appropriate local authority rather than relying on a software support case.</li></ul>
					{authenticated && <div className="mt-8 border-t border-white/15 pt-6"><h2 className="font-heading font-bold">My recent cases</h2>{mine.isLoading && <p className="mt-3 text-sm text-slate-400">Loading…</p>}{mine.data?.items.length === 0 && <p className="mt-3 text-sm text-slate-400">No support cases yet.</p>}<div className="mt-3 flex flex-col gap-3">{mine.data?.items.slice(0, 5).map((item) => <div key={item.id} className="rounded-lg bg-white/5 p-3"><p className="text-xs uppercase tracking-wide text-green-300">{item.status.replaceAll("_", " ")}</p><p className="mt-1 text-sm font-semibold">{item.subject}</p><p className="mt-1 text-xs text-slate-400">{new Date(item.createdAt).toLocaleDateString()}</p></div>)}</div></div>}
				</aside>
			</div>
		</div>
	);
}
