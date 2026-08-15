import { zodResolver } from "@hookform/resolvers/zod";
import { useForm } from "react-hook-form";
import { Button } from "../../components/Button";
import { EmptyState } from "../../components/states/EmptyState";
import { ErrorState } from "../../components/states/ErrorState";
import { LoadingState } from "../../components/states/LoadingState";
import { OrganizationMembersPanel } from "../members/OrganizationMembersPanel";
import { useCreateInvitation, useInvitations, useRevokeInvitation } from "./api";
import { createInvitationSchema, type CreateInvitationFormValues } from "./schema";
import { INVITABLE_ROLES, type InvitableRole } from "./types";

const ROLE_LABELS: Record<InvitableRole, string> = {
	ADMINISTRATOR: "Administrator",
	TEAM_ADMINISTRATOR: "Team administrator",
	TOURNAMENT_ADMINISTRATOR: "Tournament administrator",
	VIEWER: "Viewer",
};

export function InvitationsPanel({ organizationId }: { organizationId: string }) {
	const { data, isLoading, isError, refetch } = useInvitations(organizationId);
	const createInvitation = useCreateInvitation(organizationId);
	const revokeInvitation = useRevokeInvitation(organizationId);
	const {
		register,
		handleSubmit,
		reset,
		formState: { errors, isSubmitting },
	} = useForm<CreateInvitationFormValues>({
		resolver: zodResolver(createInvitationSchema),
		defaultValues: { email: "", role: "ADMINISTRATOR" },
	});
	const onSubmit = handleSubmit(async (values) => {
		await createInvitation.mutateAsync(values);
		reset();
	});

	return (
		<div className="flex flex-col gap-8">
			<OrganizationMembersPanel organizationId={organizationId} />

			<section className="flex flex-col gap-4" aria-labelledby="pending-invitations-heading">
				<div>
					<h2 id="pending-invitations-heading" className="font-heading text-xl font-bold text-navy dark:text-[#f8fafc]">
						Pending invitations
					</h2>
					<p className="mt-1 text-sm text-slate-gray dark:text-[#cbd5e1]">
						Invite another adult to join the organization.
					</p>
				</div>
				<form onSubmit={onSubmit} className="flex flex-wrap items-end gap-3" noValidate aria-label="Invite an administrator">
					<div className="flex flex-col gap-1">
						<label htmlFor="invite-email" className="text-sm font-medium text-navy dark:text-[#f8fafc]">Email</label>
						<input
							id="invite-email"
							type="email"
							{...register("email")}
							aria-invalid={!!errors.email}
							aria-describedby={errors.email ? "invite-email-error" : undefined}
							className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2"
						/>
						{errors.email && <p id="invite-email-error" role="alert" className="text-sm text-error-red">{errors.email.message}</p>}
					</div>
					<div className="flex flex-col gap-1">
						<label htmlFor="invite-role" className="text-sm font-medium text-navy dark:text-[#f8fafc]">Role</label>
						<select id="invite-role" {...register("role")} className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2">
							{INVITABLE_ROLES.map((inviteRole) => (
								<option key={inviteRole} value={inviteRole}>{ROLE_LABELS[inviteRole]}</option>
							))}
						</select>
					</div>
					<Button type="submit" disabled={isSubmitting}>{isSubmitting ? "Sending…" : "Send invitation"}</Button>
				</form>

				{isLoading && <LoadingState label="Loading invitations…" />}
				{isError && <ErrorState message="Could not load invitations." onRetry={() => refetch()} />}
				{data && data.items.length === 0 && (
					<EmptyState title="No pending invitations" description="There are no invitations waiting to be accepted." />
				)}
				{data && data.items.length > 0 && (
					<ul className="flex flex-col gap-2" aria-label="Pending invitations">
						{data.items.map((invitation) => (
							<li key={invitation.id} className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-slate-gray/20 bg-pure-white p-3 dark:bg-[#111827]">
								<div className="min-w-0 flex-1">
									<p className="break-words font-medium text-navy dark:text-[#f8fafc]">{invitation.email}</p>
									<p className="text-sm text-slate-gray dark:text-[#cbd5e1]">{ROLE_LABELS[invitation.role]}</p>
								</div>
								<Button
									type="button"
									variant="secondary"
									className="shrink-0"
									onClick={() => revokeInvitation.mutate(invitation.id)}
									disabled={revokeInvitation.isPending}
								>
									Revoke
								</Button>
							</li>
						))}
					</ul>
				)}
			</section>
		</div>
	);
}
