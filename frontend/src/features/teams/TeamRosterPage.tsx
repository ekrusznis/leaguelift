import { useMemo, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { Capabilities } from "../../authorization/capabilityConstants";
import { useContexts } from "../../authorization/api";
import { hasCapability } from "../../authorization/capabilities";
import { ListToolbar } from "../../components/lists/ListToolbar";
import { EmptyState } from "../../components/states/EmptyState";
import { ErrorState } from "../../components/states/ErrorState";
import { LoadingState } from "../../components/states/LoadingState";
import { appPaths } from "../../routes/appPaths";
import { useTeamEligibilityClearance } from "../eligibility/api";
import { ClearanceStatusPill } from "../eligibility/ClearanceStatusPill";
import { useTeamRoster } from "./api";
import { TeamStaffList } from "./TeamStaffList";

export function TeamRosterPage() {
	const { organizationId, teamId } = useParams<{ organizationId: string; teamId: string }>();
	const contexts = useContexts();
	const [query, setQuery] = useState("");
	const [eligibilityFilter, setEligibilityFilter] = useState<"ALL" | "INELIGIBLE">("ALL");
	const [sort, setSort] = useState<"NAME_ASC" | "NAME_DESC">("NAME_ASC");
	const rosterQuery = useTeamRoster(organizationId ?? "", teamId ?? "");
	const clearanceQuery = useTeamEligibilityClearance(
		organizationId ?? "",
		teamId ?? "",
		eligibilityFilter === "INELIGIBLE" ? "INELIGIBLE" : null,
	);

	if (!organizationId || !teamId) return <ErrorState message="No team selected." />;

	const canViewEligibility = hasCapability(contexts.data, Capabilities.TEAM_ELIGIBILITY_VIEW, {
		contextType: "TEAM",
		resourceId: teamId,
	});
	const clearanceByParticipant = new Map((clearanceQuery.data ?? []).map((c) => [c.participantId, c]));
	const roster = rosterQuery.data ?? [];

	const visibleRoster = useMemo(() => {
		const needle = query.trim().toLowerCase();
		const filtered = roster.filter((participant) => {
			if (eligibilityFilter === "INELIGIBLE" && !clearanceByParticipant.has(participant.id)) return false;
			if (!needle) return true;
			return `${participant.firstName} ${participant.lastName}`.toLowerCase().includes(needle);
		});
		return [...filtered].sort((a, b) => {
			const left = `${a.lastName} ${a.firstName}`.toLowerCase();
			const right = `${b.lastName} ${b.firstName}`.toLowerCase();
			return sort === "NAME_ASC" ? left.localeCompare(right) : right.localeCompare(left);
		});
	}, [roster, eligibilityFilter, clearanceByParticipant, query, sort]);

	const hasFilters = eligibilityFilter !== "ALL";

	return (
		<div className="flex flex-col gap-6">
			<div>
				<Link to={appPaths.teamEvents(organizationId, teamId)} className="mb-2 inline-block text-sm text-azure-blue hover:underline">
					← Back to team schedule
				</Link>
				<h1 className="font-heading text-2xl font-bold text-navy dark:text-[#f8fafc]">Team Roster</h1>
				<p className="mt-1 text-slate-gray dark:text-[#cbd5e1]">Athletes and assigned team staff.</p>
			</div>

			<TeamStaffList organizationId={organizationId} teamId={teamId} />

			<section aria-labelledby="athletes-heading" className="flex flex-col gap-4">
				<h2 id="athletes-heading" className="font-heading text-lg font-bold text-navy dark:text-[#f8fafc]">Athletes</h2>
				<ListToolbar
					searchValue={query}
					onSearchChange={setQuery}
					searchPlaceholder="Search athletes by name"
					resultCount={visibleRoster.length}
					sortValue={sort}
					sortOptions={[
						{ value: "NAME_ASC", label: "Name A–Z" },
						{ value: "NAME_DESC", label: "Name Z–A" },
					]}
					onSortChange={(value) => setSort(value as "NAME_ASC" | "NAME_DESC")}
					hasActiveFilters={hasFilters}
					onClear={() => {
						setQuery("");
						setEligibilityFilter("ALL");
						setSort("NAME_ASC");
					}}
					filters={
						canViewEligibility ? (
							<label className="text-sm font-semibold text-navy dark:text-[#f8fafc]">
								<span className="sr-only">Eligibility</span>
								<select
									aria-label="Eligibility filter"
									value={eligibilityFilter}
									onChange={(event) => setEligibilityFilter(event.target.value as "ALL" | "INELIGIBLE")}
									className="min-h-11 rounded-lg border border-slate-300 bg-white px-3 font-normal text-navy dark:border-[#334155] dark:bg-[#0f172a] dark:text-[#f8fafc]"
								>
									<option value="ALL">All athletes</option>
									<option value="INELIGIBLE">Ineligible only</option>
								</select>
							</label>
						) : undefined
					}
				/>

				{rosterQuery.isLoading && <LoadingState label="Loading roster…" />}
				{rosterQuery.isError && <ErrorState message="Could not load the roster." onRetry={() => rosterQuery.refetch()} />}
				{rosterQuery.data && visibleRoster.length === 0 && (
					<EmptyState
						title={query.trim() || hasFilters ? "No results found" : "No participants yet"}
						description={
							query.trim() || hasFilters
								? "Try changing your search or eligibility filter."
								: "Nobody is on this team's roster yet."
						}
					/>
				)}
				{visibleRoster.length > 0 && (
					<ul className="flex flex-col gap-2" aria-label="Team roster">
						{visibleRoster.map((participant) => {
							const clearance = clearanceByParticipant.get(participant.id);
							const status = clearance?.status ?? "ROSTER_PENDING";
							return (
								<li key={participant.id} className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-slate-gray/20 bg-pure-white p-3 dark:bg-[#111827]">
									<p className="font-medium text-navy dark:text-[#f8fafc]">
										{participant.firstName} {participant.lastName}
									</p>
									{canViewEligibility && <ClearanceStatusPill status={status} />}
								</li>
							);
						})}
					</ul>
				)}
			</section>
		</div>
	);
}
