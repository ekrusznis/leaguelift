import { useMemo, useState } from "react";
import { Link, useLocation } from "react-router-dom";
import { Button } from "../../components/Button";
import { EmptyState } from "../../components/states/EmptyState";
import { ErrorState } from "../../components/states/ErrorState";
import { LoadingState } from "../../components/states/LoadingState";
import { appPaths } from "../../routes/appPaths";
import { downloadEventCalendar, useCreateEvent, useEvents, type EventScope } from "./api";
import type { CreateEventInput, EventType, EventVisibility, LeagueLiftEvent } from "./types";

function formatDateTime(value: string | null, timezone: string) {
	if (!value) return "To be determined";
	return new Intl.DateTimeFormat("en-US", {
		dateStyle: "medium",
		timeStyle: "short",
		timeZone: timezone,
	}).format(new Date(value));
}

function statusClasses(status: LeagueLiftEvent["status"]) {
	switch (status) {
		case "CANCELLED":
			return "bg-error-50 text-error-700";
		case "POSTPONED":
		case "DELAYED":
			return "bg-gold-100 text-gold-800";
		case "SCHEDULED":
		case "COMPLETED":
			return "bg-green-50 text-green-700";
		default:
			return "bg-slate-100 text-slate-600";
	}
}

function saveBlob(blob: Blob, filename: string) {
	const url = URL.createObjectURL(blob);
	const anchor = document.createElement("a");
	anchor.href = url;
	anchor.download = filename;
	document.body.appendChild(anchor);
	anchor.click();
	anchor.remove();
	URL.revokeObjectURL(url);
}

export function EventListPanel({
	scope,
	canManage = false,
	householdId,
	participantId,
}: {
	scope: EventScope;
	canManage?: boolean;
	householdId?: string;
	participantId?: string;
}) {
	const location = useLocation();
	const eventsQuery = useEvents(scope);
	const createEvent = useCreateEvent(scope);
	const [showCreate, setShowCreate] = useState(false);
	const [downloadError, setDownloadError] = useState<string | null>(null);

	const events = useMemo(
		() => [...(eventsQuery.data ?? [])].sort((left, right) => {
			if (!left.startAt && !right.startAt) return left.displayTitle.localeCompare(right.displayTitle);
			if (!left.startAt) return 1;
			if (!right.startAt) return -1;
			return left.startAt.localeCompare(right.startAt);
		}),
		[eventsQuery.data],
	);

	async function downloadCalendar(event: LeagueLiftEvent) {
		setDownloadError(null);
		try {
			const file = await downloadEventCalendar(event.organizationId, event.id);
			saveBlob(file.blob, file.filename);
		} catch {
			setDownloadError("Could not download this calendar event.");
		}
	}

	if (eventsQuery.isLoading) return <LoadingState label="Loading events…" />;
	if (eventsQuery.isError) return <ErrorState message="Could not load events." onRetry={() => eventsQuery.refetch()} />;

	return (
		<div className="flex flex-col gap-4">
			<div className="flex flex-wrap items-center justify-between gap-3">
				<p className="text-sm text-slate-gray">
					{events.length} event{events.length === 1 ? "" : "s"}
				</p>
				{canManage && scope.type !== "household" && scope.type !== "participant" && (
					<Button type="button" variant="secondary" onClick={() => setShowCreate((value) => !value)}>
						{showCreate ? "Cancel" : "Create event"}
					</Button>
				)}
			</div>

			{showCreate && canManage && scope.type !== "household" && scope.type !== "participant" && (
				<CreateEventForm
					scope={scope}
					isSubmitting={createEvent.isPending}
					error={createEvent.isError ? "Could not create the event. Review the details and try again." : null}
					onCancel={() => setShowCreate(false)}
					onSubmit={async (input) => {
						await createEvent.mutateAsync(input);
						setShowCreate(false);
					}}
				/>
			)}

			{downloadError && <p role="alert" className="text-sm text-error-red">{downloadError}</p>}

			{events.length === 0 ? (
				<EmptyState
					title="No events yet"
					description={canManage ? "Create the first game, match, practice, meeting, or tournament event." : "Events will appear here when they are scheduled."}
				/>
			) : (
				<ul className="flex flex-col gap-3" aria-label="Events">
					{events.map((event) => {
						const search = new URLSearchParams();
						if (householdId) search.set("householdId", householdId);
						if (participantId) search.set("participantId", participantId);
						search.set("returnTo", `${location.pathname}${location.search}`);
						return (
							<li key={event.id} className="rounded-xl border border-slate-gray/20 bg-pure-white p-4">
								<div className="flex flex-wrap items-start justify-between gap-3">
									<div className="min-w-0 flex-1">
										<div className="flex flex-wrap items-center gap-2">
											<Link to={appPaths.event(event.organizationId, event.id, search)} className="break-words font-heading font-semibold text-navy hover:text-azure-blue hover:underline">
												{event.displayTitle}
											</Link>
											<span className={`rounded-full px-2 py-0.5 text-xs font-medium ${statusClasses(event.status)}`}>{event.status}</span>
											{event.sourceType !== "MANUAL" && <span className="rounded-full bg-info-50 px-2 py-0.5 text-xs font-medium text-info-700">Imported</span>}
										</div>
										<p className="mt-1 text-sm text-slate-gray">
											{formatDateTime(event.startAt, event.timezone)}
											{event.arrivalAt ? ` · Arrive ${formatDateTime(event.arrivalAt, event.timezone)}` : ""}
										</p>
										<p className="text-sm text-slate-gray">
											{[event.venueName, event.area, event.address].filter(Boolean).join(" · ") || "Location to be determined"}
										</p>
									</div>
									<div className="flex shrink-0 flex-wrap gap-2">
										<Button type="button" variant="secondary" onClick={() => downloadCalendar(event)}>Add to calendar</Button>
										<Link
											to={appPaths.event(event.organizationId, event.id, search)}
											className="inline-flex min-h-11 items-center rounded-md border border-slate-gray/30 bg-pure-white px-4 py-2 text-sm font-medium text-navy hover:bg-ice-white"
										>
											View details
										</Link>
									</div>
								</div>
							</li>
						);
					})}
				</ul>
			)}
		</div>
	);
}

function toInstant(value: string) {
	return value ? new Date(value).toISOString() : null;
}

function CreateEventForm({
	scope,
	isSubmitting,
	error,
	onCancel,
	onSubmit,
}: {
	scope: Exclude<EventScope, { type: "household" } | { type: "participant" }>;
	isSubmitting: boolean;
	error: string | null;
	onCancel: () => void;
	onSubmit: (input: CreateEventInput) => Promise<void>;
}) {
	const [eventType, setEventType] = useState<EventType>("COMPETITION");
	const [title, setTitle] = useState("");
	const [opponentName, setOpponentName] = useState("");
	const [startAt, setStartAt] = useState("");
	const [arrivalAt, setArrivalAt] = useState("");
	const [venueName, setVenueName] = useState("");
	const [address, setAddress] = useState("");
	const [area, setArea] = useState("");
	const [visibility, setVisibility] = useState<EventVisibility>(scope.type === "organization" ? "ORGANIZATION" : "TEAM");
	const timezone = Intl.DateTimeFormat().resolvedOptions().timeZone || "America/New_York";

	return (
		<form
			className="rounded-xl border border-slate-gray/20 bg-ice-white p-4"
			onSubmit={async (event) => {
				event.preventDefault();
				await onSubmit({
					eventType,
					title: title.trim() || null,
					opponentName: opponentName.trim() || null,
					startAt: toInstant(startAt),
					arrivalAt: toInstant(arrivalAt),
					timezone,
					venueName: venueName.trim() || null,
					address: address.trim() || null,
					area: area.trim() || null,
					visibility,
				});
			}}
		>
			<h3 className="font-heading text-base font-semibold text-navy">New event</h3>
			<p className="mt-1 text-sm text-slate-gray">The event starts as a draft. Open it after creation to publish or update its status.</p>
			<div className="mt-4 grid gap-3 md:grid-cols-2">
				<label className="flex flex-col gap-1 text-sm font-medium text-navy">
					Event type
					<select value={eventType} onChange={(event) => setEventType(event.target.value as EventType)} className="min-h-11 rounded-md border border-slate-gray/30 bg-white px-3 py-2">
						<option value="COMPETITION">Game / match / competition</option>
						<option value="PRACTICE">Practice</option>
						<option value="MEETING">Meeting</option>
						<option value="TOURNAMENT">Tournament item</option>
						<option value="OTHER">Other</option>
					</select>
				</label>
				<label className="flex flex-col gap-1 text-sm font-medium text-navy">
					Title
					<input value={title} onChange={(event) => setTitle(event.target.value)} placeholder="Optional custom title" maxLength={200} className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2" />
				</label>
				<label className="flex flex-col gap-1 text-sm font-medium text-navy">
					Opponent
					<input value={opponentName} onChange={(event) => setOpponentName(event.target.value)} placeholder="External opponent name" maxLength={120} className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2" />
				</label>
				<label className="flex flex-col gap-1 text-sm font-medium text-navy">
					Start time
					<input type="datetime-local" value={startAt} onChange={(event) => setStartAt(event.target.value)} className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2" />
				</label>
				<label className="flex flex-col gap-1 text-sm font-medium text-navy">
					Arrival time
					<input type="datetime-local" value={arrivalAt} onChange={(event) => setArrivalAt(event.target.value)} className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2" />
				</label>
				<label className="flex flex-col gap-1 text-sm font-medium text-navy">
					Venue
					<input value={venueName} onChange={(event) => setVenueName(event.target.value)} maxLength={200} className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2" />
				</label>
				<label className="flex flex-col gap-1 text-sm font-medium text-navy md:col-span-2">
					Address
					<input value={address} onChange={(event) => setAddress(event.target.value)} maxLength={300} className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2" />
				</label>
				<label className="flex flex-col gap-1 text-sm font-medium text-navy">
					Field / court / area
					<input value={area} onChange={(event) => setArea(event.target.value)} maxLength={120} className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2" />
				</label>
				<label className="flex flex-col gap-1 text-sm font-medium text-navy">
					Visibility
					<select value={visibility} onChange={(event) => setVisibility(event.target.value as EventVisibility)} className="min-h-11 rounded-md border border-slate-gray/30 bg-white px-3 py-2">
						<option value="TEAM">Team</option>
						<option value="ORGANIZATION">Organization</option>
						<option value="PUBLIC">Public</option>
					</select>
				</label>
			</div>
			{error && <p role="alert" className="mt-3 text-sm text-error-red">{error}</p>}
			<div className="mt-4 flex justify-end gap-2">
				<Button type="button" variant="secondary" onClick={onCancel}>Cancel</Button>
				<Button type="submit" disabled={isSubmitting}>{isSubmitting ? "Creating…" : "Create event"}</Button>
			</div>
		</form>
	);
}
