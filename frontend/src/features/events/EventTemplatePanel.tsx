import { useState } from "react";
import { Button } from "../../components/Button";
import { EmptyState } from "../../components/states/EmptyState";
import { ErrorState } from "../../components/states/ErrorState";
import { LoadingState } from "../../components/states/LoadingState";
import { useArchiveEventTemplate, useCreateEventTemplate, useEventTemplates, useUpdateEventTemplate } from "./api";
import type { EventTemplate, EventType, EventVisibility, SaveEventTemplateInput } from "./types";

const EVENT_TYPE_LABELS: Record<EventType, string> = {
	COMPETITION: "Game / match / competition",
	TOURNAMENT: "Tournament item",
	PRACTICE: "Practice",
	MEETING: "Meeting",
	OTHER: "Other",
};

function timingSummary(template: EventTemplate) {
	const parts: string[] = [];
	if (template.durationMinutes != null) parts.push(`${template.durationMinutes} min duration`);
	if (template.arrivalOffsetMinutes != null) parts.push(`arrive ${template.arrivalOffsetMinutes} min early`);
	if (template.meetingOffsetMinutes != null) parts.push(`meet ${template.meetingOffsetMinutes} min early`);
	return parts.join(" · ") || "No automatic timing offsets";
}

export function EventTemplatePanel({ organizationId }: { organizationId: string }) {
	const templatesQuery = useEventTemplates(organizationId, true);
	const createTemplate = useCreateEventTemplate(organizationId);
	const updateTemplate = useUpdateEventTemplate(organizationId);
	const archiveTemplate = useArchiveEventTemplate(organizationId);
	const [editing, setEditing] = useState<EventTemplate | null>(null);
	const [showForm, setShowForm] = useState(false);
	const [formError, setFormError] = useState<string | null>(null);
	const [archiveError, setArchiveError] = useState<string | null>(null);

	if (templatesQuery.isLoading) return <LoadingState label="Loading event templates…" />;
	if (templatesQuery.isError) return <ErrorState message="Could not load event templates." onRetry={() => templatesQuery.refetch()} />;

	const templates = templatesQuery.data ?? [];
	const active = templates.filter((template) => template.status === "ACTIVE");
	const archived = templates.filter((template) => template.status === "ARCHIVED");

	function closeForm() {
		setEditing(null);
		setShowForm(false);
		setFormError(null);
	}

	return (
		<div className="rounded-xl border border-slate-gray/20 bg-pure-white p-4">
			<div className="flex flex-wrap items-start justify-between gap-3">
				<div>
					<h3 className="font-heading text-lg font-semibold text-navy">Reusable event templates</h3>
					<p className="mt-1 max-w-3xl text-sm text-slate-gray">
						Save common practice, game, meeting, and tournament defaults. Selecting a template pre-fills a normal draft event; it never creates a recurring schedule or publishes automatically.
					</p>
				</div>
				<Button
					type="button"
					variant="secondary"
					onClick={() => {
						setEditing(null);
						setShowForm((value) => !value);
						setFormError(null);
					}}
				>
					{showForm && editing == null ? "Cancel" : "New template"}
				</Button>
			</div>

			{showForm && (
				<div className="mt-4">
					<EventTemplateForm
						key={editing?.id ?? "new"}
						template={editing}
						isSubmitting={createTemplate.isPending || updateTemplate.isPending}
						error={formError}
						onCancel={closeForm}
						onSubmit={async (input) => {
							setFormError(null);
							try {
								if (editing) {
									await updateTemplate.mutateAsync({ templateId: editing.id, input });
								} else {
									await createTemplate.mutateAsync(input);
								}
								closeForm();
							} catch {
								setFormError("Could not save the template. Check the values and make sure the active template name is unique.");
							}
						}}
					/>
				</div>
			)}

			{archiveError && <p role="alert" className="mt-3 text-sm text-error-red">{archiveError}</p>}

			<div className="mt-5">
				{active.length === 0 ? (
					<EmptyState title="No active templates" description="Create a reusable set of defaults for the events your organization enters most often." />
				) : (
					<ul className="flex flex-col gap-3" aria-label="Active event templates">
						{active.map((template) => (
							<li key={template.id} className="rounded-lg border border-slate-gray/20 bg-ice-white p-4">
								<div className="flex flex-wrap items-start justify-between gap-3">
									<div className="min-w-0 flex-1">
										<div className="flex flex-wrap items-center gap-2">
											<p className="break-words font-heading font-semibold text-navy">{template.name}</p>
											<span className="rounded-full bg-info-50 px-2 py-0.5 text-xs font-medium text-info-700">{EVENT_TYPE_LABELS[template.eventType]}</span>
										</div>
										<p className="mt-1 text-sm text-slate-gray">{timingSummary(template)}</p>
										<p className="text-sm text-slate-gray">
											{[template.venueName, template.area, template.address].filter(Boolean).join(" · ") || "No default location"}
										</p>
									</div>
									<div className="flex shrink-0 flex-wrap gap-2">
										<Button
											type="button"
											variant="secondary"
											onClick={() => {
												setEditing(template);
												setShowForm(true);
												setFormError(null);
											}}
										>
											Edit
										</Button>
										<Button
											type="button"
											variant="danger"
											disabled={archiveTemplate.isPending}
											onClick={async () => {
												if (!window.confirm(`Archive the "${template.name}" template? Existing events are not changed.`)) return;
												setArchiveError(null);
												try {
													await archiveTemplate.mutateAsync(template.id);
												} catch {
													setArchiveError("Could not archive this template. Refresh and try again.");
												}
											}}
										>
											Archive
										</Button>
									</div>
								</div>
							</li>
						))}
					</ul>
				)}
			</div>

			{archived.length > 0 && (
				<details className="mt-4 rounded-lg border border-slate-gray/20 p-3">
					<summary className="cursor-pointer font-medium text-navy">Archived templates ({archived.length})</summary>
					<ul className="mt-3 flex flex-col gap-2" aria-label="Archived event templates">
						{archived.map((template) => (
							<li key={template.id} className="rounded-md bg-slate-50 px-3 py-2 text-sm text-slate-gray">
								<span className="font-medium text-navy">{template.name}</span> · {EVENT_TYPE_LABELS[template.eventType]}
							</li>
						))}
					</ul>
				</details>
			)}
		</div>
	);
}

function optionalNumber(value: string) {
	if (!value.trim()) return null;
	return Number(value);
}

function optionalText(value: string) {
	return value.trim() || null;
}

function EventTemplateForm({
	template,
	isSubmitting,
	error,
	onCancel,
	onSubmit,
}: {
	template: EventTemplate | null;
	isSubmitting: boolean;
	error: string | null;
	onCancel: () => void;
	onSubmit: (input: SaveEventTemplateInput) => Promise<void>;
}) {
	const [name, setName] = useState(template?.name ?? "");
	const [eventType, setEventType] = useState<EventType>(template?.eventType ?? "PRACTICE");
	const [title, setTitle] = useState(template?.title ?? "");
	const [description, setDescription] = useState(template?.description ?? "");
	const [durationMinutes, setDurationMinutes] = useState(template?.durationMinutes?.toString() ?? "");
	const [arrivalOffsetMinutes, setArrivalOffsetMinutes] = useState(template?.arrivalOffsetMinutes?.toString() ?? "");
	const [meetingOffsetMinutes, setMeetingOffsetMinutes] = useState(template?.meetingOffsetMinutes?.toString() ?? "");
	const [timezone, setTimezone] = useState(template?.timezone ?? Intl.DateTimeFormat().resolvedOptions().timeZone ?? "America/New_York");
	const [venueName, setVenueName] = useState(template?.venueName ?? "");
	const [address, setAddress] = useState(template?.address ?? "");
	const [area, setArea] = useState(template?.area ?? "");
	const [meetingPoint, setMeetingPoint] = useState(template?.meetingPoint ?? "");
	const [directionsNotes, setDirectionsNotes] = useState(template?.directionsNotes ?? "");
	const [visibility, setVisibility] = useState<EventVisibility>(template?.visibility ?? "TEAM");

	return (
		<form
			className="rounded-xl border border-slate-gray/20 bg-ice-white p-4"
			onSubmit={async (event) => {
				event.preventDefault();
				await onSubmit({
					name: name.trim(),
					eventType,
					title: optionalText(title),
					description: optionalText(description),
					durationMinutes: optionalNumber(durationMinutes),
					arrivalOffsetMinutes: optionalNumber(arrivalOffsetMinutes),
					meetingOffsetMinutes: optionalNumber(meetingOffsetMinutes),
					timezone: timezone.trim(),
					venueName: optionalText(venueName),
					address: optionalText(address),
					area: optionalText(area),
					meetingPoint: optionalText(meetingPoint),
					directionsNotes: optionalText(directionsNotes),
					visibility,
				});
			}}
		>
			<h4 className="font-heading text-base font-semibold text-navy">{template ? `Edit ${template.name}` : "New event template"}</h4>
			<div className="mt-4 grid gap-3 md:grid-cols-2">
				<label className="flex flex-col gap-1 text-sm font-medium text-navy">
					Template name
					<input required minLength={2} maxLength={120} value={name} onChange={(event) => setName(event.target.value)} className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2" />
				</label>
				<label className="flex flex-col gap-1 text-sm font-medium text-navy">
					Event type
					<select value={eventType} onChange={(event) => setEventType(event.target.value as EventType)} className="min-h-11 rounded-md border border-slate-gray/30 bg-white px-3 py-2">
						{Object.entries(EVENT_TYPE_LABELS).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
					</select>
				</label>
				<label className="flex flex-col gap-1 text-sm font-medium text-navy md:col-span-2">
					Default title
					<input maxLength={200} value={title} onChange={(event) => setTitle(event.target.value)} className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2" />
				</label>
				<label className="flex flex-col gap-1 text-sm font-medium text-navy md:col-span-2">
					Default description
					<textarea maxLength={2000} rows={3} value={description} onChange={(event) => setDescription(event.target.value)} className="rounded-md border border-slate-gray/30 px-3 py-2" />
				</label>
				<label className="flex flex-col gap-1 text-sm font-medium text-navy">
					Duration in minutes
					<input type="number" min={1} max={1440} value={durationMinutes} onChange={(event) => setDurationMinutes(event.target.value)} className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2" />
				</label>
				<label className="flex flex-col gap-1 text-sm font-medium text-navy">
					Arrival minutes before start
					<input type="number" min={0} max={1440} value={arrivalOffsetMinutes} onChange={(event) => setArrivalOffsetMinutes(event.target.value)} className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2" />
				</label>
				<label className="flex flex-col gap-1 text-sm font-medium text-navy">
					Meeting minutes before start
					<input type="number" min={0} max={1440} value={meetingOffsetMinutes} onChange={(event) => setMeetingOffsetMinutes(event.target.value)} className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2" />
				</label>
				<label className="flex flex-col gap-1 text-sm font-medium text-navy">
					Timezone
					<input required maxLength={100} value={timezone} onChange={(event) => setTimezone(event.target.value)} className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2" />
				</label>
				<label className="flex flex-col gap-1 text-sm font-medium text-navy">
					Venue
					<input maxLength={200} value={venueName} onChange={(event) => setVenueName(event.target.value)} className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2" />
				</label>
				<label className="flex flex-col gap-1 text-sm font-medium text-navy">
					Field / court / area
					<input maxLength={120} value={area} onChange={(event) => setArea(event.target.value)} className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2" />
				</label>
				<label className="flex flex-col gap-1 text-sm font-medium text-navy md:col-span-2">
					Address
					<input maxLength={300} value={address} onChange={(event) => setAddress(event.target.value)} className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2" />
				</label>
				<label className="flex flex-col gap-1 text-sm font-medium text-navy md:col-span-2">
					Meeting point
					<input maxLength={300} value={meetingPoint} onChange={(event) => setMeetingPoint(event.target.value)} className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2" />
				</label>
				<label className="flex flex-col gap-1 text-sm font-medium text-navy md:col-span-2">
					Directions notes
					<textarea maxLength={1000} rows={3} value={directionsNotes} onChange={(event) => setDirectionsNotes(event.target.value)} className="rounded-md border border-slate-gray/30 px-3 py-2" />
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
				<Button type="submit" disabled={isSubmitting}>{isSubmitting ? "Saving…" : "Save template"}</Button>
			</div>
		</form>
	);
}
