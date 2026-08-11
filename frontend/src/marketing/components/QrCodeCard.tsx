/**
 * Placeholder QR-code visual for solution pages that reference QR-code promotion
 * (team/tournament pages). Real QR generation belongs to the org/team/tournament
 * public-page feature (already implemented for Phase 1 slugs) — this is
 * illustrative only, not a functioning generator.
 */
export function QrCodeCard({ label }: { label: string }) {
	return (
		<div className="flex items-center gap-4 rounded-2xl border border-slate-200 dark:border-[#334155] bg-white dark:bg-[#111827] p-5 shadow-[0_12px_34px_rgba(11,31,51,0.10)]">
			<svg viewBox="0 0 64 64" className="size-16 shrink-0 text-navy-900 dark:text-[#f8fafc]" aria-hidden="true">
				<rect x="4" y="4" width="20" height="20" rx="2" fill="none" stroke="currentColor" strokeWidth="3" />
				<rect x="40" y="4" width="20" height="20" rx="2" fill="none" stroke="currentColor" strokeWidth="3" />
				<rect x="4" y="40" width="20" height="20" rx="2" fill="none" stroke="currentColor" strokeWidth="3" />
				<rect x="10" y="10" width="8" height="8" fill="currentColor" />
				<rect x="46" y="10" width="8" height="8" fill="currentColor" />
				<rect x="10" y="46" width="8" height="8" fill="currentColor" />
				<rect x="36" y="36" width="6" height="6" fill="currentColor" />
				<rect x="48" y="36" width="6" height="6" fill="currentColor" />
				<rect x="36" y="48" width="6" height="6" fill="currentColor" />
				<rect x="54" y="48" width="6" height="6" fill="currentColor" />
			</svg>
			<p className="text-sm font-medium text-slate-700 dark:text-[#cbd5e1]">{label}</p>
		</div>
	);
}
