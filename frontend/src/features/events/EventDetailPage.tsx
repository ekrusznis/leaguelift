import { useState } from "react";
import { Link, useParams, useSearchParams } from "react-router-dom";
import { Button } from "../../components/Button";
import { Capabilities } from "../../authorization/capabilityConstants";
import { useContexts } from "../../authorization/api";
import { hasCapability } from "../../authorization/capabilities";
import { ErrorState } from "../../components/states/ErrorState";
import { LoadingState } from "../../components/states/LoadingState";
import { useParticipants, useParticipantTeams } from "../households/api";
import type { Participant } from "../households/types";
import { ApiError } from "../../lib/apiError";
import {
	downloadEventCalendar,
	useCancelEvent,
	useDetachEvent,
	useDirections,
	useEvent,
	useEventRsvps,
	usePostponeEvent,
	usePublishEvent,
	useSubmitRsvp,
} from "./api";
import type { Rally26Event, RsvpResponse } from "./types";
import { ReminderButton } from "../communications/ReminderButton";
import { formatEventAllDayDate, formatEventDateTime } from "./formatEventDateTime";

function formatDateTime(value: string | null, timezone: string) {
	if (!value) return "To be determined";
	return new Intl.DateTimeFormat("en-US", { dateStyle: "full", timeStyle: "short", timeZone: timezone }).format(new Date(value));
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

export function EventDetailPage() {
	const { organizationId, eventId } = useParams<{ organizationId: string; eventId: string }>();
	const [searchParams] = useSearchParams();
	const householdId = searchParams.get("householdId") ?? undefined;
	const selfParticipantId = searchParams.get("participantId") ?? undefined;
	const returnTo = searchParams.get("returnTo") ?? "/app";
	const eventQuery = useEvent(organizationId ?? "", eventId ?? "");
	const rsvps = useEventRsvps(organizationId ?? "", eventId ?? "");
	const directions = useDirections(organizationId ?? "", eventId ?? "");
	const participants = useParticipants(organizationId ?? "", householdId ?? "");
	const submitRsvp = useSubmitRsvp(organizationId ?? "", eventId ?? "");
	const publish = usePublishEvent(organizationId ?? "", eventId ?? "");
	const cancel = useCancelEvent(organizationId ?? "", eventId ?? "");
	const postpone = usePostponeEvent(organizationId ?? "", eventId ?? "");
	const detach = useDetachEvent(organizationId ?? "", eventId ?? "");
	const [actionError, setActionError] = useState<string | null>(null);
	const contexts = useContexts();

	if (!organizationId || !eventId) return <ErrorState message="Invalid event URL." />;
	if (eventQuery.isLoading) return <LoadingState label="Loading event…" />;
	if (eventQuery.isError || !eventQuery.data) return <ErrorState message="Could not load this event." onRetry={() => eventQuery.refetch()} />;

	const event = eventQuery.data;
	const safeReturnTo = returnTo.startsWith("/app") ? returnTo : "/app";
	const canManage =
		hasCapability(contexts.data, Capabilities.ORG_EVENT_MANAGE, { contextType: "ORGANIZATION", resourceId: organizationId }) ||
		(event.teamId !== null && hasCapability(contexts.data, Capabilities.TEAM_EVENT_MANAGE, { contextType: "TEAM", resourceId: event.teamId })) ||
		(event.tournamentId !== null && hasCapability(contexts.data, Capabilities.TOURNAMENT_EVENT_MANAGE, { contextType: "TOURNAMENT", resourceId: event.tournamentId }));
	const rsvpParticipants = householdId ? participants.data ?? [] : selfParticipantId ? [{ id: selfParticipantId, firstName: "My", lastName: "RSVP" }] : [];

	async function runAction(action: () => Promise<unknown>) {
		setActionError(null);
		try {
			await action();
		} catch (error) {
			setActionError(error instanceof ApiError ? error.message : "This action could not be completed.");
		}
	}

	async function downloadCalendar() {
		await runAction(async () => {
			const file = await downloadEventCalendar(organizationId!, eventId!);
			saveBlob(file.blob, file.filename);
		});
	}

	return (
		<div className="flex flex-col gap-6">
			<div>
				<Link to={safeReturnTo} className="mb-2 inline-block text-sm text-azure-blue hover:underline">← Back to schedule</Link>
				<div className="flex flex-wrap items-start justify-between gap-4">
					<div>
						<h1 className="font-heading text-2xl font-bold text-navy">{event.displayTitle}</h1>
						<p className="mt-1 text-slate-gray">{event.eventType} · {event.status}</p>
						<p className="mt-0.5 text-xs text-slate-gray/80">
							{event.allDayDate
								? `${formatEventAllDayDate(event.allDayDate)} (all day)`
								: (formatEventDateTime(event.startAt, event.timezone) ?? "To be determined")}
						</p>
					</div>
					<div className="flex flex-wrap gap-2">
						<Button type="button" variant="secondary" onClick={downloadCalendar}>Add to calendar</Button>
						{directions.data?.url && (
							<a href={directions.data.url} target="_blank" rel="noreferrer" className="inline-flex min-h-11 items-center rounded-md border border-slate-gray/30 bg-pure-white px-4 py-2 text-sm font-medium text-navy hover:bg-ice-white">
								Open directions
							</a>
						)}
					</div>
				</div>
			</div>

			{actionError && <p role="alert" className="text-sm text-error-red">{actionError}</p>}

			<div className="grid gap-5 lg:grid-cols-2">
				<section className="rounded-xl border border-slate-gray/20 bg-pure-white p-5">
					<h2 className="font-heading text-lg font-semibold text-navy">Event details</h2>
					<dl className="mt-4 grid gap-3 text-sm sm:grid-cols-2">
						<Detail label="Starts" value={event.allDayDate ? `${formatEventAllDayDate(event.allDayDate)} (all day)` : formatDateTime(event.startAt, event.timezone)} />
						<Detail label="Ends" value={event.allDayDate ? `${formatEventAllDayDate(event.allDayDate)} (all day)` : formatDateTime(event.endAt, event.timezone)} />
						<Detail label="Arrival" value={formatDateTime(event.arrivalAt, event.timezone)} />
						<Detail label="Meeting time" value={formatDateTime(event.meetingAt, event.timezone)} />
						<Detail label="Venue" value={event.venueName ?? "To be determined"} />
						<Detail label="Field / court / area" value={event.area ?? "To be determined"} />
						<Detail label="Address" value={event.address ?? "To be determined"} />
						<Detail label="Meeting point" value={event.meetingPoint ?? "Not specified"} />
					</dl>
					{event.description && <p className="mt-4 whitespace-pre-wrap text-sm text-slate-gray">{event.description}</p>}
					{event.directionsNotes && <p className="mt-3 rounded-lg bg-ice-white p-3 text-sm text-slate-gray"><strong className="text-navy">Directions notes:</strong> {event.directionsNotes}</p>}
				</section>

				<section className="rounded-xl border border-slate-gray/20 bg-pure-white p-5">
					<h2 className="font-heading text-lg font-semibold text-navy">RSVP</h2>
					{rsvps.isLoading && <LoadingState label="Loading RSVP status…" />}
					{rsvps.isError && <p className="mt-3 text-sm text-slate-gray">RSVP status is not available for this event or account.</p>}
					{rsvps.data && (
						<div className="mt-4 grid grid-cols-2 gap-3 sm:grid-cols-4">
							<RsvpCount label="Attending" value={rsvps.data.summary.attending} />
							<RsvpCount label="Not attending" value={rsvps.data.summary.notAttending} />
							<RsvpCount label="Maybe" value={rsvps.data.summary.maybe} />
							<RsvpCount label="No response" value={rsvps.data.summary.noResponse} />
						</div>
					)}
					{rsvpParticipants.length > 0 && (
						<div className="mt-5 flex flex-col gap-4">
							{rsvpParticipants.map((participant) => (
								<RsvpParticipantControls
									key={participant.id}
									organizationId={organizationId}
									event={event}
									participant={participant}
									isSelf={participant.id === selfParticipantId}
									isSubmitting={submitRsvp.isPending}
									onRespond={(response) => runAction(() => submitRsvp.mutateAsync({ participantId: participant.id, response }))}
								/>
							))}
						</div>
					)}
					{rsvps.data && rsvps.data.responses.length > 0 && (
						<ul className="mt-5 flex flex-col gap-2" aria-label="Individual RSVP responses">
							{rsvps.data.responses.map((response) => (
								<li key={response.id} className="flex items-center justify-between rounded-lg bg-ice-white px-3 py-2 text-sm">
									<span className="text-slate-gray">{response.participantName ?? `Participant ${response.participantId.slice(0, 8)}`}</span>
									<span className="font-medium text-navy">{response.response.replace("_", " ")}</span>
								</li>
							))}
						</ul>
					)}
				</section>
			</div>

			{canManage && <EventManagementActions organizationId={organizationId} event={event} publish={() => runAction(() => publish.mutateAsync())} cancel={() => runAction(() => cancel.mutateAsync())} postpone={() => runAction(() => postpone.mutateAsync())} detach={() => runAction(() => detach.mutateAsync())} />}
		</div>
	);
}


function RsvpParticipantControls({
	organizationId,
	event,
	participant,
	isSelf,
	isSubmitting,
	onRespond,
}: {
	organizationId: string;
	event: Rally26Event;
	participant: Pick<Participant, "id" | "firstName" | "lastName">;
	isSelf: boolean;
	isSubmitting: boolean;
	onRespond: (response: RsvpResponse) => Promise<void>;
}) {
	const teams = useParticipantTeams(organizationId, participant.id);
	const relevantTeamIds = new Set([event.teamId, event.opponentTeamId].filter((id): id is string => id !== null));
	const isOnEventTeam = isSelf || (teams.data?.some((assignment) => assignment.status === "ACTIVE" && relevantTeamIds.has(assignment.teamId)) ?? false);

	if (!isSelf && teams.isLoading) return <LoadingState label={`Checking ${participant.firstName}'s team assignment…`} />;
	if (!isOnEventTeam) return null;

	return (
		<div className="rounded-lg border border-slate-gray/20 p-3">
			<p className="font-medium text-navy">{participant.firstName} {participant.lastName}</p>
			<div className="mt-3 flex flex-wrap gap-2">
				{(["ATTENDING", "NOT_ATTENDING", "MAYBE"] as RsvpResponse[]).map((response) => (
					<Button
						key={response}
						type="button"
						variant="secondary"
						disabled={isSubmitting}
						onClick={() => void onRespond(response)}
					>
						{response === "ATTENDING" ? "Attending" : response === "NOT_ATTENDING" ? "Not attending" : "Maybe"}
					</Button>
				))}
			</div>
		</div>
	);
}

function Detail({ label, value }: { label: string; value: string }) {
	return <div><dt className="text-xs font-medium uppercase tracking-wide text-slate-gray">{label}</dt><dd className="mt-1 text-navy">{value}</dd></div>;
}

function RsvpCount({ label, value }: { label: string; value: number }) {
	return <div className="rounded-lg bg-ice-white p-3 text-center"><p className="font-heading text-xl font-bold text-navy">{value}</p><p className="text-xs text-slate-gray">{label}</p></div>;
}

function EventManagementActions({ organizationId, event, publish, cancel, postpone, detach }: { organizationId: string; event: Rally26Event; publish: () => void; cancel: () => void; postpone: () => void; detach: () => void }) {
	return (
		<section className="rounded-xl border border-slate-gray/20 bg-pure-white p-5">
			<h2 className="font-heading text-lg font-semibold text-navy">Event actions</h2>
			<p className="mt-1 text-sm text-slate-gray">Only actions allowed by the backend for your role will succeed.</p>
			<div className="mt-4 flex flex-wrap gap-2">
				{event.status === "DRAFT" && <Button type="button" onClick={publish}>Publish</Button>}
				{event.status !== "DRAFT" && event.status !== "CANCELLED" && event.status !== "COMPLETED" && event.startAt && new Date(event.startAt).getTime() > Date.now() && (
					<ReminderButton organizationId={organizationId} resourceType="EVENT" resourceId={event.id} label="Send event reminder" />
				)}
				{event.status !== "CANCELLED" && event.status !== "COMPLETED" && <Button type="button" variant="secondary" onClick={postpone}>Postpone</Button>}
				{event.status !== "CANCELLED" && event.status !== "COMPLETED" && <Button type="button" variant="danger" onClick={cancel}>Cancel event</Button>}
				{event.sourceType !== "MANUAL" && <Button type="button" variant="secondary" onClick={detach}>Detach from source</Button>}
			</div>
		</section>
	);
}
