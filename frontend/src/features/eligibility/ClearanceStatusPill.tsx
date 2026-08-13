import type { ClearanceStatus } from "./types";

export const CLEARANCE_LABELS: Record<ClearanceStatus, string> = {
	ROSTER_PENDING: "Not yet reviewed",
	DOCUMENTS_REQUIRED: "Documents required",
	UNDER_REVIEW: "Under review",
	CLEARED: "Cleared",
	EXPIRED: "Expired",
	INELIGIBLE: "Ineligible",
};

export const CLEARANCE_CLASSES: Record<ClearanceStatus, string> = {
	ROSTER_PENDING: "bg-slate-gray/10 text-slate-gray dark:text-[#cbd5e1]",
	DOCUMENTS_REQUIRED: "bg-championship-gold/10 text-championship-gold",
	UNDER_REVIEW: "bg-info-blue/10 text-info-blue",
	CLEARED: "bg-victory-green/10 text-victory-green",
	EXPIRED: "bg-error-red/10 text-error-red",
	INELIGIBLE: "bg-error-red/10 text-error-red",
};

/** Statuses that mean an athlete is genuinely blocked or lapsed, as opposed to just still in progress (DOCUMENTS_REQUIRED/UNDER_REVIEW/ROSTER_PENDING). Used to decide whether a screen's "ineligible" filter/count should include a given athlete. */
export const INELIGIBLE_STATUSES: ReadonlySet<ClearanceStatus> = new Set(["INELIGIBLE", "EXPIRED"]);

/** Shared eligibility status pill — same visual language everywhere an athlete's clearance status is shown (team roster, household participants, event RSVP lists). */
export function ClearanceStatusPill({ status }: { status: ClearanceStatus }) {
	return <span className={`rounded-full px-2 py-0.5 text-xs font-medium ${CLEARANCE_CLASSES[status]}`}>{CLEARANCE_LABELS[status]}</span>;
}
