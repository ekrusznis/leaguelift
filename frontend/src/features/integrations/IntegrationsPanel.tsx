import { useState } from "react";
import { zodResolver } from "@hookform/resolvers/zod";
import { useForm } from "react-hook-form";
import { Button } from "../../components/Button";
import { ErrorState } from "../../components/states/ErrorState";
import { LoadingState } from "../../components/states/LoadingState";
import { CsvImportSection } from "./CsvImportSection";
import { useConnectIcsFeed, useDisconnectEventSource, useEventSourceConnections } from "./api";
import { connectIcsFeedSchema, type ConnectIcsFeedFormValues } from "./schema";

const PLATFORM_OPERATED_CONNECTORS = [
	{ name: "Stripe", description: "Payments, payouts, and sponsorship/apparel checkout." },
	{ name: "Printify", description: "Apparel catalog, cost discovery, and order submission." },
	{ name: "DigitalOcean Spaces", description: "Organization logo/cover and document storage." },
	{ name: "Resend", description: "Transactional and reminder emails." },
	{ name: "Twilio", description: "Fee-payment reminder SMS." },
	{ name: "Google Maps", description: "Directions links on event pages." },
	{ name: "Google Calendar", description: ".ics calendar export (Add-to-Calendar) — no account connection needed." },
];

/** Vendors with no verified API access yet — shown so the roadmap is visible, never wired to a fake connect flow (ADR-031). CSV import (ADR-032) graduated out of this list — see CsvImportSection. */
const COMING_SOON_CONNECTORS = [
	{ name: "MaxPreps", description: "Requires a verified MaxPreps API relationship, not yet in place." },
	{ name: "GameChanger", description: "Requires a verified GameChanger API relationship, not yet in place." },
	{ name: "SportsEngine", description: "Requires a verified SportsEngine API relationship, not yet in place." },
];

function ComingSoonCard({ name, description }: { name: string; description: string }) {
	return (
		<div className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-slate-gray/20 bg-ice-white/60 p-3 opacity-70">
			<div className="min-w-0 flex-1">
				<p className="font-medium text-navy">{name}</p>
				<p className="text-sm text-slate-gray">{description}</p>
			</div>
			<span className="shrink-0 rounded-full bg-slate-gray/10 px-2 py-0.5 text-xs font-medium text-slate-gray">Coming soon</span>
		</div>
	);
}

function ConnectIcsFeedForm({ organizationId, onDone }: { organizationId: string; onDone: () => void }) {
	const connect = useConnectIcsFeed(organizationId);
	const {
		register,
		handleSubmit,
		reset,
		formState: { errors, isSubmitting },
	} = useForm<ConnectIcsFeedFormValues>({
		resolver: zodResolver(connectIcsFeedSchema),
		defaultValues: { label: "", feedUrl: "" },
	});

	const onSubmit = handleSubmit(async (values) => {
		await connect.mutateAsync(values);
		reset();
		onDone();
	});

	return (
		<form onSubmit={onSubmit} className="flex flex-col gap-3 rounded-lg border border-slate-gray/20 bg-ice-white p-4" noValidate aria-label="Connect an ICS feed">
			{connect.isError && (
				<p role="alert" className="text-sm text-error-red">
					Could not connect that feed. Check the URL and try again.
				</p>
			)}
			<div className="flex flex-wrap gap-3">
				<div className="flex flex-col gap-1">
					<label htmlFor="ics-feed-label" className="text-sm font-medium text-navy">
						Label <span aria-hidden>*</span>
					</label>
					<input
						id="ics-feed-label"
						type="text"
						placeholder="e.g. Varsity Soccer Schedule"
						{...register("label")}
						aria-invalid={!!errors.label}
						className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2"
					/>
					{errors.label && <p role="alert" className="text-sm text-error-red">{errors.label.message}</p>}
				</div>
				<div className="flex flex-1 flex-col gap-1">
					<label htmlFor="ics-feed-url" className="text-sm font-medium text-navy">
						Feed URL <span aria-hidden>*</span>
					</label>
					<input
						id="ics-feed-url"
						type="url"
						placeholder="https://example.com/schedule.ics"
						{...register("feedUrl")}
						aria-invalid={!!errors.feedUrl}
						className="min-h-11 w-full rounded-md border border-slate-gray/30 px-3 py-2"
					/>
					{errors.feedUrl && <p role="alert" className="text-sm text-error-red">{errors.feedUrl.message}</p>}
				</div>
			</div>
			<p className="text-xs text-slate-gray">Syncing starts once the scheduled sync is available — connecting a feed now just saves it for later.</p>
			<Button type="submit" disabled={isSubmitting} className="self-start">
				{isSubmitting ? "Connecting…" : "Connect feed"}
			</Button>
		</form>
	);
}

function IcsFeedSection({ organizationId }: { organizationId: string }) {
	const { data, isLoading, isError, refetch } = useEventSourceConnections(organizationId);
	const disconnect = useDisconnectEventSource(organizationId);
	const [showForm, setShowForm] = useState(false);

	if (isLoading) {
		return <LoadingState label="Loading connections…" />;
	}
	if (isError) {
		return <ErrorState message="Could not load event-source connections." onRetry={() => refetch()} />;
	}

	const connections = data ?? [];

	return (
		<div className="flex flex-col gap-3">
			<div className="flex items-center justify-between">
				<h3 className="font-heading text-base font-semibold text-navy">ICS Feed</h3>
				<Button type="button" variant="secondary" onClick={() => setShowForm((v) => !v)}>
					{showForm ? "Cancel" : "Connect a feed"}
				</Button>
			</div>
			{showForm && <ConnectIcsFeedForm organizationId={organizationId} onDone={() => setShowForm(false)} />}
			{connections.length > 0 && (
				<ul className="flex flex-col gap-2" aria-label="Connected ICS feeds">
					{connections.map((connection) => (
						<li key={connection.id} className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-slate-gray/20 bg-pure-white p-3">
							<div className="min-w-0 flex-1">
								<p className="break-words font-medium text-navy">{connection.label}</p>
								<p className="break-words text-sm text-slate-gray">{connection.feedUrl}</p>
								<p className="text-xs text-slate-gray">
									{connection.status === "ACTIVE"
										? connection.lastSyncedAt
											? `Last synced ${new Date(connection.lastSyncedAt).toLocaleString()}`
											: "Not yet synced"
										: "Disconnected"}
								</p>
							</div>
							{connection.status === "ACTIVE" && (
								<Button
									type="button"
									variant="secondary"
									className="shrink-0"
									onClick={() => disconnect.mutate(connection.id)}
									disabled={disconnect.isPending}
								>
									Disconnect
								</Button>
							)}
						</li>
					))}
				</ul>
			)}
		</div>
	);
}

/**
 * Organization-owner-level Integrations settings page section (Phase 12 slice 1,
 * ADR-031). Platform-operated connectors are read-only — an org never connects or
 * authorizes these, they're just infrastructure. ICS Feed is the only real
 * org-connected connect flow this slice; CSV import and the not-yet-integrated
 * vendors render as disabled "coming soon" cards, never a fake connect button.
 */
export function IntegrationsPanel({ organizationId }: { organizationId: string }) {
	return (
		<div className="flex flex-col gap-6">
			<div>
				<h3 className="font-heading text-base font-semibold text-navy">Platform Connectors</h3>
				<p className="text-sm text-slate-gray">LeagueLift-operated — nothing to connect, shown for visibility.</p>
				<ul className="mt-3 flex flex-col gap-2" aria-label="Platform-operated connectors">
					{PLATFORM_OPERATED_CONNECTORS.map((connector) => (
						<li key={connector.name} className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-slate-gray/20 bg-pure-white p-3">
							<div className="min-w-0 flex-1">
								<p className="font-medium text-navy">{connector.name}</p>
								<p className="text-sm text-slate-gray">{connector.description}</p>
							</div>
							<span className="shrink-0 rounded-full bg-green-500/15 px-2 py-0.5 text-xs font-medium text-victory-green">Active</span>
						</li>
					))}
				</ul>
			</div>

			<IcsFeedSection organizationId={organizationId} />

			<CsvImportSection organizationId={organizationId} />

			<div>
				<h3 className="font-heading text-base font-semibold text-navy">More Connectors</h3>
				<p className="text-sm text-slate-gray">Planned — not available yet.</p>
				<ul className="mt-3 flex flex-col gap-2" aria-label="Planned connectors">
					{COMING_SOON_CONNECTORS.map((connector) => (
						<li key={connector.name}>
							<ComingSoonCard name={connector.name} description={connector.description} />
						</li>
					))}
				</ul>
			</div>
		</div>
	);
}
