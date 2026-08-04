import { useState } from "react";
import { Button } from "../../components/Button";
import { ErrorState } from "../../components/states/ErrorState";
import { LoadingState } from "../../components/states/LoadingState";
import { usePreviewConnectedSportsData, useSportsDataOverview } from "./api";
import type { SportsDataPreview } from "./types";

export function SportsDataScaffoldPanel({ organizationId }: { organizationId: string }) {
	const query = useSportsDataOverview(organizationId);
	const previewConnected = usePreviewConnectedSportsData(organizationId);
	const [preview, setPreview] = useState<SportsDataPreview | null>(null);
	if (query.isLoading) return <LoadingState label="Loading sports-data readiness…" />;
	if (query.isError || !query.data) return <ErrorState message="Could not load sports-data readiness." onRetry={() => query.refetch()} />;

	async function runPreview(connectionId: string) {
		setPreview(await previewConnected.mutateAsync(connectionId));
	}

	return (
		<section className="rounded-xl border border-slate-200 bg-white p-5" aria-labelledby="sports-data-heading">
			<h3 id="sports-data-heading" className="font-heading text-lg font-semibold text-navy-900">Sports-data provider readiness</h3>
			<p className="mt-1 text-sm text-slate-500">Provider previews retain external identity and never commit records until an official contract and reviewed import are enabled.</p>
			<div className="mt-4 grid gap-3 md:grid-cols-3">
				{query.data.providers.map((item) => {
					const connected = item.provider === "SPORTSENGINE" && ["CONNECTED", "DEGRADED"].includes(item.connection?.status ?? "");
					return (
						<article key={item.provider} className="rounded-lg border border-slate-200 p-4">
							<div className="flex items-start justify-between gap-2"><h4 className="font-semibold text-navy-900">{item.displayName}</h4><span className="rounded-full bg-slate-100 px-2 py-1 text-[11px] font-semibold text-slate-600">{(item.connection?.status ?? item.readiness).replaceAll("_", " ")}</span></div>
							<p className="mt-2 text-sm text-slate-600">{item.description}</p>
							<p className="mt-2 text-xs text-slate-500">{item.activationRequirement}</p>
							{connected && item.connection && <Button type="button" variant="secondary" className="mt-3" onClick={() => void runPreview(item.connection!.id)} disabled={previewConnected.isPending}>{previewConnected.isPending ? "Reviewing…" : "Preview provider records"}</Button>}
						</article>
					);
				})}
			</div>
			{previewConnected.isError && <p role="alert" className="mt-3 text-sm text-error-red">Could not create the provider review preview.</p>}
			{preview && <div role="status" className="mt-4 rounded-lg border border-slate-200 bg-ice-50 p-4"><p className="font-medium text-navy-900">Review-only provider preview</p><p className="mt-1 text-sm text-slate-600">{preview.message}</p><p className="mt-2 text-sm text-slate-600">{preview.run.validCount}/{preview.run.discoveredCount} valid · {preview.run.duplicateCount} duplicate · {preview.run.conflictCount} conflicts · {preview.run.errorCount} errors</p><p className="mt-1 text-xs font-medium text-slate-500">Direct import: disabled</p></div>}
			{query.data.recentRuns.length > 0 && <div className="mt-4"><h4 className="font-medium text-navy-900">Recent mapping previews</h4><ul className="mt-2 space-y-2">{query.data.recentRuns.slice(0, 5).map((run) => <li key={run.id} className="flex flex-wrap justify-between gap-2 rounded-md border border-slate-200 px-3 py-2 text-sm"><span>{run.provider} · {run.sourceMode.replaceAll("_", " ")}</span><span className="text-slate-500">{run.validCount}/{run.discoveredCount} valid · {run.status}</span></li>)}</ul></div>}
			<p className="mt-4 text-xs text-slate-500">GameChanger and MaxPreps remain partner-pending. Existing CSV and ICS workflows below are the supported paths today; Rally26 does not scrape either consumer site or invent an unsupported direct client.</p>
		</section>
	);
}
