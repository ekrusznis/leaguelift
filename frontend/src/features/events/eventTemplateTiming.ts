import type { EventTemplate } from "./types";

export type EventTemplateTimingDefaults = Pick<EventTemplate, "durationMinutes" | "arrivalOffsetMinutes" | "meetingOffsetMinutes">;

export function deriveTemplateTimes(startAt: string | null, template: EventTemplateTimingDefaults | null) {
	if (!startAt || !template) return { endAt: null, arrivalAt: null, meetingAt: null };
	const startMillis = Date.parse(startAt);
	if (!Number.isFinite(startMillis)) return { endAt: null, arrivalAt: null, meetingAt: null };
	const offset = (minutes: number, direction: 1 | -1) => new Date(startMillis + direction * minutes * 60_000).toISOString();
	return {
		endAt: template.durationMinutes == null ? null : offset(template.durationMinutes, 1),
		arrivalAt: template.arrivalOffsetMinutes == null ? null : offset(template.arrivalOffsetMinutes, -1),
		meetingAt: template.meetingOffsetMinutes == null ? null : offset(template.meetingOffsetMinutes, -1),
	};
}
