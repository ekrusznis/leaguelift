import type { DisputeStatus } from "./types";

export const STATUS_LABELS: Record<DisputeStatus, string> = {
	NEEDS_RESPONSE: "Needs response",
	UNDER_REVIEW: "Under review",
	WON: "Won",
	LOST: "Lost",
};

export const STATUS_COLORS: Record<DisputeStatus, string> = {
	NEEDS_RESPONSE: "bg-amber-100 text-amber-800",
	UNDER_REVIEW: "bg-blue-100 text-blue-800",
	WON: "bg-green-100 text-green-800",
	LOST: "bg-red-100 text-red-700",
};
