import { Link } from "react-router-dom";
import { ErrorState } from "../../components/states/ErrorState";
import { LoadingState } from "../../components/states/LoadingState";
import { usePlatformOrganizations } from "../../dashboard/api";
import { appPaths } from "../../routes/appPaths";

export function PlatformOrganizationsPage() {
	const organizations = usePlatformOrganizations();

	if (organizations.isLoading) return <LoadingState label="Loading organizations…" />;
	if (organizations.isError || !organizations.data) {
		return <ErrorState message="Could not load platform organizations." onRetry={() => organizations.refetch()} />;
	}

	return (
		<div className="flex flex-col gap-6">
			<div>
				<Link to={appPaths.dashboard()} className="mb-2 inline-block text-sm text-azure-blue hover:underline">← Back to platform dashboard</Link>
				<h1 className="font-heading text-2xl font-bold text-navy">Organizations</h1>
				<p className="mt-1 text-slate-gray">Platform-wide customer directory. Phase 14 management actions remain intentionally separate from this read-only view.</p>
			</div>

			{organizations.data.length === 0 ? (
				<p className="rounded-xl border border-slate-gray/20 bg-pure-white p-5 text-sm text-slate-gray">No organizations have been created.</p>
			) : (
				<div className="overflow-x-auto rounded-xl border border-slate-gray/20 bg-pure-white">
					<table className="w-full min-w-[680px] text-left text-sm">
						<thead className="border-b border-slate-gray/20 bg-ice-white text-slate-gray">
							<tr>
								<th className="px-4 py-3 font-medium">Organization</th>
								<th className="px-4 py-3 font-medium">Type</th>
								<th className="px-4 py-3 font-medium">Status</th>
								<th className="px-4 py-3 font-medium">ID</th>
							</tr>
						</thead>
						<tbody className="divide-y divide-slate-gray/10">
							{organizations.data.map((organization) => (
								<tr key={organization.organizationId}>
									<td className="px-4 py-3"><p className="font-medium text-navy">{organization.name}</p><p className="text-xs text-slate-gray">/{organization.slug}</p></td>
									<td className="px-4 py-3 text-slate-gray">{organization.organizationType}</td>
									<td className="px-4 py-3"><span className="rounded-full bg-ice-white px-2.5 py-1 text-xs font-medium text-navy">{organization.status}</span></td>
									<td className="px-4 py-3 font-mono text-xs text-slate-gray">{organization.organizationId}</td>
								</tr>
							))}
						</tbody>
					</table>
				</div>
			)}
		</div>
	);
}
