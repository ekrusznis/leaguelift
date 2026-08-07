/**
 * Phase 24 slice 24.5 (ADR-071): a fixed dd/mm/yyyy HH:mm <zone> display for an event's
 * instant, always rendered in the EVENT's own IANA timezone (never the viewer's browser
 * zone) and always in this fixed day-first order regardless of the viewer's locale —
 * deliberately not using `Intl`'s locale-dependent month/day ordering, since the point
 * of this display is an unambiguous, always-the-same-shape timestamp next to the zone
 * it belongs to.
 */
export function formatEventDateTime(instant: string | null, timezone: string): string | null {
	if (!instant) return null;
	const date = new Date(instant);
	const parts = new Intl.DateTimeFormat("en-GB", {
		timeZone: timezone,
		year: "numeric",
		month: "2-digit",
		day: "2-digit",
		hour: "2-digit",
		minute: "2-digit",
		hourCycle: "h23",
	}).formatToParts(date);
	const get = (type: string) => parts.find((part) => part.type === type)?.value ?? "";
	const zoneAbbreviation =
		new Intl.DateTimeFormat("en-US", { timeZone: timezone, timeZoneName: "short" })
			.formatToParts(date)
			.find((part) => part.type === "timeZoneName")?.value ?? timezone;
	return `${get("day")}/${get("month")}/${get("year")} ${get("hour")}:${get("minute")} ${zoneAbbreviation}`;
}

/**
 * An all-day date must never round-trip through `new Date(isoString)` — that reintroduces
 * a shift bug in negative-UTC-offset browsers (the same class of bug ADR-071 documents for
 * `toInstant`). [allDayDate] is already a plain `YYYY-MM-DD` string from the backend, so it
 * is formatted directly as dd/mm/yyyy with no zone conversion at all.
 */
export function formatEventAllDayDate(allDayDate: string): string {
	const [year, month, day] = allDayDate.split("-");
	return `${day}/${month}/${year}`;
}
