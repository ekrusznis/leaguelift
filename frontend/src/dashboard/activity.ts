/** Turns an audit_event action like "household.adult.added" into "Household adult added". */
export function describeActivityAction(action: string): string {
	const words = action.split(/[._]/).join(" ");
	return words.charAt(0).toUpperCase() + words.slice(1);
}

/** Coarse relative-time label ("2h ago", "3d ago") for a recent ISO timestamp. */
export function timeAgo(iso: string): string {
	const diffMs = Date.now() - new Date(iso).getTime();
	const minutes = Math.floor(diffMs / 60_000);
	if (minutes < 1) return "just now";
	if (minutes < 60) return `${minutes}m ago`;
	const hours = Math.floor(minutes / 60);
	if (hours < 24) return `${hours}h ago`;
	const days = Math.floor(hours / 24);
	return `${days}d ago`;
}
