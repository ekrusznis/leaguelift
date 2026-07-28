import type { AvailabilityBadge } from "../content/solutions";

const AVAILABILITY_STYLES: Record<AvailabilityBadge, string> = {
	"Available in Initial Release": "bg-green-500/15 text-green-600 border-green-500/30",
	"Pilot Workflow": "bg-info-600/10 text-info-600 border-info-600/30",
	Planned: "bg-gold-500/15 text-warning-600 border-gold-500/40",
	Future: "bg-slate-200 text-slate-700 border-slate-300",
};

export function AvailabilityStatusBadge({ status }: { status: AvailabilityBadge }) {
	return (
		<span
			className={`inline-flex items-center rounded-full border px-3 py-1 text-xs font-semibold ${AVAILABILITY_STYLES[status]}`}
		>
			{status}
		</span>
	);
}

export function StatusBadge({ tone = "neutral", children }: { tone?: "neutral" | "success" | "warning"; children: string }) {
	const styles = {
		neutral: "bg-slate-200 text-slate-700",
		success: "bg-green-500/15 text-green-600",
		warning: "bg-gold-500/15 text-warning-600",
	}[tone];

	return <span className={`inline-flex items-center rounded-full px-3 py-1 text-xs font-semibold ${styles}`}>{children}</span>;
}
