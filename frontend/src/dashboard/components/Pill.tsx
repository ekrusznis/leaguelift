type PillTone = "success" | "warning" | "error" | "info" | "neutral";

const TONE_STYLES: Record<PillTone, string> = {
	success: "bg-green-500/12 text-success-700",
	warning: "bg-gold-500/15 text-warning-600",
	error: "bg-error-600/10 text-error-600",
	info: "bg-info-600/10 text-info-600",
	neutral: "bg-slate-200 text-slate-700",
};

export function Pill({ tone = "neutral", children }: { tone?: PillTone; children: string }) {
	return <span className={`inline-flex items-center rounded-full px-2.5 py-1 text-xs font-semibold ${TONE_STYLES[tone]}`}>{children}</span>;
}
