import { ErrorState } from "../../components/states/ErrorState";
import { LoadingState } from "../../components/states/LoadingState";
import { usePlatformOutboxHealth, usePlatformWebhookHealth } from "../../dashboard/api";
import { usePlatformIntegrationReadiness, usePlatformIntegrationSyncRuns, usePlatformProviderContracts } from "../integrations/api";
import type { PlatformIntegrationReadiness, PlatformProviderContract } from "../integrations/types";
import { useDeadLetterOutboxEvents, useFailedOutboxEvents, useReprocessOutboxEvent } from "./api";
import type { OutboxEvent } from "./types";

export function PlatformOperationsPage() {
	const webhook = usePlatformWebhookHealth();
	const outbox = usePlatformOutboxHealth();
	const failed = useFailedOutboxEvents();
	const deadLetter = useDeadLetterOutboxEvents();
	const providers = usePlatformIntegrationReadiness();
	const contracts = usePlatformProviderContracts();
	const syncRuns = usePlatformIntegrationSyncRuns();
	const reprocess = useReprocessOutboxEvent();

	if (webhook.isLoading || outbox.isLoading || failed.isLoading || deadLetter.isLoading || providers.isLoading || contracts.isLoading || syncRuns.isLoading) return <LoadingState label="Loading platform operations…" />;
	if (webhook.isError || outbox.isError || failed.isError || deadLetter.isError || providers.isError || contracts.isError || syncRuns.isError || !webhook.data || !outbox.data || !failed.data || !deadLetter.data || !providers.data || !contracts.data || !syncRuns.data) {
		return <ErrorState message="Could not load integration operations." onRetry={() => { void webhook.refetch(); void outbox.refetch(); void failed.refetch(); void deadLetter.refetch(); void providers.refetch(); void contracts.refetch(); void syncRuns.refetch(); }} />;
	}

	const events = [...deadLetter.data, ...failed.data].sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime());

	return (
		<div className="flex flex-col gap-6">
			<div><h1 className="font-heading text-2xl font-bold text-navy-900 dark:text-[#f8fafc]">Integration Operations</h1><p className="mt-1 text-slate-500 dark:text-[#cbd5e1]">Platform-wide provider health, asynchronous failures, and controlled retry operations.</p></div>
			<div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
				<Metric label="Webhooks processed" value={webhook.data.processed} />
				<Metric label="Webhook failures" value={webhook.data.failed} tone={webhook.data.failed > 0 ? "error" : "normal"} />
				<Metric label="Outbox pending" value={outbox.data.pending + outbox.data.processing} />
				<Metric label="Failed / dead-letter" value={outbox.data.failed + outbox.data.deadLetter} tone={outbox.data.failed + outbox.data.deadLetter > 0 ? "error" : "normal"} />
			</div>
			<section className="rounded-xl border border-slate-200 dark:border-[#334155] bg-white dark:bg-[#111827] p-5">
				<div><h2 className="font-heading text-lg font-semibold text-navy-900 dark:text-[#f8fafc]">Platform provider readiness</h2><p className="mt-1 text-sm text-slate-500 dark:text-[#cbd5e1]">Sanitized configuration checks only. A configured result is not a live-provider health claim; credentialed probes and contract verification remain Phase 20.</p></div>
				<div className="mt-4 grid gap-3 md:grid-cols-2 xl:grid-cols-3">
					{providers.data.map((provider) => <ProviderReadinessCard key={provider.provider} provider={provider} />)}
				</div>
			</section>
			<section className="rounded-xl border border-slate-200 dark:border-[#334155] bg-white dark:bg-[#111827] p-5">
				<div><h2 className="font-heading text-lg font-semibold text-navy-900 dark:text-[#f8fafc]">Provider contract hardening</h2><p className="mt-1 text-sm text-slate-500 dark:text-[#cbd5e1]">Code and configuration readiness only. Live probes remain disabled until Phase 20 credentialed verification.</p></div>
				<div className="mt-4 grid gap-3 lg:grid-cols-2">{contracts.data.map((contract) => <ProviderContractCard key={contract.provider} contract={contract} />)}</div>
			</section>
			<section className="rounded-xl border border-slate-200 dark:border-[#334155] bg-white dark:bg-[#111827] p-5">
				<div><h2 className="font-heading text-lg font-semibold text-navy-900 dark:text-[#f8fafc]">Recent provider runs</h2><p className="mt-1 text-sm text-slate-500 dark:text-[#cbd5e1]">Durable read/write/webhook/health runs with redacted errors and immutable counts.</p></div>
				{syncRuns.data.length === 0 ? <p className="mt-4 text-sm text-slate-500 dark:text-[#cbd5e1]">No integration sync runs recorded.</p> : <ul className="mt-4 space-y-2">{syncRuns.data.slice(0, 10).map((run) => <li key={run.id} className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-slate-200 dark:border-[#334155] px-3 py-2 text-sm"><span className="font-medium text-navy-900 dark:text-[#f8fafc]">{run.provider.replaceAll("_", " ")} · {run.direction}</span><span className="text-slate-500 dark:text-[#cbd5e1]">{run.status} · {run.createdCount} created · {run.failedCount} failed</span></li>)}</ul>}
			</section>
			<section className="rounded-xl border border-slate-200 dark:border-[#334155] bg-white dark:bg-[#111827]">
				<div className="border-b border-slate-200 dark:border-[#334155] px-5 py-4"><h2 className="font-heading text-lg font-semibold text-navy-900 dark:text-[#f8fafc]">Outbox exception queue</h2><p className="mt-1 text-sm text-slate-500 dark:text-[#cbd5e1]">Retry resets an event to pending. Payloads cannot be edited from the console.</p></div>
				{events.length === 0 ? (
					<p className="p-6 text-sm text-slate-500 dark:text-[#cbd5e1]">No failed or dead-letter events.</p>
				) : (
					<div className="overflow-x-auto">
						<table className="w-full min-w-[980px] text-left text-sm">
							<thead className="border-b border-slate-200 dark:border-[#334155] bg-ice-50 dark:bg-[#0f172a] text-slate-500 dark:text-[#cbd5e1]"><tr><th className="px-4 py-3 font-medium">Event</th><th className="px-4 py-3 font-medium">Status</th><th className="px-4 py-3 font-medium">Organization</th><th className="px-4 py-3 font-medium">Attempts</th><th className="px-4 py-3 font-medium">Last error</th><th className="px-4 py-3 font-medium"><span className="sr-only">Actions</span></th></tr></thead>
							<tbody className="divide-y divide-slate-100 dark:divide-[#334155]">
								{events.map((event) => <OutboxRow key={event.id} event={event} pending={reprocess.isPending && reprocess.variables === event.id} onReprocess={() => reprocess.mutate(event.id)} />)}
							</tbody>
						</table>
					</div>
				)}
			</section>
		</div>
	);
}

function Metric({ label, value, tone = "normal" }: { label: string; value: number; tone?: "normal" | "error" }) {
	return <div className={`rounded-xl border bg-white dark:bg-[#111827] p-4 ${tone === "error" ? "border-error-200" : "border-slate-200 dark:border-[#334155]"}`}><p className="text-xs font-medium uppercase tracking-wide text-slate-500 dark:text-[#cbd5e1]">{label}</p><p className={`mt-2 font-heading text-2xl font-bold ${tone === "error" ? "text-error-600" : "text-navy-900 dark:text-[#f8fafc]"}`}>{value}</p></div>;
}

function OutboxRow({ event, pending, onReprocess }: { event: OutboxEvent; pending: boolean; onReprocess: () => void }) {
	return (
		<tr>
			<td className="px-4 py-3"><p className="font-semibold text-navy-900 dark:text-[#f8fafc]">{event.eventType}</p><p className="font-mono text-[11px] text-slate-400">{event.id}</p></td>
			<td className="px-4 py-3"><span className={`rounded-full px-2.5 py-1 text-xs font-semibold ${event.status === "DEAD_LETTER" ? "bg-error-100 text-error-700" : "bg-amber-100 text-amber-900"}`}>{event.status}</span></td>
			<td className="px-4 py-3 font-mono text-xs text-slate-500 dark:text-[#cbd5e1]">{event.organizationId ?? "Platform"}</td>
			<td className="px-4 py-3 text-slate-600 dark:text-[#cbd5e1]">{event.attemptCount}</td>
			<td className="max-w-md px-4 py-3 text-slate-600 dark:text-[#cbd5e1]"><p className="line-clamp-3">{event.lastError ?? "No error message recorded"}</p></td>
			<td className="px-4 py-3 text-right"><button type="button" disabled={pending} onClick={onReprocess} className="rounded-md border border-slate-300 dark:border-[#334155] px-3 py-2 font-medium text-navy-900 dark:text-[#f8fafc] hover:border-green-500 disabled:opacity-50">{pending ? "Retrying…" : "Reprocess"}</button></td>
		</tr>
	);
}

function ProviderReadinessCard({ provider }: { provider: PlatformIntegrationReadiness }) {
	const tone = provider.status === "CONFIGURED" || provider.status === "BUILT_IN" ? "bg-green-100 text-green-800" : provider.status === "PARTIAL" ? "bg-amber-100 text-amber-900" : "bg-slate-100 dark:bg-slate-800 text-slate-600 dark:text-[#cbd5e1]";
	return (
		<article className="rounded-lg border border-slate-200 dark:border-[#334155] p-4">
			<div className="flex items-start justify-between gap-3"><div><h3 className="font-semibold text-navy-900 dark:text-[#f8fafc]">{provider.displayName}</h3><p className="mt-0.5 text-xs text-slate-500 dark:text-[#cbd5e1]">{provider.mode}</p></div><span className={`rounded-full px-2 py-1 text-[11px] font-semibold ${tone}`}>{provider.status.replaceAll("_", " ")}</span></div>
			<p className="mt-3 text-sm text-slate-600 dark:text-[#cbd5e1]">{provider.summary}</p>
			<ul className="mt-3 flex flex-col gap-1 text-xs text-slate-500 dark:text-[#cbd5e1]">{provider.checks.map((check) => <li key={check.label} className="flex items-center justify-between gap-2"><span>{check.label}</span><span className={check.configured ? "font-medium text-green-700" : "text-slate-400"}>{check.configured ? "Configured" : "Missing"}</span></li>)}</ul>
		</article>
	);
}


function ProviderContractCard({ contract }: { contract: PlatformProviderContract }) {
	return <article className="rounded-lg border border-slate-200 dark:border-[#334155] p-4"><div className="flex items-start justify-between gap-3"><h3 className="font-semibold text-navy-900 dark:text-[#f8fafc]">{contract.provider.replaceAll("_", " ")}</h3><span className="rounded-full bg-slate-100 dark:bg-slate-800 px-2 py-1 text-[11px] font-semibold text-slate-600 dark:text-[#cbd5e1]">{contract.configurationStatus.replaceAll("_", " ")}</span></div><dl className="mt-3 space-y-2 text-xs"><div><dt className="font-medium text-slate-500 dark:text-[#cbd5e1]">Credential ownership</dt><dd className="text-slate-700 dark:text-[#cbd5e1]">{contract.credentialOwnership}</dd></div><div><dt className="font-medium text-slate-500 dark:text-[#cbd5e1]">Webhook verification</dt><dd className="text-slate-700 dark:text-[#cbd5e1]">{contract.webhookVerification}</dd></div><div><dt className="font-medium text-slate-500 dark:text-[#cbd5e1]">Health mode</dt><dd className="text-slate-700 dark:text-[#cbd5e1]">{contract.healthCheckMode}</dd></div></dl><ul className="mt-3 space-y-2">{contract.capabilities.map((capability) => <li key={capability.name} className="rounded-md bg-ice-50 dark:bg-[#0f172a] p-2 text-xs"><div className="flex justify-between gap-2"><span className="font-medium text-navy-900 dark:text-[#f8fafc]">{capability.name}</span><span className="text-slate-500 dark:text-[#cbd5e1]">{capability.status}</span></div><p className="mt-1 text-slate-600 dark:text-[#cbd5e1]">{capability.summary}</p></li>)}</ul></article>;
}
