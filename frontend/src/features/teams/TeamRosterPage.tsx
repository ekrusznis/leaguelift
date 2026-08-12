import { useState } from "react";
import { Link, useParams } from "react-router-dom";
import { Capabilities } from "../../authorization/capabilityConstants";
import { useContexts } from "../../authorization/api";
import { hasCapability } from "../../authorization/capabilities";
import { EmptyState } from "../../components/states/EmptyState";
import { ErrorState } from "../../components/states/ErrorState";
import { LoadingState } from "../../components/states/LoadingState";
import { appPaths } from "../../routes/appPaths";
import { useTeamEligibilityClearance } from "../eligibility/api";
import type { ClearanceStatus } from "../eligibility/types";
import { useTeamRoster } from "./api";

const CLEARANCE_LABELS: Record<ClearanceStatus, string> = {
	ROSTER_PENDING: "Not yet reviewed",
	DOCUMENTS_REQUIRED: "Documents required",
	UNDER_REVIEW: "Under review",
	CLEARED: "Cleared",
	EXPIRED: "Expired",
	INELIGIBLE: "Ineligible",
};

const CLEARANCE_CLASSES: Record<ClearanceStatus, string> = {
	ROSTER_PENDING: "bg-slate-gray/10 text-slate-gray dark:text-[#cbd5e1]",
	DOCUMENTS_REQUIRED: "bg-championship-gold/10 text-championship-gold",
	UNDER_REVIEW: "bg-info-blue/10 text-info-blue",
	CLEARED: "bg-victory-green/10 text-victory-green",
	EXPIRED: "bg-error-red/10 text-error-red",
	INELIGIBLE: "bg-error-red/10 text-error-red",
};

/**
 * Team Roster — coach-facing eligibility clearance view (Phase 37 slice 37.1, mobile
 * parity with mobile/src/app/(tabs)/teams.tsx built in Phase 31 slice 31.4). Web had no
 * per-team roster page reachable by a coach at all before this — CoachDashboard's
 * "Roster Summary" widget only ever showed a count. Reuses the existing
 * GET .../teams/{id}/participants ("a coach's team roster picker," Swag Shop) and
 * GET .../teams/{id}/eligibility/clearance?status= endpoints, both real since earlier
 * phases with no prior web consumer for either.
 */
export function TeamRosterPage() {
	const { organizationId, teamId } = useParams<{ organizationId: string; teamId: string }>();
	const contexts = useContexts();
	const [ineligibleOnly, setIneligibleOnly] = useState(false);
	const rosterQuery = useTeamRoster(organizationId ?? "", teamId ?? "");
	const clearanceQuery = useTeamEligibilityClearance(organizationId ?? "", teamId ?? "", ineligibleOnly ? "INELIGIBLE" : null);

	if (!organizationId || !teamId) return <ErrorState message="No team selected." />;

	const canViewEligibility = hasCapability(contexts.data, Capabilities.TEAM_ELIGIBILITY_VIEW, { contextType: "TEAM", resourceId: teamId });
	const clearanceByParticipant = new Map((clearanceQuery.data ?? []).map((c) => [c.participantId, c]));
	const roster = rosterQuery.data ?? [];
	const visibleRoster = ineligibleOnly ? roster.filter((p) => clearanceByParticipant.has(p.id)) : roster;

	return (
		<div className="flex flex-col gap-6">
			<div>
				<Link to={appPaths.teamEvents(organizationId, teamId)} className="mb-2 inline-block text-sm text-azure-blue hover:underline">
					← Back to team schedule
				</Link>
				<h1 className="font-heading text-2xl font-bold text-navy dark:text-[#f8fafc]">Team Roster</h1>
				<p className="mt-1 text-slate-gray dark:text-[#cbd5e1]">Every athlete assigned to this team.</p>
			</div>

			{canViewEligibility && (
				<button
					type="button"
					onClick={() => setIneligibleOnly((value) => !value)}
					aria-pressed={ineligibleOnly}
					className={`self-start rounded-full px-3 py-1 text-sm font-medium ${ineligibleOnly ? "bg-navy text-white" : "border border-slate-gray/30 text-slate-gray dark:text-[#cbd5e1] hover:bg-ice-white hover:dark:bg-[#0f172a]"}`}
				>
					{ineligibleOnly ? "Showing ineligible only" : "Show ineligible only"}
				</button>
			)}

			{rosterQuery.isLoading && <LoadingState label="Loading roster…" />}
			{rosterQuery.isError && <ErrorState message="Could not load the roster." onRetry={() => rosterQuery.refetch()} />}
			{rosterQuery.data && visibleRoster.length === 0 && (
				<EmptyState
					title={ineligibleOnly ? "No ineligible players" : "No participants yet"}
					description={ineligibleOnly ? "Every reviewed player on this roster is currently eligible." : "Nobody is on this team's roster yet."}
				/>
			)}

			{visibleRoster.length > 0 && (
				<ul className="flex flex-col gap-2" aria-label="Team roster">
					{visibleRoster.map((participant) => {
						const clearance = clearanceByParticipant.get(participant.id);
						const status = clearance?.status ?? "ROSTER_PENDING";
						return (
							<li key={participant.id} className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-slate-gray/20 bg-pure-white dark:bg-[#111827] p-3">
								<p className="font-medium text-navy dark:text-[#f8fafc]">
									{participant.firstName} {participant.lastName}
								</p>
								{canViewEligibility && (
									<span className={`rounded-full px-2 py-0.5 text-xs font-medium ${CLEARANCE_CLASSES[status]}`}>
										{CLEARANCE_LABELS[status]}
									</span>
								)}
							</li>
						);
					})}
				</ul>
			)}
		</div>
	);
}
