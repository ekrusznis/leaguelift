import { useEffect, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { Button } from "../../components/Button";
import { ErrorState } from "../../components/states/ErrorState";
import { LoadingState } from "../../components/states/LoadingState";
import {
	useCheckPersonalIntegrationHealth,
	useClearGoogleCalendarSelection,
	useDisconnectPersonalIntegration,
	useGoogleCalendarOverview,
	useGoogleCalendars,
	useRefreshPersonalIntegration,
	useSelectGoogleCalendar,
	useStartPersonalIntegrationAuthorization,
} from "./api";
import type { IntegrationConnectionStatus, IntegrationReadiness } from "./types";

function statusLabel(readiness: IntegrationReadiness, connectionStatus?: IntegrationConnectionStatus) {
	if (connectionStatus) {
		return {
			CONNECTED: "Connected",
			AUTHORIZATION_PENDING: "Authorization pending",
			DEGRADED: "Needs attention",
			REVOKED: "Revoked",
			DISCONNECTED: "Disconnected",
			AVAILABLE: "Available",
			NOT_CONFIGURED: "Not configured",
			UNSUPPORTED: "Unsupported",
		}[connectionStatus];
	}
	return {
		AVAILABLE: "Available",
		NOT_CONFIGURED: "Not configured",
		PARTNER_PENDING: "Partner access required",
		PLATFORM_MANAGED: "Managed by LeagueLift",
		UNSUPPORTED: "Unsupported",
	}[readiness];
}

export function PersonalIntegrationsPage() {
	const overview = useGoogleCalendarOverview();
	const [searchParams, setSearchParams] = useSearchParams();
	const [selectedCalendarId, setSelectedCalendarId] = useState("");
	const startAuthorization = useStartPersonalIntegrationAuthorization();
	const selectCalendar = useSelectGoogleCalendar();
	const clearSelection = useClearGoogleCalendarSelection();
	const refresh = useRefreshPersonalIntegration();
	const health = useCheckPersonalIntegrationHealth();
	const disconnect = useDisconnectPersonalIntegration();
	const connection = overview.data?.catalog.connection;
	const isConnected = connection?.status === "CONNECTED" || connection?.status === "DEGRADED";
	const statusTone = connection?.status === "CONNECTED"
		? "bg-green-500/15 text-victory-green"
		: connection?.status === "DEGRADED"
			? "bg-amber-500/15 text-amber-800"
			: "bg-slate-gray/10 text-slate-gray";
	const calendars = useGoogleCalendars(Boolean(isConnected));

	useEffect(() => {
		if (overview.data?.setting?.selectedCalendarId) {
			setSelectedCalendarId(overview.data.setting.selectedCalendarId);
		}
	}, [overview.data?.setting?.selectedCalendarId]);

	if (overview.isLoading) return <LoadingState label="Loading your integrations…" />;
	if (overview.isError || !overview.data) {
		return <ErrorState message="Could not load your integrations." onRetry={() => overview.refetch()} />;
	}

	const { catalog, setting } = overview.data;
	const callbackStatus = searchParams.get("integration");
	const canStart = catalog.readiness === "AVAILABLE" && connection?.status !== "CONNECTED";
	const busy = startAuthorization.isPending || refresh.isPending || health.isPending || disconnect.isPending;

	async function connect() {
		const result = await startAuthorization.mutateAsync("GOOGLE_CALENDAR");
		window.location.assign(result.authorizationUrl);
	}

	return (
		<div className="flex flex-col gap-6">
			<div>
				<h1 className="font-heading text-2xl font-bold text-navy">My Integrations</h1>
				<p className="mt-1 text-slate-gray">Personal connections belong to you, not to an organization. Organization accounting and sports-data connections remain in each organization’s Integrations workspace.</p>
			</div>

			{callbackStatus && (
				<div role="status" className="flex items-center justify-between gap-3 rounded-lg border border-green-500/30 bg-green-500/10 p-3 text-sm text-navy">
					<span>{callbackStatus === "connected" ? "The provider authorization completed." : "The provider returned to LeagueLift. Review the connection status below."}</span>
					<button type="button" className="font-medium text-azure-blue hover:underline" onClick={() => { searchParams.delete("integration"); searchParams.delete("provider"); setSearchParams(searchParams, { replace: true }); }}>Dismiss</button>
				</div>
			)}

			<section className="rounded-xl border border-slate-gray/20 bg-pure-white p-5" aria-labelledby="google-calendar-title">
				<div className="flex flex-wrap items-start justify-between gap-4">
					<div className="max-w-2xl">
						<h2 id="google-calendar-title" className="font-heading text-lg font-semibold text-navy">Google Calendar</h2>
						<p className="mt-1 text-sm text-slate-gray">{catalog.description}</p>
					</div>
					<span className={`rounded-full px-2.5 py-1 text-xs font-semibold ${statusTone}`}>
						{statusLabel(catalog.readiness, connection?.status)}
					</span>
				</div>

				{connection?.lastErrorMessage && <p role="alert" className="mt-3 rounded-md bg-error-red/10 p-3 text-sm text-error-red">{connection.lastErrorMessage}</p>}

				{!isConnected && (
					<div className="mt-5 rounded-lg border border-slate-gray/20 bg-ice-white p-4">
						<p className="text-sm text-slate-gray">{catalog.activationRequirement}</p>
						<div className="mt-3 flex flex-wrap gap-2">
							<Button type="button" onClick={() => void connect()} disabled={!canStart || busy}>{startAuthorization.isPending ? "Starting…" : connection?.status === "DEGRADED" ? "Reauthorize" : "Connect Google Calendar"}</Button>
							{connection && connection.status !== "DISCONNECTED" && <Button type="button" variant="secondary" onClick={() => disconnect.mutate(connection.id)} disabled={busy}>Disconnect locally</Button>}
						</div>
						{catalog.readiness !== "AVAILABLE" && <p className="mt-2 text-xs text-slate-gray">LeagueLift does not request Google credentials until the verified provider application is enabled.</p>}
					</div>
				)}

				{isConnected && connection && (
					<div className="mt-5 flex flex-col gap-5">
						<div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
							<Detail label="Account" value={connection.externalAccountName ?? connection.externalAccountId ?? "Connected account"} />
							<Detail label="Selected calendar" value={setting?.selectedCalendarName ?? "Not selected"} />
							<Detail label="Mapped events" value={String(overview.data.mappingCount)} />
							<Detail label="Automatic sync" value={overview.data.automaticSyncAvailable ? "Available" : "Not activated"} />
						</div>

						<div>
							<label htmlFor="google-calendar-selection" className="text-sm font-medium text-navy">Destination calendar</label>
							{calendars.isLoading ? <p className="mt-2 text-sm text-slate-gray">Loading calendars…</p> : calendars.isError ? (
								<p role="alert" className="mt-2 text-sm text-error-red">Calendar discovery is not available until the official provider client is activated.</p>
							) : (
								<div className="mt-2 flex flex-wrap gap-2">
									<select id="google-calendar-selection" value={selectedCalendarId} onChange={(event) => setSelectedCalendarId(event.target.value)} className="min-h-11 min-w-64 rounded-md border border-slate-gray/30 bg-pure-white px-3 py-2 text-navy">
										<option value="">Choose a writable calendar</option>
										{calendars.data?.filter((calendar) => calendar.writable).map((calendar) => <option key={calendar.id} value={calendar.id}>{calendar.name}{calendar.primary ? " (Primary)" : ""}</option>)}
									</select>
									<Button type="button" onClick={() => selectCalendar.mutate(selectedCalendarId)} disabled={!selectedCalendarId || selectCalendar.isPending}>{selectCalendar.isPending ? "Saving…" : "Save calendar"}</Button>
									{setting?.selectedCalendarId && <Button type="button" variant="secondary" onClick={() => clearSelection.mutate()} disabled={clearSelection.isPending}>Clear selection</Button>}
								</div>
							)}
						</div>

						<div className="flex flex-wrap gap-2">
							<Button type="button" variant="secondary" onClick={() => health.mutate(connection.id)} disabled={busy}>Check connection</Button>
							<Button type="button" variant="secondary" onClick={() => refresh.mutate(connection.id)} disabled={busy}>Refresh authorization</Button>
							<Button type="button" variant="secondary" onClick={() => void connect()} disabled={busy || catalog.readiness !== "AVAILABLE"}>Reauthorize</Button>
							<Button type="button" variant="secondary" onClick={() => disconnect.mutate(connection.id)} disabled={busy}>Disconnect</Button>
						</div>
					</div>
				)}
			</section>

			<section className="rounded-xl border border-slate-gray/20 bg-ice-white p-5">
				<h2 className="font-heading text-lg font-semibold text-navy">ICS always remains available</h2>
				<p className="mt-1 text-sm text-slate-gray">Event pages and authorized schedules can still download standards-based ICS files without connecting a Google account. Google synchronization is optional and will begin as LeagueLift-to-Google only.</p>
				<Link to="/app/help/connecting-google-calendar" className="mt-3 inline-block text-sm font-medium text-azure-blue hover:underline">Read the Google Calendar guide →</Link>
			</section>
		</div>
	);
}

function Detail({ label, value }: { label: string; value: string }) {
	return <div className="rounded-lg border border-slate-gray/20 bg-ice-white p-3"><p className="text-xs font-medium uppercase tracking-wide text-slate-gray">{label}</p><p className="mt-1 break-words text-sm font-semibold text-navy">{value}</p></div>;
}
