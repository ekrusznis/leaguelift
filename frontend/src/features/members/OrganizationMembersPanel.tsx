import { useState } from "react";
import { useAuth } from "../../auth/AuthContext";
import { Button } from "../../components/Button";
import { ListToolbar } from "../../components/lists/ListToolbar";
import { Pagination } from "../../components/lists/Pagination";
import { Modal } from "../../components/Modal";
import { EmptyState } from "../../components/states/EmptyState";
import { ErrorState } from "../../components/states/ErrorState";
import { LoadingState } from "../../components/states/LoadingState";
import type { Membership } from "./types";
import {
	useDisableOrganizationMember,
	useInviteOwnershipTransfer,
	useMemberSearch,
	usePendingOwnershipTransferInvitation,
	useRevokeOwnershipTransferInvitation,
	useTransferOwnership,
	useUpdateOrganizationMemberRole,
} from "./searchApi";

const ROLE_LABELS: Record<string, string> = {
	OWNER: "Owner",
	ADMINISTRATOR: "Administrator",
	TEAM_ADMINISTRATOR: "Team administrator",
	TOURNAMENT_ADMINISTRATOR: "Tournament administrator",
	VIEWER: "Viewer",
};

const EDITABLE_ROLES = ["ADMINISTRATOR", "TEAM_ADMINISTRATOR", "TOURNAMENT_ADMINISTRATOR", "VIEWER"] as const;

export function OrganizationMembersPanel({ organizationId }: { organizationId: string }) {
	const [page, setPage] = useState(0);
	const [size, setSize] = useState(25);
	const [query, setQuery] = useState("");
	const [role, setRole] = useState("");
	const [status, setStatus] = useState("ACTIVE");
	const [sort, setSort] = useState<"NAME_ASC" | "NAME_DESC" | "ROLE_ASC" | "NEWEST" | "OLDEST">("NAME_ASC");
	const [disableTarget, setDisableTarget] = useState<Membership | null>(null);
	const [transferTarget, setTransferTarget] = useState<Membership | null>(null);
	const [inviteEmail, setInviteEmail] = useState("");
	const [inviteError, setInviteError] = useState<string | null>(null);

	const { user } = useAuth();
	const members = useMemberSearch(organizationId, { page, size, q: query, role, status, sort });
	const updateRole = useUpdateOrganizationMemberRole(organizationId);
	const disableMember = useDisableOrganizationMember(organizationId);
	const transferOwnership = useTransferOwnership(organizationId);
	const hasFilters = !!role || status !== "ACTIVE";

	const isViewerOwner = !!members.data?.items.some(
		(member) => member.role === "OWNER" && !!user?.email && member.userEmail?.toLowerCase() === user.email.toLowerCase(),
	);
	const pendingOwnershipInvitation = usePendingOwnershipTransferInvitation(organizationId, isViewerOwner);
	const inviteOwnershipTransfer = useInviteOwnershipTransfer(organizationId);
	const revokeOwnershipInvitation = useRevokeOwnershipTransferInvitation(organizationId);

	const resetPage = () => setPage(0);

	return (
		<section className="flex flex-col gap-4" aria-labelledby="active-members-heading">
			<div>
				<h2 id="active-members-heading" className="font-heading text-xl font-bold text-navy dark:text-[#f8fafc]">
					Active members &amp; staff
				</h2>
				<p className="mt-1 text-sm text-slate-gray dark:text-[#cbd5e1]">
					Manage organization access. Disabling access preserves historical activity and audit records.
				</p>
			</div>

			<ListToolbar
				searchValue={query}
				onSearchChange={(value) => {
					setQuery(value);
					resetPage();
				}}
				searchPlaceholder="Search members by name or email"
				resultCount={members.data?.totalElements}
				sortValue={sort}
				sortOptions={[
					{ value: "NAME_ASC", label: "Name A–Z" },
					{ value: "NAME_DESC", label: "Name Z–A" },
					{ value: "ROLE_ASC", label: "Role" },
					{ value: "NEWEST", label: "Newest" },
					{ value: "OLDEST", label: "Oldest" },
				]}
				onSortChange={(value) => {
					setSort(value as typeof sort);
					resetPage();
				}}
				hasActiveFilters={hasFilters}
				onClear={() => {
					setQuery("");
					setRole("");
					setStatus("ACTIVE");
					setSort("NAME_ASC");
					resetPage();
				}}
				filters={
					<>
						<select
							aria-label="Filter members by role"
							value={role}
							onChange={(event) => {
								setRole(event.target.value);
								resetPage();
							}}
							className="min-h-11 rounded-lg border border-slate-300 bg-white px-3 text-navy dark:border-[#334155] dark:bg-[#0f172a] dark:text-[#f8fafc]"
						>
							<option value="">All roles</option>
							{Object.entries(ROLE_LABELS).map(([value, label]) => (
								<option key={value} value={value}>{label}</option>
							))}
						</select>
						<select
							aria-label="Filter members by status"
							value={status}
							onChange={(event) => {
								setStatus(event.target.value);
								resetPage();
							}}
							className="min-h-11 rounded-lg border border-slate-300 bg-white px-3 text-navy dark:border-[#334155] dark:bg-[#0f172a] dark:text-[#f8fafc]"
						>
							<option value="">All statuses</option>
							<option value="ACTIVE">Active</option>
							<option value="REVOKED">Disabled</option>
							<option value="INVITED">Invited</option>
						</select>
					</>
				}
			/>

			{members.isLoading && <LoadingState label="Loading members…" />}
			{members.isError && <ErrorState message="Could not load organization members." onRetry={() => members.refetch()} />}
			{members.data?.items.length === 0 && (
				<EmptyState
					title={query.trim() || hasFilters ? "No results found" : "No active members yet"}
					description={
						query.trim() || hasFilters
							? "Try changing your search or filters."
							: "Accepted organization members will appear here."
					}
				/>
			)}
			{members.data && members.data.items.length > 0 && (
				<ul className="flex flex-col gap-2" aria-label="Organization members">
					{members.data.items.map((member) => {
						const isOwner = member.role === "OWNER";
						return (
							<li key={member.id} className="rounded-lg border border-slate-gray/20 bg-pure-white p-3 dark:bg-[#111827]">
								<div className="flex flex-wrap items-center justify-between gap-3">
									<div className="min-w-0 flex-1">
										<p className="break-words font-medium text-navy dark:text-[#f8fafc]">
											{member.userDisplayName || member.userEmail || "Organization member"}
										</p>
										{member.userEmail && <p className="break-all text-sm text-slate-gray dark:text-[#cbd5e1]">{member.userEmail}</p>}
										<p className="mt-1 text-xs font-semibold uppercase tracking-wide text-slate-gray dark:text-[#94a3b8]">
											{ROLE_LABELS[member.role] ?? member.role} · {member.status === "REVOKED" ? "Disabled" : member.status.toLowerCase()}
										</p>
									</div>
									<div className="flex shrink-0 flex-wrap items-center gap-2">
										{!isOwner && member.status === "ACTIVE" && (
											<select
												aria-label={`Role for ${member.userDisplayName || member.userEmail || "member"}`}
												value={member.role}
												disabled={updateRole.isPending}
												onChange={(event) => updateRole.mutate({ memberId: member.id, role: event.target.value })}
												className="min-h-10 rounded-md border border-slate-300 bg-white px-2 text-sm text-navy dark:border-[#334155] dark:bg-[#0f172a] dark:text-[#f8fafc]"
											>
												{EDITABLE_ROLES.map((editableRole) => (
													<option key={editableRole} value={editableRole}>{ROLE_LABELS[editableRole]}</option>
												))}
											</select>
										)}
										{isViewerOwner && member.role === "ADMINISTRATOR" && member.status === "ACTIVE" && (
											<Button type="button" variant="secondary" onClick={() => setTransferTarget(member)}>
												Make owner
											</Button>
										)}
										{!isOwner && member.status === "ACTIVE" && (
											<Button type="button" variant="danger" onClick={() => setDisableTarget(member)}>
												Disable access
											</Button>
										)}
									</div>
								</div>
							</li>
						);
					})}
				</ul>
			)}

			{members.data && (
				<Pagination
					page={page}
					size={size}
					totalElements={members.data.totalElements}
					onPageChange={setPage}
					onSizeChange={(value) => {
						setSize(value);
						setPage(0);
					}}
				/>
			)}

			{isViewerOwner && (
				<section className="flex flex-col gap-3 rounded-lg border border-slate-gray/20 bg-ice-white p-4 dark:bg-[#0f172a]" aria-labelledby="ownership-transfer-heading">
					<div>
						<h3 id="ownership-transfer-heading" className="font-heading text-base font-bold text-navy dark:text-[#f8fafc]">
							Transfer ownership
						</h3>
						<p className="mt-1 text-sm text-slate-gray dark:text-[#cbd5e1]">
							Hand this organization to an existing Administrator using &quot;Make owner&quot; above, or invite someone
							new by email. You&rsquo;ll become an Administrator once the transfer completes.
						</p>
					</div>
					{pendingOwnershipInvitation.data ? (
						<div className="flex flex-wrap items-center justify-between gap-3 rounded-md border border-slate-gray/20 bg-pure-white p-3 dark:bg-[#111827]">
							<p className="text-sm text-navy dark:text-[#f8fafc]">
								Invitation sent to <span className="font-medium">{pendingOwnershipInvitation.data.email}</span> — pending.
							</p>
							<Button
								type="button"
								variant="secondary"
								disabled={revokeOwnershipInvitation.isPending}
								onClick={() => revokeOwnershipInvitation.mutate(pendingOwnershipInvitation.data!.id)}
							>
								Revoke
							</Button>
						</div>
					) : (
						<form
							className="flex flex-wrap items-end gap-3"
							noValidate
							aria-label="Invite someone to become the owner"
							onSubmit={async (event) => {
								event.preventDefault();
								setInviteError(null);
								try {
									await inviteOwnershipTransfer.mutateAsync(inviteEmail.trim());
									setInviteEmail("");
								} catch {
									setInviteError("Could not send the invitation. Check the email and try again.");
								}
							}}
						>
							<div className="flex flex-col gap-1">
								<label htmlFor="ownership-invite-email" className="text-sm font-medium text-navy dark:text-[#f8fafc]">
									Invite a new owner by email
								</label>
								<input
									id="ownership-invite-email"
									type="email"
									required
									value={inviteEmail}
									onChange={(event) => setInviteEmail(event.target.value)}
									className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2"
								/>
							</div>
							<Button type="submit" variant="secondary" disabled={inviteOwnershipTransfer.isPending}>
								{inviteOwnershipTransfer.isPending ? "Sending…" : "Send invitation"}
							</Button>
							{inviteError && <p role="alert" className="w-full text-sm text-error-red">{inviteError}</p>}
						</form>
					)}
				</section>
			)}

			<Modal
				open={!!transferTarget}
				onClose={() => setTransferTarget(null)}
				title="Transfer ownership?"
				actions={
					<>
						<Button type="button" variant="secondary" onClick={() => setTransferTarget(null)}>Cancel</Button>
						<Button
							type="button"
							variant="danger"
							disabled={transferOwnership.isPending}
							onClick={async () => {
								if (!transferTarget) return;
								await transferOwnership.mutateAsync(transferTarget.id);
								setTransferTarget(null);
							}}
						>
							{transferOwnership.isPending ? "Transferring…" : "Transfer ownership"}
						</Button>
					</>
				}
			>
				<p className="text-sm text-slate-gray dark:text-[#cbd5e1]">
					{transferTarget?.userDisplayName || transferTarget?.userEmail || "This member"} will become the organization
					owner. You will become an Administrator.
				</p>
			</Modal>

			<Modal
				open={!!disableTarget}
				onClose={() => setDisableTarget(null)}
				title="Disable access?"
				actions={
					<>
						<Button type="button" variant="secondary" onClick={() => setDisableTarget(null)}>Cancel</Button>
						<Button
							type="button"
							variant="danger"
							disabled={disableMember.isPending}
							onClick={async () => {
								if (!disableTarget) return;
								await disableMember.mutateAsync(disableTarget.id);
								setDisableTarget(null);
							}}
						>
							{disableMember.isPending ? "Disabling…" : "Disable access"}
						</Button>
					</>
				}
			>
				<p className="text-sm text-slate-gray dark:text-[#cbd5e1]">
					{disableTarget?.userDisplayName || disableTarget?.userEmail || "This member"} will no longer be able to access this organization.
					Historical activity and audit records will remain.
				</p>
			</Modal>
		</section>
	);
}
