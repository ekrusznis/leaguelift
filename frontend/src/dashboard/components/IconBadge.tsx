import type { ReactNode } from "react";

type Tone = "success" | "warning" | "error" | "info" | "neutral" | "purple";

const TONE_STYLES: Record<Tone, string> = {
	success: "bg-green-500/15 text-green-600",
	warning: "bg-gold-500/15 text-warning-600",
	error: "bg-error-600/12 text-error-600",
	info: "bg-info-600/12 text-info-600",
	neutral: "bg-slate-200 dark:bg-slate-700 text-slate-600 dark:text-[#cbd5e1]",
	purple: "bg-purple-500/12 text-purple-600",
};

/** Colored circular icon badge used in list rows (e.g. Attention Required, Announcements). */
export function IconBadge({ icon, tone = "neutral" }: { icon: ReactNode; tone?: Tone }) {
	return <span className={`flex size-9 shrink-0 items-center justify-center rounded-full ${TONE_STYLES[tone]}`}>{icon}</span>;
}
