export function LoadingState({ label = "Loading…" }: { label?: string }) {
	return (
		<div role="status" aria-live="polite" className="flex items-center gap-3 p-6 text-slate-gray">
			<span
				aria-hidden="true"
				className="h-4 w-4 animate-spin rounded-full border-2 border-slate-gray border-t-transparent"
			/>
			<span>{label}</span>
		</div>
	);
}
