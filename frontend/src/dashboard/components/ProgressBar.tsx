export function ProgressBar({ percent, tone = "success" }: { percent: number; tone?: "success" | "neutral" }) {
	const clamped = Math.max(0, Math.min(100, percent));
	const fill = tone === "success" ? "bg-green-500" : "bg-info-600";

	return (
		<div className="h-2 w-full overflow-hidden rounded-full bg-slate-200 dark:bg-slate-700" role="progressbar" aria-valuenow={clamped} aria-valuemin={0} aria-valuemax={100}>
			<div className={`h-full rounded-full ${fill}`} style={{ width: `${clamped}%` }} />
		</div>
	);
}
