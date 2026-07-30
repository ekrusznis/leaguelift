import { useOrganizationMembers } from "../members/api";
import { useGrantTeamRole, useRevokeTeamRole, useTeamRoleAssignments } from "./api";
import { RoleAssignmentsPanel, type RoleOption } from "./RoleAssignmentsPanel";

const TEAM_ROLE_OPTIONS: RoleOption[] = [
	{ value: "COACH_READ", label: "Coach (read-only)" },
	{ value: "TEAM_EDITOR", label: "Team Editor" },
	{ value: "TEAM_MANAGER", label: "Team Manager" },
];

/** Grant/revoke access to one team — wires the team-specific hooks into the shared panel. */
export function TeamRoleAssignmentsSection({ organizationId, teamId }: { organizationId: string; teamId: string }) {
	const assignments = useTeamRoleAssignments(organizationId, teamId);
	const members = useOrganizationMembers(organizationId);
	const grant = useGrantTeamRole(organizationId, teamId);
	const revoke = useRevokeTeamRole(organizationId, teamId);

	return (
		<RoleAssignmentsPanel
			assignments={assignments.data}
			isLoading={assignments.isLoading}
			isError={assignments.isError}
			onRetry={() => assignments.refetch()}
			members={members.data?.items}
			roleOptions={TEAM_ROLE_OPTIONS}
			onGrant={(userId, role) => grant.mutateAsync({ userId, role })}
			onRevoke={(assignmentId) => revoke.mutate(assignmentId)}
			isGranting={grant.isPending}
			isRevoking={revoke.isPending}
		/>
	);
}
