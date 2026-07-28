type SectionHeadingProps = {
	eyebrow?: string;
	heading: string;
	copy?: string;
	align?: "left" | "center";
	tone?: "dark" | "light";
};

export function SectionHeading({ eyebrow, heading, copy, align = "center", tone = "light" }: SectionHeadingProps) {
	const alignClass = align === "center" ? "mx-auto text-center" : "text-left";
	const headingColor = tone === "dark" ? "text-white" : "text-navy-900";
	const copyColor = tone === "dark" ? "text-slate-300" : "text-slate-700";

	return (
		<div className={`max-w-2xl ${alignClass}`}>
			{eyebrow && (
				<p className={`font-heading text-xs font-semibold uppercase tracking-wide ${tone === "dark" ? "text-green-400" : "text-green-600"}`}>
					{eyebrow}
				</p>
			)}
			<h2 className={`mt-2 text-balance font-heading text-3xl font-extrabold sm:text-4xl ${headingColor}`}>{heading}</h2>
			{copy && <p className={`mt-4 text-lg leading-relaxed ${copyColor}`}>{copy}</p>}
		</div>
	);
}
