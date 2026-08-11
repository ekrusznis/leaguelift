import { Link, useParams } from "react-router-dom";
import { Capabilities } from "../../authorization/capabilityConstants";
import { useContexts } from "../../authorization/api";
import { hasCapability } from "../../authorization/capabilities";
import { ErrorState } from "../../components/states/ErrorState";
import { appPaths } from "../../routes/appPaths";
import { EventListPanel } from "./EventListPanel";
import type { EventScope } from "./api";

export type EventsPageScope = "team" | "tournament" | "household" | "participant";

export function EventsPage({ scopeType }: { scopeType: EventsPageScope }) {
	const { organizationId, teamId, tournamentId, householdId, participantId } = useParams<{
		organizationId: string;
		teamId: string;
		tournamentId: string;
		householdId: string;
		participantId: string;
	}>();
	const contexts = useContexts();

	if (!organizationId) return <ErrorState message="No organization selected." />;

	let scope: EventScope;
	let title: string;
	let description: string;
	let canManage = false;
	let backTo = appPaths.dashboard();

	switch (scopeType) {
		case "team":
			if (!teamId) return <ErrorState message="No team selected." />;
			scope = { type: "team", organizationId, teamId };
			title = "Team Schedule";
			description = "Games, matches, practices, meetings, arrival times, and RSVP status for this team.";
			canManage = hasCapability(contexts.data, Capabilities.TEAM_EVENT_MANAGE, { contextType: "TEAM", resourceId: teamId });
			break;
		case "tournament":
			if (!tournamentId) return <ErrorState message="No tournament selected." />;
			scope = { type: "tournament", organizationId, tournamentId };
			title = "Tournament Schedule";
			description = "Tournament child events may begin with TBD details and be updated as brackets and locations become known.";
			canManage = hasCapability(contexts.data, Capabilities.TOURNAMENT_EVENT_MANAGE, { contextType: "TOURNAMENT", resourceId: tournamentId });
			break;
		case "household":
			if (!householdId) return <ErrorState message="No household selected." />;
			scope = { type: "household", organizationId, householdId };
			title = "Family Schedule";
			description = "A combined schedule for athletes linked to this household.";
			backTo = appPaths.household(organizationId, householdId, "profile");
			break;
		case "participant":
			if (!participantId) return <ErrorState message="No athlete selected." />;
			scope = { type: "participant", organizationId, participantId };
			title = "Athlete Schedule";
			description = "Upcoming events for this athlete's assigned teams.";
			break;
	}

	return (
		<div className="flex flex-col gap-6">
			<div>
				<Link to={backTo} className="mb-2 inline-block text-sm text-azure-blue hover:underline">← Back</Link>
				<h1 className="font-heading text-2xl font-bold text-navy dark:text-[#f8fafc]">{title}</h1>
				<p className="mt-1 text-slate-gray dark:text-[#cbd5e1]">{description}</p>
			</div>
			<EventListPanel scope={scope} canManage={canManage} householdId={householdId} participantId={participantId} />
		</div>
	);
}
