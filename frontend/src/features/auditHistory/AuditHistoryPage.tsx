import { useEffect, useMemo, useState } from "react";
import { ErrorState } from "../../components/states/ErrorState";
import { LoadingState } from "../../components/states/LoadingState";
import { useAuditHistory } from "./api";
import type { AuditHistoryFilters, AuditHistoryItem, AuditResult, AuditSortDirection, AuditSortField } from "./types";

interface Props {
	title?: string;
	description?: string;
}

interface FilterForm {
	from: string;
	to: string;
	action: string;
	result: AuditResult | "";
	keyword: string;
	user: string;
	organizationId: string;
	teamId: string;
	sortBy: AuditSortField;
	direction: AuditSortDirection;
}

const DEFAULT_FILTERS: FilterForm = {
	from: "",
	to: "",
	action: "",
	result: "",
	keyword: "",
	user: "",
	organizationId: "",
	teamId: "",
	sortBy: "DATE",
	direction: "DESC",
};

function startOfDate(value: string) {
	return value ? new Date(`${value}T00:00:00.000Z`).toISOString() : undefined;
}

function afterDate(value: string) {
	if (!value) return undefined;
	const date = new Date(`${value}T00:00:00.000Z`);
	date.setUTCDate(date.getUTCDate() + 1);
	return date.toISOString();
}

function resultClass(result: AuditResult) {
	switch (result) {
		case "SUCCESS": return "bg-emerald-50 text-emerald-700";
		case "PARTIAL": return "bg-amber-50 dark:bg-amber-950 text-amber-800";
		case "DENIED": return "bg-orange-50 dark:bg-orange-950 text-orange-800";
		case "FAILURE": return "bg-rose-50 text-rose-700";
	}
}

function actorLabel(item: AuditHistoryItem) {
	if (item.actorDisplayName) return item.actorDisplayName;
	if (item.actorType === "PROVIDER") return "Provider";
	if (item.actorType === "SYSTEM") return "Rally26 system";
	return "User";
}

function scopeLabel(item: AuditHistoryItem) {
	return [item.organizationName, item.teamName, item.householdName, item.participantDisplayName].filter(Boolean).join(" · ") || "Platform";
}

export function AuditHistoryPage({
	title = "History",
	description = "Role-scoped, immutable activity history. Your permissions are applied by the server before any records are returned.",
}: Props) {
	const [draft, setDraft] = useState<FilterForm>(DEFAULT_FILTERS);
	const [applied, setApplied] = useState<FilterForm>(DEFAULT_FILTERS);
	const [cursor, setCursor] = useState<string | undefined>();
	const [previousCursors, setPreviousCursors] = useState<Array<string | undefined>>([]);

	const filters = useMemo<AuditHistoryFilters>(() => ({
		from: startOfDate(applied.from),
		to: afterDate(applied.to),
		action: applied.action || undefined,
		result: applied.result,
		keyword: applied.keyword || undefined,
		user: applied.user || undefined,
		organizationId: applied.organizationId || undefined,
		teamId: applied.teamId || undefined,
		sortBy: applied.sortBy,
		direction: applied.direction,
		size: 50,
		cursor,
	}), [applied, cursor]);

	const history = useAuditHistory(filters);
	const access = history.data?.filterAccess;

	useEffect(() => {
		if (!access) return;
		setDraft((current) => ({
			...current,
			user: access.canFilterUser ? current.user : "",
			teamId: access.canFilterTeam ? current.teamId : "",
			organizationId: access.canFilterOrganization ? current.organizationId : "",
		}));
	}, [access]);

	function applyFilters() {
		setApplied({
			...draft,
			user: access?.canFilterUser ? draft.user : "",
			teamId: access?.canFilterTeam ? draft.teamId : "",
			organizationId: access?.canFilterOrganization ? draft.organizationId : "",
		});
		setCursor(undefined);
		setPreviousCursors([]);
	}

	function clearFilters() {
		setDraft(DEFAULT_FILTERS);
		setApplied(DEFAULT_FILTERS);
		setCursor(undefined);
		setPreviousCursors([]);
	}

	function nextPage() {
		if (!history.data?.nextCursor) return;
		setPreviousCursors((current) => [...current, cursor]);
		setCursor(history.data.nextCursor);
	}

	function previousPage() {
		setPreviousCursors((current) => {
			if (current.length === 0) return current;
			const copy = [...current];
			setCursor(copy.pop());
			return copy;
		});
	}

	return (
		<div className="flex flex-col gap-6">
			<div>
				<h1 className="font-heading text-2xl font-bold text-navy-900 dark:text-[#f8fafc]">{title}</h1>
				<p className="mt-1 max-w-3xl text-sm text-slate-500 dark:text-[#cbd5e1]">{description}</p>
			</div>

			<section className="rounded-xl border border-slate-200 dark:border-[#334155] bg-white dark:bg-[#111827] p-5" aria-labelledby="history-filters-heading">
				<div className="flex flex-wrap items-center justify-between gap-2">
					<h2 id="history-filters-heading" className="font-semibold text-navy-900 dark:text-[#f8fafc]">Filters and sorting</h2>
					<p className="text-xs text-slate-500 dark:text-[#cbd5e1]">Keyword search never inspects private audit metadata.</p>
				</div>
				<div className="mt-4 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
					<label className="text-sm font-medium text-slate-700 dark:text-[#cbd5e1]">From date
						<input type="date" value={draft.from} onChange={(event) => setDraft({ ...draft, from: event.target.value })} className="mt-1 w-full rounded-lg border border-slate-300 dark:border-[#334155] px-3 py-2" />
					</label>
					<label className="text-sm font-medium text-slate-700 dark:text-[#cbd5e1]">Through date
						<input type="date" value={draft.to} onChange={(event) => setDraft({ ...draft, to: event.target.value })} className="mt-1 w-full rounded-lg border border-slate-300 dark:border-[#334155] px-3 py-2" />
					</label>
					<label className="text-sm font-medium text-slate-700 dark:text-[#cbd5e1]">Action
						<input value={draft.action} onChange={(event) => setDraft({ ...draft, action: event.target.value })} placeholder="e.g. TEAM_UPDATED" className="mt-1 w-full rounded-lg border border-slate-300 dark:border-[#334155] px-3 py-2" />
					</label>
					<label className="text-sm font-medium text-slate-700 dark:text-[#cbd5e1]">Result
						<select value={draft.result} onChange={(event) => setDraft({ ...draft, result: event.target.value as AuditResult | "" })} className="mt-1 w-full rounded-lg border border-slate-300 dark:border-[#334155] px-3 py-2">
							<option value="">All results</option>
							<option value="SUCCESS">Success</option>
							<option value="PARTIAL">Partial</option>
							<option value="DENIED">Denied</option>
							<option value="FAILURE">Failure</option>
						</select>
					</label>
					<label className="text-sm font-medium text-slate-700 dark:text-[#cbd5e1] sm:col-span-2">Keyword
						<input value={draft.keyword} onChange={(event) => setDraft({ ...draft, keyword: event.target.value })} placeholder="Summary, action, user, team, organization…" className="mt-1 w-full rounded-lg border border-slate-300 dark:border-[#334155] px-3 py-2" />
					</label>
					{access?.canFilterUser && (
						<label className="text-sm font-medium text-slate-700 dark:text-[#cbd5e1]">User
							<input value={draft.user} onChange={(event) => setDraft({ ...draft, user: event.target.value })} placeholder="Name" className="mt-1 w-full rounded-lg border border-slate-300 dark:border-[#334155] px-3 py-2" />
						</label>
					)}
					{access?.canFilterTeam && (
						<label className="text-sm font-medium text-slate-700 dark:text-[#cbd5e1]">Team ID
							<input value={draft.teamId} onChange={(event) => setDraft({ ...draft, teamId: event.target.value })} placeholder="Optional UUID" className="mt-1 w-full rounded-lg border border-slate-300 dark:border-[#334155] px-3 py-2 font-mono text-xs" />
						</label>
					)}
					{access?.canFilterOrganization && (
						<label className="text-sm font-medium text-slate-700 dark:text-[#cbd5e1]">Organization ID
							<input value={draft.organizationId} onChange={(event) => setDraft({ ...draft, organizationId: event.target.value })} placeholder="Optional UUID" className="mt-1 w-full rounded-lg border border-slate-300 dark:border-[#334155] px-3 py-2 font-mono text-xs" />
						</label>
					)}
					<label className="text-sm font-medium text-slate-700 dark:text-[#cbd5e1]">Sort by
						<select value={draft.sortBy} onChange={(event) => setDraft({ ...draft, sortBy: event.target.value as AuditSortField })} className="mt-1 w-full rounded-lg border border-slate-300 dark:border-[#334155] px-3 py-2">
							<option value="DATE">Date</option>
							<option value="ACTION">Action</option>
							<option value="RESULT">Result</option>
						</select>
					</label>
					<label className="text-sm font-medium text-slate-700 dark:text-[#cbd5e1]">Direction
						<select value={draft.direction} onChange={(event) => setDraft({ ...draft, direction: event.target.value as AuditSortDirection })} className="mt-1 w-full rounded-lg border border-slate-300 dark:border-[#334155] px-3 py-2">
							<option value="DESC">Descending</option>
							<option value="ASC">Ascending</option>
						</select>
					</label>
				</div>
				<div className="mt-4 flex flex-wrap gap-2">
					<button type="button" onClick={applyFilters} className="rounded-lg bg-navy-900 px-4 py-2 text-sm font-semibold text-white hover:bg-navy-800">Apply</button>
					<button type="button" onClick={clearFilters} className="rounded-lg border border-slate-300 dark:border-[#334155] px-4 py-2 text-sm font-semibold text-slate-700 dark:text-[#cbd5e1] hover:bg-slate-50 hover:dark:bg-[#1e293b]">Clear</button>
				</div>
			</section>

			{history.isLoading && <LoadingState label="Loading history…" />}
			{history.isError && <ErrorState message="Could not load history." onRetry={() => history.refetch()} />}
			{history.data && (
				<section className="overflow-hidden rounded-xl border border-slate-200 dark:border-[#334155] bg-white dark:bg-[#111827]">
					<div className="overflow-x-auto">
						<table className="min-w-full divide-y divide-slate-200 dark:divide-[#334155] text-left text-sm">
							<thead className="bg-slate-50 dark:bg-[#1e293b] text-xs uppercase tracking-wide text-slate-500 dark:text-[#cbd5e1]">
								<tr><th className="px-4 py-3">Date</th><th className="px-4 py-3">Action / summary</th><th className="px-4 py-3">Result</th><th className="px-4 py-3">User</th><th className="px-4 py-3">Scope</th></tr>
							</thead>
							<tbody className="divide-y divide-slate-100 dark:divide-[#334155]">
								{history.data.items.map((item) => (
									<tr key={item.id} className="align-top">
										<td className="whitespace-nowrap px-4 py-4 text-slate-600 dark:text-[#cbd5e1]"><time dateTime={item.occurredAt}>{new Date(item.occurredAt).toLocaleString()}</time></td>
										<td className="px-4 py-4"><p className="font-mono text-xs font-semibold text-navy-900 dark:text-[#f8fafc]">{item.action}</p><p className="mt-1 max-w-xl text-slate-600 dark:text-[#cbd5e1]">{item.summary}</p><p className="mt-1 text-xs text-slate-400">{item.entityType} · {item.entityId}</p></td>
										<td className="px-4 py-4"><span className={`inline-flex rounded-full px-2 py-1 text-xs font-semibold ${resultClass(item.result)}`}>{item.result}</span></td>
										<td className="px-4 py-4 text-slate-700 dark:text-[#cbd5e1]">{actorLabel(item)}{item.targetDisplayName && <p className="mt-1 text-xs text-slate-500 dark:text-[#cbd5e1]">Affected: {item.targetDisplayName}</p>}</td>
										<td className="px-4 py-4 text-slate-600 dark:text-[#cbd5e1]">{scopeLabel(item)}</td>
									</tr>
								))}
								{history.data.items.length === 0 && <tr><td colSpan={5} className="px-4 py-10 text-center text-slate-500 dark:text-[#cbd5e1]">No history matches these filters.</td></tr>}
							</tbody>
						</table>
					</div>
					<div className="flex items-center justify-between gap-3 border-t border-slate-200 dark:border-[#334155] px-4 py-3">
						<button type="button" disabled={previousCursors.length === 0} onClick={previousPage} className="rounded-lg border border-slate-300 dark:border-[#334155] px-3 py-2 text-sm font-semibold text-slate-700 dark:text-[#cbd5e1] disabled:cursor-not-allowed disabled:opacity-40">Previous</button>
						<p className="text-xs text-slate-500 dark:text-[#cbd5e1]">Up to 50 immutable events per page</p>
						<button type="button" disabled={!history.data.nextCursor} onClick={nextPage} className="rounded-lg border border-slate-300 dark:border-[#334155] px-3 py-2 text-sm font-semibold text-slate-700 dark:text-[#cbd5e1] disabled:cursor-not-allowed disabled:opacity-40">Next</button>
					</div>
				</section>
			)}
		</div>
	);
}
