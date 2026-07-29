import type { FeeAssignmentStatus } from "./types";

export const STATUS_LABELS: Record<FeeAssignmentStatus, string> = {
	OPEN: "Open",
	PARTIALLY_PAID: "Partially paid",
	PAID: "Paid",
	WAIVED: "Waived",
	CANCELLED: "Cancelled",
};

export const STATUS_COLORS: Record<FeeAssignmentStatus, string> = {
	OPEN: "bg-amber-100 text-amber-800",
	PARTIALLY_PAID: "bg-blue-100 text-blue-800",
	PAID: "bg-green-100 text-green-800",
	WAIVED: "bg-slate-100 text-slate-600",
	CANCELLED: "bg-red-100 text-red-700",
};
