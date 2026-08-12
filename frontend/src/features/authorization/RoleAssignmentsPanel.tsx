import { useState } from "react";
import { Button } from "../../components/Button";
import { LoadingState } from "../../components/states/LoadingState";
import { ErrorState } from "../../components/states/ErrorState";
import type { Membership } from "../members/types";
import type { RoleAssignment } from "./types";

export interface RoleOption {
	value: string;
	label: string;
}

interface RoleAssignmentsPanelProps {
	assignments: RoleAssignment[] | undefined;
	isLoading: boolean;
	isError: boolean;
	onRetry: () => void;
	members: Membership[] | undefined;
	roleOptions: RoleOption[];
	onGrant: (userId: string, role: string) => Promise<unknown>;
	onRevoke: (assignmentId: string) => void;
	isGranting: boolean;
	isRevoking: boolean;
}

function memberLabel(member: Membership): string {
	return member.userDisplayName ?? member.userEmail ?? member.userId;
}

/**
 * Presentational grant/revoke UI shared by team and tournament role assignments
 * (ADR-020 consequence: `POST/DELETE .../role-assignments` existed with no admin
 * screen). The caller supplies resource-specific query/mutation results — this
 * component doesn't know whether it's showing a team or a tournament.
 */
export function RoleAssignmentsPanel({
	assignments,
	isLoading,
	isError,
	onRetry,
	members,
	roleOptions,
	onGrant,
	onRevoke,
	isGranting,
	isRevoking,
}: RoleAssignmentsPanelProps) {
	const grantedUserIds = new Set((assignments ?? []).map((a) => a.userId));
	const grantableMembers = (members ?? []).filter((m) => !grantedUserIds.has(m.userId));

	const [selectedUserId, setSelectedUserId] = useState("");
	const [selectedRole, setSelectedRole] = useState(roleOptions[0]?.value ?? "");

	if (isLoading) return <LoadingState label="Loading access…" />;
	if (isError) return <ErrorState message="Could not load who has access." onRetry={onRetry} />;

	return (
		<div className="flex flex-col gap-3 rounded-lg border border-slate-gray/20 bg-ice-white dark:bg-[#0f172a] p-4">
			{assignments && assignments.length > 0 ? (
				<ul className="flex flex-col gap-2" aria-label="Current access">
					{assignments.map((assignment) => (
						<li key={assignment.id} className="flex items-center justify-between gap-3 rounded-md bg-pure-white dark:bg-[#111827] p-2.5">
							<div className="min-w-0">
								<p className="truncate text-sm font-medium text-navy dark:text-[#f8fafc]">
									{assignment.userDisplayName ?? assignment.userEmail ?? assignment.userId}
								</p>
								<p className="text-xs text-slate-gray dark:text-[#cbd5e1]">{assignment.role.replaceAll("_", " ")}</p>
							</div>
							<Button
								type="button"
								variant="secondary"
								onClick={() => onRevoke(assignment.id)}
								disabled={isRevoking}
							>
								Revoke
							</Button>
						</li>
					))}
				</ul>
			) : (
				<p className="text-sm text-slate-gray dark:text-[#cbd5e1]">No one has been granted access yet.</p>
			)}

			{grantableMembers.length > 0 ? (
				<form
					className="flex flex-wrap items-end gap-2 border-t border-slate-gray/20 pt-3"
					onSubmit={(event) => {
						event.preventDefault();
						if (!selectedUserId || !selectedRole) return;
						onGrant(selectedUserId, selectedRole).then(() => setSelectedUserId(""));
					}}
				>
					<div className="flex flex-col gap-1">
						<label htmlFor="grant-member" className="text-xs font-medium text-navy dark:text-[#f8fafc]">
							Member
						</label>
						<select
							id="grant-member"
							value={selectedUserId}
							onChange={(event) => setSelectedUserId(event.target.value)}
							className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2 text-sm"
						>
							<option value="">Select a member…</option>
							{grantableMembers.map((member) => (
								<option key={member.userId} value={member.userId}>
									{memberLabel(member)}
								</option>
							))}
						</select>
					</div>
					<div className="flex flex-col gap-1">
						<label htmlFor="grant-role" className="text-xs font-medium text-navy dark:text-[#f8fafc]">
							Role
						</label>
						<select
							id="grant-role"
							value={selectedRole}
							onChange={(event) => setSelectedRole(event.target.value)}
							className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2 text-sm"
						>
							{roleOptions.map((option) => (
								<option key={option.value} value={option.value}>
									{option.label}
								</option>
							))}
						</select>
					</div>
					<Button type="submit" disabled={!selectedUserId || isGranting}>
						{isGranting ? "Granting…" : "Grant access"}
					</Button>
				</form>
			) : (
				<p className="border-t border-slate-gray/20 pt-3 text-xs text-slate-gray dark:text-[#cbd5e1]">
					Every current organization member already has access, or there are no other members to grant.
				</p>
			)}
		</div>
	);
}
