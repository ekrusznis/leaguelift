import { useState } from "react";
import { Button } from "../../components/Button";
import { ErrorState } from "../../components/states/ErrorState";
import { LoadingState } from "../../components/states/LoadingState";
import { usePreviewConnectedSportsData, useSportsDataOverview } from "./api";
import type { SportsDataPreview } from "./types";

export function SportsDataScaffoldPanel({ organizationId, readOnly = false }: { organizationId: string; readOnly?: boolean }) {
	const query = useSportsDataOverview(organizationId);
	const previewConnected = usePreviewConnectedSportsData(organizationId);
	const [preview, setPreview] = useState<SportsDataPreview | null>(null);
	if (query.isLoading) return <LoadingState label="Loading sports-data readiness…" />;
	if (query.isError || !query.data) return <ErrorState message="Could not load sports-data readiness." onRetry={() => query.refetch()} />;

	async function runPreview(connectionId: string) {
		setPreview(await previewConnected.mutateAsync(connectionId));
	}

	return (
		<section className="rounded-xl border border-slate-200 dark:border-[#334155] bg-white dark:bg-[#111827] p-5" aria-labelledby="sports-data-heading">
			<h3 id="sports-data-heading" className="font-heading text-lg font-semibold text-navy-900 dark:text-[#f8fafc]">Sports-data provider readiness</h3>
			<p className="mt-1 text-sm text-slate-500 dark:text-[#cbd5e1]">Provider previews retain external identity and never commit records until an official contract and reviewed import are enabled.</p>
			<div className="mt-4 grid gap-3 md:grid-cols-2">
				{(query.data.providers ?? []).map((item) => {
					const connected =
						(item.provider === "SPORTSENGINE" || item.provider === "TEAMSNAP") &&
						["CONNECTED", "DEGRADED"].includes(item.connection?.status ?? "");
					return (
						<article key={item.provider} className="rounded-lg border border-slate-200 dark:border-[#334155] p-4">
							<div className="flex items-start justify-between gap-2"><h4 className="font-semibold text-navy-900 dark:text-[#f8fafc]">{item.displayName}</h4><span className="rounded-full bg-slate-100 dark:bg-slate-800 px-2 py-1 text-[11px] font-semibold text-slate-600 dark:text-[#cbd5e1]">{(item.connection?.status ?? item.readiness).replaceAll("_", " ")}</span></div>
							<p className="mt-2 text-sm text-slate-600 dark:text-[#cbd5e1]">{item.description}</p>
							<p className="mt-2 text-xs text-slate-500 dark:text-[#cbd5e1]">{item.activationRequirement}</p>
							{!readOnly && connected && item.connection && <Button type="button" variant="secondary" className="mt-3" onClick={() => void runPreview(item.connection!.id)} disabled={previewConnected.isPending}>{previewConnected.isPending ? "Reviewing…" : "Preview provider records"}</Button>}
						</article>
					);
				})}
			</div>
			{previewConnected.isError && <p role="alert" className="mt-3 text-sm text-error-red">Could not create the provider review preview.</p>}
			{preview && <div role="status" className="mt-4 rounded-lg border border-slate-200 dark:border-[#334155] bg-ice-50 dark:bg-[#0f172a] p-4"><p className="font-medium text-navy-900 dark:text-[#f8fafc]">Review-only provider preview</p><p className="mt-1 text-sm text-slate-600 dark:text-[#cbd5e1]">{preview.message}</p><p className="mt-2 text-sm text-slate-600 dark:text-[#cbd5e1]">{preview.run.validCount}/{preview.run.discoveredCount} valid · {preview.run.duplicateCount} duplicate · {preview.run.conflictCount} conflicts · {preview.run.errorCount} errors</p><p className="mt-1 text-xs font-medium text-slate-500 dark:text-[#cbd5e1]">Direct import: disabled</p></div>}
			{query.data.recentRuns.length > 0 && <div className="mt-4"><h4 className="font-medium text-navy-900 dark:text-[#f8fafc]">Recent mapping previews</h4><ul className="mt-2 space-y-2">{query.data.recentRuns.slice(0, 5).map((run) => <li key={run.id} className="flex flex-wrap justify-between gap-2 rounded-md border border-slate-200 dark:border-[#334155] px-3 py-2 text-sm"><span>{run.provider} · {run.sourceMode.replaceAll("_", " ")}</span><span className="text-slate-500 dark:text-[#cbd5e1]">{run.validCount}/{run.discoveredCount} valid · {run.status}</span></li>)}</ul></div>}
			<p className="mt-4 text-xs text-slate-500 dark:text-[#cbd5e1]">SportsEngine and TeamSnap remain Not configured until Rally26 has a registered and verified developer application for each. GameChanger has no public API — use the existing CSV event import below with a CSV exported from GameChanger. MaxPreps has no public API or partner program and is not offered as a connection.</p>
		</section>
	);
}
