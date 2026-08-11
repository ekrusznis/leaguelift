import { ErrorState } from "../../components/states/ErrorState";
import { LoadingState } from "../../components/states/LoadingState";
import { useOrganizationIntegrationSyncRuns } from "./api";

export function IntegrationSyncHistory({ organizationId }: { organizationId: string }) {
	const query = useOrganizationIntegrationSyncRuns(organizationId);
	if (query.isLoading) return <LoadingState label="Loading integration history…" />;
	if (query.isError) return <ErrorState message="Could not load integration history." onRetry={() => query.refetch()} />;
	const runs = query.data ?? [];
	return (
		<section className="rounded-xl border border-slate-200 dark:border-[#334155] bg-white dark:bg-[#111827] p-5">
			<h3 className="font-heading text-lg font-semibold text-navy-900 dark:text-[#f8fafc]">Integration sync history</h3>
			<p className="mt-1 text-sm text-slate-500 dark:text-[#cbd5e1]">Durable provider runs retain counts and redacted errors. History is never rewritten by a retry.</p>
			{runs.length === 0 ? <p className="mt-4 text-sm text-slate-500 dark:text-[#cbd5e1]">No provider sync runs have been recorded yet.</p> : <div className="mt-4 overflow-x-auto"><table className="w-full min-w-[760px] text-left text-sm"><thead className="border-b border-slate-200 dark:border-[#334155] text-slate-500 dark:text-[#cbd5e1]"><tr><th className="py-2 pr-3 font-medium">Provider</th><th className="py-2 pr-3 font-medium">Direction</th><th className="py-2 pr-3 font-medium">Status</th><th className="py-2 pr-3 font-medium">Counts</th><th className="py-2 font-medium">Requested</th></tr></thead><tbody className="divide-y divide-slate-100 dark:divide-[#334155]">{runs.map((run) => <tr key={run.id}><td className="py-3 pr-3 font-medium text-navy-900 dark:text-[#f8fafc]">{run.provider.replaceAll("_", " ")}</td><td className="py-3 pr-3 text-slate-600 dark:text-[#cbd5e1]">{run.direction}</td><td className="py-3 pr-3 text-slate-600 dark:text-[#cbd5e1]">{run.status}</td><td className="py-3 pr-3 text-slate-600 dark:text-[#cbd5e1]">{run.createdCount} created · {run.updatedCount} updated · {run.failedCount} failed</td><td className="py-3 text-slate-500 dark:text-[#cbd5e1]">{new Date(run.requestedAt).toLocaleString()}</td></tr>)}</tbody></table></div>}
		</section>
	);
}
