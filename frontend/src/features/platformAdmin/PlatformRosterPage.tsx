import { useState } from "react";
import { Link } from "react-router-dom";
import { ClearanceStatusPill } from "../eligibility/ClearanceStatusPill";
import type { ClearanceStatus } from "../eligibility/types";
import { usePlatformAthletes, usePlatformCoaches } from "./api";
import type { PlatformAthleteListItem, PlatformCoachListItem } from "./types";

const ELIGIBILITY_OPTIONS: ClearanceStatus[] = ["ROSTER_PENDING", "DOCUMENTS_REQUIRED", "UNDER_REVIEW", "CLEARED", "EXPIRED", "INELIGIBLE"];

const COACH_ROLE_LABELS: Record<string, string> = {
	COACH_READ: "Coach (read-only)",
	TEAM_EDITOR: "Coach (editor)",
	TEAM_MANAGER: "Team manager",
};

/**
 * Platform Admin org > team > household > athlete/coach drill-down (DESIGN-DOC.md
 * section 14.1L). Read-only, like PlatformPaymentsPage/PlatformSwagShopPage — every
 * row links out to the real Organization Console for any actual change, since a
 * platform admin needs an active support session to touch org data at all.
 */
export function PlatformRosterPage() {
	const [personType, setPersonType] = useState<"athletes" | "coaches">("athletes");
	const [view, setView] = useState<"table" | "card">("table");
	const [queryInput, setQueryInput] = useState("");
	const [query, setQuery] = useState("");
	const [eligibilityStatus, setEligibilityStatus] = useState("");
	const [page, setPage] = useState(0);

	const athletes = usePlatformAthletes({ query, eligibilityStatus, page, size: 25 });
	const coaches = usePlatformCoaches({ query, page, size: 25 });
	const active = personType === "athletes" ? athletes : coaches;

	function onSearchSubmit(event: React.FormEvent) {
		event.preventDefault();
		setPage(0);
		setQuery(queryInput);
	}

	function switchPersonType(next: "athletes" | "coaches") {
		setPersonType(next);
		setPage(0);
	}

	return (
		<div className="flex flex-col gap-6">
			<div>
				<h1 className="font-heading text-2xl font-bold text-navy-900 dark:text-[#f8fafc]">Athletes &amp; Coaches</h1>
				<p className="mt-1 text-slate-500 dark:text-[#cbd5e1]">
					Every athlete and coach across every organization, team, and household — read-only. Open an organization below and start a support
					session to make any real change.
				</p>
			</div>

			<div className="flex flex-wrap items-center justify-between gap-3">
				<div className="inline-flex rounded-lg border border-slate-300 dark:border-[#334155]" role="tablist" aria-label="Person type">
					<button type="button" role="tab" aria-selected={personType === "athletes"} onClick={() => switchPersonType("athletes")} className={`rounded-l-lg px-4 py-2 text-sm font-medium ${personType === "athletes" ? "bg-navy-900 text-white" : "text-navy-900 dark:text-[#f8fafc]"}`}>
						Athletes
					</button>
					<button type="button" role="tab" aria-selected={personType === "coaches"} onClick={() => switchPersonType("coaches")} className={`rounded-r-lg px-4 py-2 text-sm font-medium ${personType === "coaches" ? "bg-navy-900 text-white" : "text-navy-900 dark:text-[#f8fafc]"}`}>
						Coaches
					</button>
				</div>
				<div className="inline-flex rounded-lg border border-slate-300 dark:border-[#334155]" role="tablist" aria-label="View">
					<button type="button" role="tab" aria-selected={view === "table"} onClick={() => setView("table")} className={`rounded-l-lg px-3 py-2 text-sm font-medium ${view === "table" ? "bg-slate-100 dark:bg-slate-800 text-navy-900 dark:text-[#f8fafc]" : "text-slate-500 dark:text-[#cbd5e1]"}`}>
						Table
					</button>
					<button type="button" role="tab" aria-selected={view === "card"} onClick={() => setView("card")} className={`rounded-r-lg px-3 py-2 text-sm font-medium ${view === "card" ? "bg-slate-100 dark:bg-slate-800 text-navy-900 dark:text-[#f8fafc]" : "text-slate-500 dark:text-[#cbd5e1]"}`}>
						Cards
					</button>
				</div>
			</div>

			<form onSubmit={onSearchSubmit} className="flex flex-wrap items-end gap-3 rounded-xl border border-slate-200 dark:border-[#334155] bg-white dark:bg-[#111827] p-4">
				<label className="flex min-w-64 flex-1 flex-col gap-1 text-sm font-medium text-navy-900 dark:text-[#f8fafc]">
					Search
					<input value={queryInput} onChange={(event) => setQueryInput(event.target.value)} placeholder={personType === "athletes" ? "Athlete name or household" : "Coach name or email"} className="min-h-11 rounded-md border border-slate-300 dark:border-[#334155] px-3 py-2 font-normal" />
				</label>
				{personType === "athletes" && (
					<label className="flex flex-col gap-1 text-sm font-medium text-navy-900 dark:text-[#f8fafc]">
						Eligibility
						<select value={eligibilityStatus} onChange={(event) => { setEligibilityStatus(event.target.value); setPage(0); }} className="min-h-11 rounded-md border border-slate-300 dark:border-[#334155] bg-white dark:bg-[#111827] px-3 py-2 font-normal">
							<option value="">All statuses</option>
							{ELIGIBILITY_OPTIONS.map((value) => <option key={value} value={value}>{value.replaceAll("_", " ")}</option>)}
						</select>
					</label>
				)}
				<button type="submit" className="min-h-11 rounded-md bg-navy-900 px-4 py-2 font-semibold text-white hover:bg-navy-800">Search</button>
			</form>

			{active.isLoading && <p className="text-slate-500 dark:text-[#cbd5e1]">Loading…</p>}
			{(active.isError || (!active.isLoading && !active.data)) && (
				<div className="rounded-lg border border-rose-300 bg-rose-50 p-4 text-rose-800">
					Could not load {personType}. <button type="button" className="underline" onClick={() => active.refetch()}>Retry</button>
				</div>
			)}

			{active.data && personType === "athletes" && view === "table" && <AthleteTable items={athletes.data?.items ?? []} />}
			{active.data && personType === "athletes" && view === "card" && <AthleteCards items={athletes.data?.items ?? []} />}
			{active.data && personType === "coaches" && view === "table" && <CoachTable items={coaches.data?.items ?? []} />}
			{active.data && personType === "coaches" && view === "card" && <CoachCards items={coaches.data?.items ?? []} />}

			{active.data && active.data.items.length === 0 && (
				<p className="rounded-xl border border-slate-200 dark:border-[#334155] bg-white dark:bg-[#111827] p-6 text-center text-sm text-slate-500 dark:text-[#cbd5e1]">
					No {personType} match these filters.
				</p>
			)}

			{active.data && (
				<div className="flex items-center justify-between text-sm text-slate-600 dark:text-[#cbd5e1]">
					<p>{active.data.totalElements} {personType}</p>
					<div className="flex items-center gap-2">
						<button type="button" disabled={page === 0} onClick={() => setPage((value) => Math.max(0, value - 1))} className="rounded-md border border-slate-300 dark:border-[#334155] px-3 py-2 disabled:opacity-40">Previous</button>
						<span>Page {page + 1} of {Math.max(1, Math.ceil(active.data.totalElements / active.data.size))}</span>
						<button type="button" disabled={page + 1 >= Math.max(1, Math.ceil(active.data.totalElements / active.data.size))} onClick={() => setPage((value) => value + 1)} className="rounded-md border border-slate-300 dark:border-[#334155] px-3 py-2 disabled:opacity-40">Next</button>
					</div>
				</div>
			)}
		</div>
	);
}

function OrganizationLink({ organizationId }: { organizationId: string }) {
	return (
		<Link to={`/app/platform/organizations/${organizationId}`} className="rounded-md border border-slate-300 dark:border-[#334155] px-3 py-2 font-medium text-navy-900 dark:text-[#f8fafc] hover:border-green-500 hover:text-green-700">
			Open organization
		</Link>
	);
}

function AthleteTable({ items }: { items: PlatformAthleteListItem[] }) {
	if (items.length === 0) return null;
	return (
		<div className="overflow-x-auto rounded-xl border border-slate-200 dark:border-[#334155] bg-white dark:bg-[#111827]">
			<table className="w-full min-w-[1000px] text-left text-sm">
				<thead className="border-b border-slate-200 dark:border-[#334155] bg-ice-50 dark:bg-[#0f172a] text-slate-500 dark:text-[#cbd5e1]">
					<tr>
						<th className="px-4 py-3 font-medium">Athlete</th>
						<th className="px-4 py-3 font-medium">Organization</th>
						<th className="px-4 py-3 font-medium">Household</th>
						<th className="px-4 py-3 font-medium">Teams</th>
						<th className="px-4 py-3 font-medium">Eligibility</th>
						<th className="px-4 py-3 font-medium"><span className="sr-only">Actions</span></th>
					</tr>
				</thead>
				<tbody className="divide-y divide-slate-100 dark:divide-[#334155]">
					{items.map((athlete) => (
						<tr key={athlete.participantId}>
							<td className="px-4 py-3">
								<p className="font-medium text-navy-900 dark:text-[#f8fafc]">{athlete.firstName} {athlete.lastName}</p>
								{athlete.dateOfBirth && <p className="text-xs text-slate-500 dark:text-[#cbd5e1]">Born {athlete.dateOfBirth}</p>}
							</td>
							<td className="px-4 py-3 text-slate-600 dark:text-[#cbd5e1]">{athlete.organizationName}</td>
							<td className="px-4 py-3 text-slate-600 dark:text-[#cbd5e1]">{athlete.householdName}</td>
							<td className="px-4 py-3 text-slate-600 dark:text-[#cbd5e1]">{athlete.teamNames.length > 0 ? athlete.teamNames.join(", ") : "—"}</td>
							<td className="px-4 py-3">{athlete.eligibilityStatus ? <ClearanceStatusPill status={athlete.eligibilityStatus} /> : <span className="text-slate-400">—</span>}</td>
							<td className="px-4 py-3 text-right"><OrganizationLink organizationId={athlete.organizationId} /></td>
						</tr>
					))}
				</tbody>
			</table>
		</div>
	);
}

function AthleteCards({ items }: { items: PlatformAthleteListItem[] }) {
	if (items.length === 0) return null;
	return (
		<div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
			{items.map((athlete) => (
				<div key={athlete.participantId} className="flex flex-col gap-2 rounded-xl border border-slate-200 dark:border-[#334155] bg-white dark:bg-[#111827] p-4">
					<div className="flex items-start justify-between gap-2">
						<div>
							<p className="font-medium text-navy-900 dark:text-[#f8fafc]">{athlete.firstName} {athlete.lastName}</p>
							{athlete.dateOfBirth && <p className="text-xs text-slate-500 dark:text-[#cbd5e1]">Born {athlete.dateOfBirth}</p>}
						</div>
						{athlete.eligibilityStatus && <ClearanceStatusPill status={athlete.eligibilityStatus} />}
					</div>
					<p className="text-sm text-slate-600 dark:text-[#cbd5e1]">{athlete.organizationName}</p>
					<p className="text-sm text-slate-600 dark:text-[#cbd5e1]">Household: {athlete.householdName}</p>
					<p className="text-sm text-slate-600 dark:text-[#cbd5e1]">Teams: {athlete.teamNames.length > 0 ? athlete.teamNames.join(", ") : "—"}</p>
					<OrganizationLink organizationId={athlete.organizationId} />
				</div>
			))}
		</div>
	);
}

function CoachTable({ items }: { items: PlatformCoachListItem[] }) {
	if (items.length === 0) return null;
	return (
		<div className="overflow-x-auto rounded-xl border border-slate-200 dark:border-[#334155] bg-white dark:bg-[#111827]">
			<table className="w-full min-w-[900px] text-left text-sm">
				<thead className="border-b border-slate-200 dark:border-[#334155] bg-ice-50 dark:bg-[#0f172a] text-slate-500 dark:text-[#cbd5e1]">
					<tr>
						<th className="px-4 py-3 font-medium">Coach</th>
						<th className="px-4 py-3 font-medium">Organization</th>
						<th className="px-4 py-3 font-medium">Team</th>
						<th className="px-4 py-3 font-medium">Role</th>
						<th className="px-4 py-3 font-medium"><span className="sr-only">Actions</span></th>
					</tr>
				</thead>
				<tbody className="divide-y divide-slate-100 dark:divide-[#334155]">
					{items.map((coach) => (
						<tr key={coach.roleAssignmentId}>
							<td className="px-4 py-3">
								<p className="font-medium text-navy-900 dark:text-[#f8fafc]">{coach.displayName}</p>
								<p className="text-xs text-slate-500 dark:text-[#cbd5e1]">{coach.email}</p>
							</td>
							<td className="px-4 py-3 text-slate-600 dark:text-[#cbd5e1]">{coach.organizationName}</td>
							<td className="px-4 py-3 text-slate-600 dark:text-[#cbd5e1]">{coach.teamName}</td>
							<td className="px-4 py-3 text-slate-600 dark:text-[#cbd5e1]">{COACH_ROLE_LABELS[coach.role] ?? coach.role}</td>
							<td className="px-4 py-3 text-right"><OrganizationLink organizationId={coach.organizationId} /></td>
						</tr>
					))}
				</tbody>
			</table>
		</div>
	);
}

function CoachCards({ items }: { items: PlatformCoachListItem[] }) {
	if (items.length === 0) return null;
	return (
		<div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
			{items.map((coach) => (
				<div key={coach.roleAssignmentId} className="flex flex-col gap-2 rounded-xl border border-slate-200 dark:border-[#334155] bg-white dark:bg-[#111827] p-4">
					<p className="font-medium text-navy-900 dark:text-[#f8fafc]">{coach.displayName}</p>
					<p className="text-sm text-slate-600 dark:text-[#cbd5e1]">{coach.email}</p>
					<p className="text-sm text-slate-600 dark:text-[#cbd5e1]">{coach.organizationName} · {coach.teamName}</p>
					<p className="text-sm text-slate-600 dark:text-[#cbd5e1]">{COACH_ROLE_LABELS[coach.role] ?? coach.role}</p>
					<OrganizationLink organizationId={coach.organizationId} />
				</div>
			))}
		</div>
	);
}
