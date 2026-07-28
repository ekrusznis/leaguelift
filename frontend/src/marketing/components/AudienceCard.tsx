export function AudienceCard({ heading, copy }: { heading: string; copy?: string }) {
	return (
		<div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-[0_12px_34px_rgba(11,31,51,0.10)]">
			<h3 className="font-heading text-lg font-bold text-navy-900">{heading}</h3>
			{copy && <p className="mt-2 text-sm leading-relaxed text-slate-700">{copy}</p>}
		</div>
	);
}
