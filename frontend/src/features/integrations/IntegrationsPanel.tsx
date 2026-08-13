import { useState } from "react";
import { useSearchParams } from "react-router-dom";
import { zodResolver } from "@hookform/resolvers/zod";
import { useForm } from "react-hook-form";
import { Button } from "../../components/Button";
import { ErrorState } from "../../components/states/ErrorState";
import { LoadingState } from "../../components/states/LoadingState";
import { useTeams } from "../teams/api";
import { CsvImportSection } from "./CsvImportSection";
import { IntegrationSyncHistory } from "./IntegrationSyncHistory";
import { QuickBooksScaffoldPanel } from "./QuickBooksScaffoldPanel";
import { SportsDataScaffoldPanel } from "./SportsDataScaffoldPanel";
import {
	useCheckOrganizationIntegrationHealth,
	useConnectIcsFeed,
	useDisconnectEventSource,
	useDisconnectOrganizationIntegration,
	useEventSourceConnections,
	useOrganizationIntegrationCatalog,
	useRefreshOrganizationIntegration,
	useStartOrganizationIntegrationAuthorization,
} from "./api";
import { connectIcsFeedSchema, type ConnectIcsFeedFormValues } from "./schema";
import type { IntegrationCatalogItem, IntegrationConnectionStatus, IntegrationReadiness } from "./types";

const DEDICATED_WORKFLOW_PROVIDERS = new Set(["ICS_FEED", "CSV_IMPORT"]);

function readinessLabel(readiness: IntegrationReadiness) {
	switch (readiness) {
		case "AVAILABLE":
			return "Available";
		case "NOT_CONFIGURED":
			return "Not configured";
		case "PARTNER_PENDING":
			return "Partner access required";
		case "PLATFORM_MANAGED":
			return "Managed by Rally26";
		case "UNSUPPORTED":
			return "Unsupported";
	}
}

function connectionLabel(status: IntegrationConnectionStatus) {
	switch (status) {
		case "CONNECTED":
			return "Connected";
		case "AUTHORIZATION_PENDING":
			return "Authorization pending";
		case "DEGRADED":
			return "Needs attention";
		case "REVOKED":
			return "Revoked";
		case "DISCONNECTED":
			return "Disconnected";
		case "AVAILABLE":
			return "Available";
		case "NOT_CONFIGURED":
			return "Not configured";
		case "UNSUPPORTED":
			return "Unsupported";
	}
}

function statusClasses(value: string) {
	if (value === "CONNECTED" || value === "AVAILABLE") return "bg-green-500/15 text-victory-green";
	if (value === "DEGRADED") return "bg-amber-500/15 text-amber-800";
	return "bg-slate-gray/10 text-slate-gray dark:text-[#cbd5e1]";
}

function ProviderReadinessCard({ organizationId, item, readOnly }: { organizationId: string; item: IntegrationCatalogItem; readOnly: boolean }) {
	const startAuthorization = useStartOrganizationIntegrationAuthorization(organizationId);
	const refresh = useRefreshOrganizationIntegration(organizationId);
	const health = useCheckOrganizationIntegrationHealth(organizationId);
	const disconnect = useDisconnectOrganizationIntegration(organizationId);
	const statusValue = item.connection?.status ?? item.readiness;
	const label = item.connection ? connectionLabel(item.connection.status) : readinessLabel(item.readiness);
	const canConnect = item.authMode === "OAUTH2" && item.readiness === "AVAILABLE" && item.connection?.status !== "CONNECTED";
	const connected = item.connection?.status === "CONNECTED" || item.connection?.status === "DEGRADED";
	const busy = startAuthorization.isPending || refresh.isPending || health.isPending || disconnect.isPending;

	async function connect() {
		const result = await startAuthorization.mutateAsync(item.provider);
		window.location.assign(result.authorizationUrl);
	}

	return (
		<li className="rounded-lg border border-slate-gray/20 bg-pure-white dark:bg-[#111827] p-4">
			<div className="flex flex-wrap items-start justify-between gap-3">
				<div className="min-w-0 flex-1">
					<div className="flex flex-wrap items-center gap-2">
						<p className="font-medium text-navy dark:text-[#f8fafc]">{item.displayName}</p>
						{item.stub && <span className="rounded-full bg-sky-500/10 px-2 py-0.5 text-xs font-medium text-sky-800">Local stub</span>}
					</div>
					<p className="mt-1 text-sm text-slate-gray dark:text-[#cbd5e1]">{item.description}</p>
					<p className="mt-2 text-xs text-slate-gray dark:text-[#cbd5e1]">{item.activationRequirement}</p>
					{item.connection?.externalAccountName && <p className="mt-2 text-xs font-medium text-navy dark:text-[#f8fafc]">Account: {item.connection.externalAccountName}</p>}
					{item.connection?.lastErrorMessage && <p role="status" className="mt-2 text-xs text-error-red">{item.connection.lastErrorMessage}</p>}
				</div>
				<span className={`shrink-0 rounded-full px-2 py-0.5 text-xs font-medium ${statusClasses(statusValue)}`}>{label}</span>
			</div>
			{!readOnly && (canConnect || connected) && (
				<div className="mt-3 flex flex-wrap gap-2">
					{canConnect && <Button type="button" onClick={() => void connect()} disabled={busy}>{item.connection?.status === "DEGRADED" ? "Reauthorize" : "Connect"}</Button>}
					{connected && item.connection && <>
						<Button type="button" variant="secondary" onClick={() => health.mutate(item.connection!.id)} disabled={busy}>Check connection</Button>
						<Button type="button" variant="secondary" onClick={() => refresh.mutate(item.connection!.id)} disabled={busy}>Refresh authorization</Button>
						<Button type="button" variant="secondary" onClick={() => disconnect.mutate(item.connection!.id)} disabled={busy}>Disconnect</Button>
					</>}
				</div>
			)}
		</li>
	);
}

function ProviderReadinessSection({ organizationId, readOnly }: { organizationId: string; readOnly: boolean }) {
	const { data, isLoading, isError, refetch } = useOrganizationIntegrationCatalog(organizationId);
	if (isLoading) return <LoadingState label="Loading integration readiness…" />;
	if (isError) return <ErrorState message="Could not load integration readiness." onRetry={() => refetch()} />;
	const items = (data ?? []).filter((item) => !DEDICATED_WORKFLOW_PROVIDERS.has(item.provider));
	return (
		<div>
			<h3 className="font-heading text-base font-semibold text-navy dark:text-[#f8fafc]">Organization Connections</h3>
			<p className="text-sm text-slate-gray dark:text-[#cbd5e1]">
				These providers are scaffolded but remain disabled until Rally26 verifies official access, credentials, scopes, and contracts.
			</p>
			<ul className="mt-3 flex flex-col gap-2" aria-label="Organization integration readiness">
				{items.map((item) => <ProviderReadinessCard key={item.provider} organizationId={organizationId} item={item} readOnly={readOnly} />)}
			</ul>
		</div>
	);
}

function ConnectIcsFeedForm({ organizationId, onDone }: { organizationId: string; onDone: () => void }) {
	const connect = useConnectIcsFeed(organizationId);
	const { data: teams } = useTeams(organizationId);
	const [teamId, setTeamId] = useState("");
	const {
		register,
		handleSubmit,
		reset,
		formState: { errors, isSubmitting },
	} = useForm<ConnectIcsFeedFormValues>({
		resolver: zodResolver(connectIcsFeedSchema),
		defaultValues: { label: "", feedUrl: "", timezone: "" },
	});

	const onSubmit = handleSubmit(async (values) => {
		await connect.mutateAsync({ ...values, teamId: teamId || null });
		reset();
		setTeamId("");
		onDone();
	});

	return (
		<form onSubmit={onSubmit} className="flex flex-col gap-3 rounded-lg border border-slate-gray/20 bg-ice-white dark:bg-[#0f172a] p-4" noValidate aria-label="Connect an ICS feed">
			{connect.isError && <p role="alert" className="text-sm text-error-red">Could not connect that feed. Check the URL and try again.</p>}
			<div className="flex flex-wrap gap-3">
				<div className="flex flex-col gap-1">
					<label htmlFor="ics-feed-label" className="text-sm font-medium text-navy dark:text-[#f8fafc]">Label <span aria-hidden>*</span></label>
					<input id="ics-feed-label" type="text" placeholder="e.g. Varsity Soccer Schedule" {...register("label")} aria-invalid={!!errors.label} aria-describedby={errors.label ? "ics-feed-label-error" : undefined} className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2" />
					{errors.label && <p id="ics-feed-label-error" role="alert" className="text-sm text-error-red">{errors.label.message}</p>}
				</div>
				<div className="flex flex-1 flex-col gap-1">
					<label htmlFor="ics-feed-url" className="text-sm font-medium text-navy dark:text-[#f8fafc]">Feed URL <span aria-hidden>*</span></label>
					<input id="ics-feed-url" type="url" placeholder="https://example.com/schedule.ics" {...register("feedUrl")} aria-invalid={!!errors.feedUrl} aria-describedby={errors.feedUrl ? "ics-feed-url-error" : undefined} className="min-h-11 w-full rounded-md border border-slate-gray/30 px-3 py-2" />
					{errors.feedUrl && <p id="ics-feed-url-error" role="alert" className="text-sm text-error-red">{errors.feedUrl.message}</p>}
				</div>
				<div className="flex flex-col gap-1">
					<label htmlFor="ics-feed-timezone" className="text-sm font-medium text-navy dark:text-[#f8fafc]">Timezone <span aria-hidden>*</span></label>
					<input id="ics-feed-timezone" type="text" placeholder="America/New_York" {...register("timezone")} aria-invalid={!!errors.timezone} aria-describedby={errors.timezone ? "ics-feed-timezone-error" : undefined} className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2" />
					{errors.timezone && <p id="ics-feed-timezone-error" role="alert" className="text-sm text-error-red">{errors.timezone.message}</p>}
				</div>
				<div className="flex flex-col gap-1">
					<label htmlFor="ics-feed-team" className="text-sm font-medium text-navy dark:text-[#f8fafc]">Team</label>
					<select id="ics-feed-team" value={teamId} onChange={(event) => setTeamId(event.target.value)} className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2">
						<option value="">Org-wide (no single team)</option>
						{teams?.items?.map((team) => <option key={team.id} value={team.id}>{team.name}</option>)}
					</select>
				</div>
			</div>
			<p className="text-xs text-slate-gray dark:text-[#cbd5e1]">Resolves this feed&apos;s floating (no UTC offset) event times. Synced events also reuse this timezone.</p>
			<Button type="submit" disabled={isSubmitting} className="self-start">{isSubmitting ? "Connecting…" : "Connect feed"}</Button>
		</form>
	);
}

function IcsFeedSection({ organizationId, readOnly }: { organizationId: string; readOnly: boolean }) {
	const { data, isLoading, isError, refetch } = useEventSourceConnections(organizationId);
	const disconnect = useDisconnectEventSource(organizationId);
	const [showForm, setShowForm] = useState(false);
	if (isLoading) return <LoadingState label="Loading connections…" />;
	if (isError) return <ErrorState message="Could not load event-source connections." onRetry={() => refetch()} />;
	const connections = data ?? [];
	return (
		<div className="flex flex-col gap-3">
			<div className="flex items-center justify-between">
				<h3 className="font-heading text-base font-semibold text-navy dark:text-[#f8fafc]">ICS Feed</h3>
				{!readOnly && <Button type="button" variant="secondary" onClick={() => setShowForm((value) => !value)}>{showForm ? "Cancel" : "Connect a feed"}</Button>}
			</div>
			{showForm && <ConnectIcsFeedForm organizationId={organizationId} onDone={() => setShowForm(false)} />}
			{connections.length > 0 && (
				<ul className="flex flex-col gap-2" aria-label="Connected ICS feeds">
					{connections.map((connection) => (
						<li key={connection.id} className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-slate-gray/20 bg-pure-white dark:bg-[#111827] p-3">
							<div className="min-w-0 flex-1">
								<p className="break-words font-medium text-navy dark:text-[#f8fafc]">{connection.label}</p>
								<p className="break-words text-sm text-slate-gray dark:text-[#cbd5e1]">{connection.feedUrl}</p>
								<p className="text-xs text-slate-gray dark:text-[#cbd5e1]">{connection.status === "ACTIVE" ? connection.lastSyncedAt ? `Last synced ${new Date(connection.lastSyncedAt).toLocaleString()}` : "Not yet synced" : "Disconnected"}</p>
							</div>
							{!readOnly && connection.status === "ACTIVE" && <Button type="button" variant="secondary" className="shrink-0" onClick={() => disconnect.mutate(connection.id)} disabled={disconnect.isPending}>Disconnect</Button>}
						</li>
					))}
				</ul>
			)}
		</div>
	);
}

export function IntegrationsPanel({ organizationId, readOnly = false }: { organizationId: string; readOnly?: boolean }) {
	const [searchParams, setSearchParams] = useSearchParams();
	const callbackStatus = searchParams.get("integration");
	return (
		<div className="flex flex-col gap-6">
			{callbackStatus && (
				<div role="status" className="flex items-center justify-between gap-3 rounded-lg border border-green-500/30 bg-green-500/10 p-3 text-sm text-navy dark:text-[#f8fafc]">
					<span>{callbackStatus === "connected" ? "The organization provider authorization completed." : "The provider returned to Rally26. Review the connection status below."}</span>
					<button type="button" className="font-medium text-azure-blue hover:underline" onClick={() => { searchParams.delete("integration"); searchParams.delete("provider"); setSearchParams(searchParams, { replace: true }); }}>Dismiss</button>
				</div>
			)}
			{readOnly && (
				<div role="status" className="rounded-lg border border-slate-gray/20 bg-ice-white dark:bg-[#0f172a] p-4 text-sm text-slate-gray dark:text-[#cbd5e1]">
					Support access is view-only here — integrations are configured by organization staff.
				</div>
			)}
			<div className="rounded-lg border border-slate-gray/20 bg-ice-white dark:bg-[#0f172a] p-4">
				<h3 className="font-heading text-base font-semibold text-navy dark:text-[#f8fafc]">Rally26-managed providers</h3>
				<p className="mt-1 text-sm text-slate-gray dark:text-[#cbd5e1]">
					Payments, fulfillment, email, SMS, and file storage are configured by Rally26. Your organization will never be asked for Rally26&apos;s Stripe, Printify, Resend, Twilio, or storage credentials.
				</p>
			</div>
			<ProviderReadinessSection organizationId={organizationId} readOnly={readOnly} />
			<QuickBooksScaffoldPanel organizationId={organizationId} readOnly={readOnly} />
			<SportsDataScaffoldPanel organizationId={organizationId} readOnly={readOnly} />
			<IntegrationSyncHistory organizationId={organizationId} />
			<IcsFeedSection organizationId={organizationId} readOnly={readOnly} />
			<CsvImportSection organizationId={organizationId} readOnly={readOnly} />
		</div>
	);
}
