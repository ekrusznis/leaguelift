import { EmptyState } from "../../components/states/EmptyState";
import { ErrorState } from "../../components/states/ErrorState";
import { LoadingState } from "../../components/states/LoadingState";
import { useTeamStaff } from "./searchApi";

export function TeamStaffList({
	organizationId,
	teamId,
}: {
	organizationId: string;
	teamId: string;
}) {
	const query = useTeamStaff(organizationId, teamId);

	return (
		<section aria-labelledby="team-staff-heading" className="flex flex-col gap-3">
			<div>
				<h2 id="team-staff-heading" className="font-heading text-lg font-bold text-navy dark:text-[#f8fafc]">
					Coaches &amp; Staff
				</h2>
				<p className="mt-1 text-sm text-slate-gray dark:text-[#cbd5e1]">
					Team-assigned staff. Private email and phone information is not shown here.
				</p>
			</div>

			{query.isLoading && <LoadingState label="Loading team staff…" />}
			{query.isError && <ErrorState message="Could not load team staff." onRetry={() => query.refetch()} />}
			{query.data?.length === 0 && (
				<EmptyState
					compact
					title="No coaches or team staff yet"
					description="Assigned coaches and team staff will appear here."
				/>
			)}
			{query.data && query.data.length > 0 && (
				<ul className="grid gap-2 sm:grid-cols-2" aria-label="Coaches and team staff">
					{query.data.map((staff) => (
						<li key={staff.userId} className="rounded-lg border border-slate-gray/20 bg-pure-white p-3 dark:bg-[#111827]">
							<p className="font-medium text-navy dark:text-[#f8fafc]">{staff.displayName}</p>
							<p className="text-sm text-slate-gray dark:text-[#cbd5e1]">{staff.roleLabel}</p>
						</li>
					))}
				</ul>
			)}
		</section>
	);
}
