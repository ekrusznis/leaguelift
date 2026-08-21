/**
 * Pure date/grouping helpers for the messaging inbox and thread views. Mirrors
 * mobile/src/features/messaging/dateFormat.ts in spirit (separate package, same rules)
 * so the two platforms read the same way.
 */

function isSameDay(a: Date, b: Date): boolean {
	return a.getFullYear() === b.getFullYear() && a.getMonth() === b.getMonth() && a.getDate() === b.getDate();
}

function daysBefore(from: Date, days: number): Date {
	const d = new Date(from);
	d.setDate(d.getDate() - days);
	return d;
}

/** Inbox row timestamp: time-of-day today, "Yesterday", short weekday within the last week, else a short date. */
export function formatRelativeListTime(iso: string, now: Date = new Date()): string {
	const date = new Date(iso);
	if (isSameDay(date, now)) {
		return date.toLocaleTimeString("en-US", { hour: "numeric", minute: "2-digit" });
	}
	if (isSameDay(date, daysBefore(now, 1))) {
		return "Yesterday";
	}
	const diffDays = Math.floor((now.getTime() - date.getTime()) / 86_400_000);
	if (diffDays >= 0 && diffDays < 7) {
		return date.toLocaleDateString("en-US", { weekday: "short" });
	}
	return date.toLocaleDateString("en-US", { month: "short", day: "numeric" });
}

/** Thread date-separator label: TODAY / YESTERDAY / "AUG 18". */
export function dateSeparatorLabel(iso: string, now: Date = new Date()): string {
	const date = new Date(iso);
	if (isSameDay(date, now)) return "TODAY";
	if (isSameDay(date, daysBefore(now, 1))) return "YESTERDAY";
	return date.toLocaleDateString("en-US", { month: "short", day: "numeric" }).toUpperCase();
}

/** Whether two messages fall on different calendar days — used to decide where to insert a date separator. */
export function isDifferentDay(isoA: string, isoB: string): boolean {
	return !isSameDay(new Date(isoA), new Date(isoB));
}

const GROUPING_WINDOW_MS = 5 * 60 * 1000;

/**
 * Whether `current` should visually merge with `previous` (sender label suppressed) —
 * same sender, within a short window. Not day-aware: the renderer already inserts a
 * date separator between different days (see [isDifferentDay]), which naturally breaks
 * the visual group there regardless of this function's own return value.
 */
export function shouldGroupWithPrevious(
	current: { senderUserId: string; sentAt: string },
	previous: { senderUserId: string; sentAt: string } | undefined,
): boolean {
	if (!previous) return false;
	if (previous.senderUserId !== current.senderUserId) return false;
	return new Date(current.sentAt).getTime() - new Date(previous.sentAt).getTime() <= GROUPING_WINDOW_MS;
}
