import { useEffect, useMemo, useState } from "react";
import { Link, useLocation } from "react-router-dom";
import { Button } from "../../components/Button";
import { EmptyState } from "../../components/states/EmptyState";
import { ErrorState } from "../../components/states/ErrorState";
import { LoadingState } from "../../components/states/LoadingState";
import { appPaths } from "../../routes/appPaths";
import { EventTemplatePanel } from "./EventTemplatePanel";
import { deriveTemplateTimes } from "./eventTemplateTiming";
import { formatEventAllDayDate, formatEventDateTime } from "./formatEventDateTime";
import { downloadEventCalendar, useCreateEvent, useEvents, useEventTemplates, useEventTimezoneDefault, type EventScope } from "./api";
import type { CreateEventInput, EventTemplate, EventType, EventVisibility, Rally26Event } from "./types";

function formatDateTime(value: string | null, timezone: string) {
	if (!value) return "To be determined";
	return new Intl.DateTimeFormat("en-US", {
		dateStyle: "medium",
		timeStyle: "short",
		timeZone: timezone,
	}).format(new Date(value));
}

function statusClasses(status: Rally26Event["status"]) {
	switch (status) {
		case "CANCELLED":
			return "bg-error-50 text-error-700";
		case "POSTPONED":
		case "DELAYED":
			return "bg-gold-100 text-gold-800";
		case "SCHEDULED":
		case "COMPLETED":
			return "bg-green-50 dark:bg-green-950 text-green-700";
		default:
			return "bg-slate-100 dark:bg-slate-800 text-slate-600 dark:text-[#cbd5e1]";
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
	const [showTemplates, setShowTemplates] = useState(false);
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

	async function downloadCalendar(event: Rally26Event) {
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

	const mutableScope = scope.type !== "household" && scope.type !== "participant";

	return (
		<div className="flex flex-col gap-4">
			<div className="flex flex-wrap items-center justify-between gap-3">
				<p className="text-sm text-slate-gray dark:text-[#cbd5e1]">
					{events.length} event{events.length === 1 ? "" : "s"}
				</p>
				{canManage && mutableScope && (
					<div className="flex flex-wrap gap-2">
						{scope.type === "organization" && (
							<Button type="button" variant="secondary" onClick={() => setShowTemplates((value) => !value)}>
								{showTemplates ? "Hide templates" : "Manage templates"}
							</Button>
						)}
						<Button type="button" variant="secondary" onClick={() => setShowCreate((value) => !value)}>
							{showCreate ? "Cancel" : "Create event"}
						</Button>
					</div>
				)}
			</div>

			{showTemplates && canManage && scope.type === "organization" && (
				<EventTemplatePanel organizationId={scope.organizationId} />
			)}

			{showCreate && canManage && mutableScope && (
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
							<li key={event.id} className="rounded-xl border border-slate-gray/20 bg-pure-white dark:bg-[#111827] p-4">
								<div className="flex flex-wrap items-start justify-between gap-3">
									<div className="min-w-0 flex-1">
										<div className="flex flex-wrap items-center gap-2">
											<Link to={appPaths.event(event.organizationId, event.id, search)} className="break-words font-heading font-semibold text-navy dark:text-[#f8fafc] hover:text-azure-blue hover:underline">
												{event.displayTitle}
											</Link>
											<span className={`rounded-full px-2 py-0.5 text-xs font-medium ${statusClasses(event.status)}`}>{event.status}</span>
											{event.sourceType !== "MANUAL" && <span className="rounded-full bg-info-50 px-2 py-0.5 text-xs font-medium text-info-700">Imported</span>}
										</div>
										<p className="mt-0.5 text-xs text-slate-gray/80">
											{event.allDayDate
												? `${formatEventAllDayDate(event.allDayDate)} (all day)`
												: (formatEventDateTime(event.startAt, event.timezone) ?? "To be determined")}
										</p>
										<p className="mt-1 text-sm text-slate-gray dark:text-[#cbd5e1]">
											{event.allDayDate ? "All day" : formatDateTime(event.startAt, event.timezone)}
											{event.arrivalAt ? ` · Arrive ${formatDateTime(event.arrivalAt, event.timezone)}` : ""}
										</p>
										<p className="text-sm text-slate-gray dark:text-[#cbd5e1]">
											{[event.venueName, event.area, event.address].filter(Boolean).join(" · ") || "Location to be determined"}
										</p>
									</div>
									<div className="flex shrink-0 flex-wrap gap-2">
										<Button type="button" variant="secondary" onClick={() => downloadCalendar(event)}>Add to calendar</Button>
										<Link
											to={appPaths.event(event.organizationId, event.id, search)}
											className="inline-flex min-h-11 items-center rounded-md border border-slate-gray/30 bg-pure-white dark:bg-[#111827] px-4 py-2 text-sm font-medium text-navy dark:text-[#f8fafc] hover:bg-ice-white hover:dark:bg-[#0f172a]"
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

// Known gap (ADR-071): converts using the BROWSER's local zone, ignoring whichever `timezone` value is
// actually selected in the form below — deferred pending a JS timezone-aware date library.
function toInstant(value: string) {
	return value ? new Date(value).toISOString() : null;
}

function templateTimingSummary(template: EventTemplate) {
	const parts: string[] = [];
	if (template.durationMinutes != null) parts.push(`${template.durationMinutes}-minute event`);
	if (template.arrivalOffsetMinutes != null) parts.push(`arrival ${template.arrivalOffsetMinutes} minutes before`);
	if (template.meetingOffsetMinutes != null) parts.push(`meeting ${template.meetingOffsetMinutes} minutes before`);
	return parts.join(" · ");
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
	const templatesQuery = useEventTemplates(scope.organizationId);
	const templates = templatesQuery.data ?? [];
	const [selectedTemplateId, setSelectedTemplateId] = useState("");
	const [eventType, setEventType] = useState<EventType>("COMPETITION");
	const [title, setTitle] = useState("");
	const [description, setDescription] = useState("");
	const [opponentName, setOpponentName] = useState("");
	const [isAllDay, setIsAllDay] = useState(false);
	const [allDayDate, setAllDayDate] = useState("");
	const [startAt, setStartAt] = useState("");
	const [endAt, setEndAt] = useState("");
	const [arrivalAt, setArrivalAt] = useState("");
	const [meetingAt, setMeetingAt] = useState("");
	const [venueName, setVenueName] = useState("");
	const [address, setAddress] = useState("");
	const [area, setArea] = useState("");
	const [meetingPoint, setMeetingPoint] = useState("");
	const [directionsNotes, setDirectionsNotes] = useState("");
	const [visibility, setVisibility] = useState<EventVisibility>(scope.type === "organization" ? "ORGANIZATION" : "TEAM");
	const [timezone, setTimezone] = useState(Intl.DateTimeFormat().resolvedOptions().timeZone || "America/New_York");
	const [timezoneTouched, setTimezoneTouched] = useState(false);
	const selectedTemplate = templates.find((template) => template.id === selectedTemplateId) ?? null;

	// Browser Intl above is only the last-resort fallback (ADR-071) — the resolved override-chain default,
	// once it loads, takes over as long as the user hasn't already typed something else.
	const timezoneDefault = useEventTimezoneDefault(
		scope.organizationId,
		scope.type === "team" ? scope.teamId : undefined,
		scope.type === "tournament" ? scope.tournamentId : undefined,
	);
	useEffect(() => {
		if (!timezoneTouched && timezoneDefault.data?.timezone) {
			setTimezone(timezoneDefault.data.timezone);
		}
	}, [timezoneDefault.data?.timezone, timezoneTouched]);

	function selectTemplate(templateId: string) {
		setSelectedTemplateId(templateId);
		const template = templates.find((candidate) => candidate.id === templateId);
		if (!template) return;
		setEventType(template.eventType);
		setTitle(template.title ?? "");
		setDescription(template.description ?? "");
		setTimezone(template.timezone);
		setTimezoneTouched(true);
		setVenueName(template.venueName ?? "");
		setAddress(template.address ?? "");
		setArea(template.area ?? "");
		setMeetingPoint(template.meetingPoint ?? "");
		setDirectionsNotes(template.directionsNotes ?? "");
		setVisibility(template.visibility);
		setEndAt("");
		setArrivalAt("");
		setMeetingAt("");
	}

	return (
		<form
			className="rounded-xl border border-slate-gray/20 bg-ice-white dark:bg-[#0f172a] p-4"
			onSubmit={async (event) => {
				event.preventDefault();
				const startInstant = isAllDay ? null : toInstant(startAt);
				const derived = isAllDay ? { endAt: null, arrivalAt: null, meetingAt: null } : deriveTemplateTimes(startInstant, selectedTemplate);
				await onSubmit({
					eventType,
					title: title.trim() || null,
					description: description.trim() || null,
					opponentName: opponentName.trim() || null,
					startAt: startInstant,
					endAt: isAllDay ? null : (toInstant(endAt) ?? derived.endAt),
					arrivalAt: isAllDay ? null : (toInstant(arrivalAt) ?? derived.arrivalAt),
					meetingAt: isAllDay ? null : (toInstant(meetingAt) ?? derived.meetingAt),
					timezone,
					venueName: venueName.trim() || null,
					address: address.trim() || null,
					area: area.trim() || null,
					meetingPoint: meetingPoint.trim() || null,
					directionsNotes: directionsNotes.trim() || null,
					visibility,
					allDayDate: isAllDay ? allDayDate || null : null,
				});
			}}
		>
			<h3 className="font-heading text-base font-semibold text-navy dark:text-[#f8fafc]">New event</h3>
			<p className="mt-1 text-sm text-slate-gray dark:text-[#cbd5e1]">The event starts as a draft. A template only supplies defaults; every field can be reviewed before creation.</p>
			<div className="mt-4 grid gap-3 md:grid-cols-2">
				<label className="flex flex-col gap-1 text-sm font-medium text-navy dark:text-[#f8fafc] md:col-span-2">
					Reusable template
					<select
						value={selectedTemplateId}
						onChange={(event) => selectTemplate(event.target.value)}
						disabled={templatesQuery.isLoading || templatesQuery.isError}
						className="min-h-11 rounded-md border border-slate-gray/30 bg-white dark:bg-[#111827] px-3 py-2"
					>
						<option value="">Start without a template</option>
						{templates.map((template) => <option key={template.id} value={template.id}>{template.name}</option>)}
					</select>
					{templatesQuery.isError && <span className="text-xs font-normal text-error-red">Templates could not be loaded. You can still create the event manually.</span>}
					{selectedTemplate && templateTimingSummary(selectedTemplate) && <span className="text-xs font-normal text-slate-gray dark:text-[#cbd5e1]">{templateTimingSummary(selectedTemplate)}</span>}
				</label>
				<label className="flex flex-col gap-1 text-sm font-medium text-navy dark:text-[#f8fafc]">
					Event type
					<select value={eventType} onChange={(event) => setEventType(event.target.value as EventType)} className="min-h-11 rounded-md border border-slate-gray/30 bg-white dark:bg-[#111827] px-3 py-2">
						<option value="COMPETITION">Game / match / competition</option>
						<option value="PRACTICE">Practice</option>
						<option value="MEETING">Meeting</option>
						<option value="TOURNAMENT">Tournament item</option>
						<option value="OTHER">Other</option>
					</select>
				</label>
				<label className="flex flex-col gap-1 text-sm font-medium text-navy dark:text-[#f8fafc]">
					Title
					<input value={title} onChange={(event) => setTitle(event.target.value)} placeholder="Optional custom title" maxLength={200} className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2" />
				</label>
				<label className="flex flex-col gap-1 text-sm font-medium text-navy dark:text-[#f8fafc] md:col-span-2">
					Description
					<textarea value={description} onChange={(event) => setDescription(event.target.value)} maxLength={2000} rows={3} className="rounded-md border border-slate-gray/30 px-3 py-2" />
				</label>
				<label className="flex flex-col gap-1 text-sm font-medium text-navy dark:text-[#f8fafc]">
					Opponent
					<input value={opponentName} onChange={(event) => setOpponentName(event.target.value)} placeholder="External opponent name" maxLength={120} className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2" />
				</label>
				<label className="flex flex-col gap-1 text-sm font-medium text-navy dark:text-[#f8fafc]">
					Timezone
					<input
						required
						value={timezone}
						onChange={(event) => {
							setTimezone(event.target.value);
							setTimezoneTouched(true);
						}}
						maxLength={100}
						className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2"
					/>
				</label>
				<label className="flex min-h-11 items-center gap-2 text-sm font-medium text-navy dark:text-[#f8fafc] md:col-span-2">
					<input
						type="checkbox"
						checked={isAllDay}
						onChange={(event) => setIsAllDay(event.target.checked)}
						className="h-4 w-4"
					/>
					All-day event
				</label>
				{isAllDay ? (
					<label className="flex flex-col gap-1 text-sm font-medium text-navy dark:text-[#f8fafc]">
						Date
						<input type="date" value={allDayDate} onChange={(event) => setAllDayDate(event.target.value)} className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2" />
					</label>
				) : (
					<>
						<label className="flex flex-col gap-1 text-sm font-medium text-navy dark:text-[#f8fafc]">
							Start time
							<input type="datetime-local" value={startAt} onChange={(event) => setStartAt(event.target.value)} className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2" />
						</label>
						<label className="flex flex-col gap-1 text-sm font-medium text-navy dark:text-[#f8fafc]">
							End time
							<input type="datetime-local" value={endAt} onChange={(event) => setEndAt(event.target.value)} className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2" />
						</label>
						<label className="flex flex-col gap-1 text-sm font-medium text-navy dark:text-[#f8fafc]">
							Arrival time
							<input type="datetime-local" value={arrivalAt} onChange={(event) => setArrivalAt(event.target.value)} className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2" />
						</label>
						<label className="flex flex-col gap-1 text-sm font-medium text-navy dark:text-[#f8fafc]">
							Meeting time
							<input type="datetime-local" value={meetingAt} onChange={(event) => setMeetingAt(event.target.value)} className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2" />
						</label>
					</>
				)}
				<label className="flex flex-col gap-1 text-sm font-medium text-navy dark:text-[#f8fafc]">
					Venue
					<input value={venueName} onChange={(event) => setVenueName(event.target.value)} maxLength={200} className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2" />
				</label>
				<label className="flex flex-col gap-1 text-sm font-medium text-navy dark:text-[#f8fafc]">
					Field / court / area
					<input value={area} onChange={(event) => setArea(event.target.value)} maxLength={120} className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2" />
				</label>
				<label className="flex flex-col gap-1 text-sm font-medium text-navy dark:text-[#f8fafc] md:col-span-2">
					Address
					<input value={address} onChange={(event) => setAddress(event.target.value)} maxLength={300} className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2" />
				</label>
				<label className="flex flex-col gap-1 text-sm font-medium text-navy dark:text-[#f8fafc] md:col-span-2">
					Meeting point
					<input value={meetingPoint} onChange={(event) => setMeetingPoint(event.target.value)} maxLength={300} className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2" />
				</label>
				<label className="flex flex-col gap-1 text-sm font-medium text-navy dark:text-[#f8fafc] md:col-span-2">
					Directions notes
					<textarea value={directionsNotes} onChange={(event) => setDirectionsNotes(event.target.value)} maxLength={1000} rows={3} className="rounded-md border border-slate-gray/30 px-3 py-2" />
				</label>
				<label className="flex flex-col gap-1 text-sm font-medium text-navy dark:text-[#f8fafc]">
					Visibility
					<select value={visibility} onChange={(event) => setVisibility(event.target.value as EventVisibility)} className="min-h-11 rounded-md border border-slate-gray/30 bg-white dark:bg-[#111827] px-3 py-2">
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
