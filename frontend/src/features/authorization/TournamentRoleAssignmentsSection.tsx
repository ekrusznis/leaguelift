import { useOrganizationMembers } from "../members/api";
import { useGrantTournamentRole, useRevokeTournamentRole, useTournamentRoleAssignments } from "./api";
import { RoleAssignmentsPanel, type RoleOption } from "./RoleAssignmentsPanel";

const TOURNAMENT_ROLE_OPTIONS: RoleOption[] = [
	{ value: "TOURNAMENT_VIEWER", label: "Tournament Viewer" },
	{ value: "TOURNAMENT_ADMINISTRATOR", label: "Tournament Administrator" },
];

/** Grant/revoke access to one tournament — wires the tournament-specific hooks into the shared panel. */
export function TournamentRoleAssignmentsSection({ organizationId, tournamentId }: { organizationId: string; tournamentId: string }) {
	const assignments = useTournamentRoleAssignments(organizationId, tournamentId);
	const members = useOrganizationMembers(organizationId);
	const grant = useGrantTournamentRole(organizationId, tournamentId);
	const revoke = useRevokeTournamentRole(organizationId, tournamentId);

	return (
		<RoleAssignmentsPanel
			assignments={assignments.data}
			isLoading={assignments.isLoading}
			isError={assignments.isError}
			onRetry={() => assignments.refetch()}
			members={members.data?.items}
			roleOptions={TOURNAMENT_ROLE_OPTIONS}
			onGrant={(userId, role) => grant.mutateAsync({ userId, role })}
			onRevoke={(assignmentId) => revoke.mutate(assignmentId)}
			isGranting={grant.isPending}
			isRevoking={revoke.isPending}
		/>
	);
}
