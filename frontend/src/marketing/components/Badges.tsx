export function StatusBadge({ tone = "neutral", children }: { tone?: "neutral" | "success" | "warning"; children: string }) {
	const styles = {
		neutral: "bg-slate-200 dark:bg-slate-700 text-slate-700 dark:text-[#cbd5e1]",
		success: "bg-green-500/15 text-green-600",
		warning: "bg-gold-500/15 text-warning-600",
	}[tone];

	return <span className={`inline-flex shrink-0 items-center whitespace-nowrap rounded-full px-3 py-1 text-xs font-semibold ${styles}`}>{children}</span>;
}
