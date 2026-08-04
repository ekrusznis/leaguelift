import type { ReactNode } from "react";
import logoMarkLight from "../../assets/rally26-mark-light.svg";

type ToolCategory = { label: string; icon: ReactNode };

const TOOL_CATEGORIES: [ToolCategory, ToolCategory, ToolCategory, ToolCategory] = [
	{
		label: "Registration",
		icon: (
			<svg viewBox="0 0 24 24" fill="none" className="size-5" aria-hidden="true">
				<rect x="6" y="4" width="12" height="16" rx="2" stroke="currentColor" strokeWidth="1.6" />
				<path d="M9 4V3.5A1.5 1.5 0 0 1 10.5 2h3A1.5 1.5 0 0 1 15 3.5V4" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" />
				<path d="m9.5 13 1.8 1.8L14.5 11" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" />
			</svg>
		),
	},
	{
		label: "Scheduling",
		icon: (
			<svg viewBox="0 0 24 24" fill="none" className="size-5" aria-hidden="true">
				<rect x="4" y="5" width="16" height="15" rx="2" stroke="currentColor" strokeWidth="1.6" />
				<path d="M4 9.5h16M8 3v3.5M16 3v3.5" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" />
			</svg>
		),
	},
	{
		label: "Rosters",
		icon: (
			<svg viewBox="0 0 24 24" fill="none" className="size-5" aria-hidden="true">
				<circle cx="9" cy="8.5" r="2.5" stroke="currentColor" strokeWidth="1.6" />
				<path d="M4 19c.6-3 2.4-4.5 5-4.5s4.4 1.5 5 4.5" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" />
				<circle cx="16.5" cy="8" r="2" stroke="currentColor" strokeWidth="1.4" />
				<path d="M15 14.7c2.1.2 3.4 1.6 3.9 4.3" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" />
			</svg>
		),
	},
	{
		label: "Communication",
		icon: (
			<svg viewBox="0 0 24 24" fill="none" className="size-5" aria-hidden="true">
				<path
					d="M4 12c0-4.1 3.6-7.5 8-7.5s8 3.4 8 7.5-3.6 7.5-8 7.5c-.9 0-1.8-.15-2.6-.42L5 20l1.2-3.6C4.8 15.1 4 13.6 4 12Z"
					stroke="currentColor"
					strokeWidth="1.6"
					strokeLinejoin="round"
				/>
			</svg>
		),
	},
];

function ToolNode({ category }: { category: ToolCategory }) {
	return (
		<div className="flex w-[9.5rem] flex-col items-center gap-2 rounded-2xl border border-white/10 bg-white/[0.04] px-4 py-4 text-center">
			<div className="flex size-10 items-center justify-center rounded-full bg-white/10 text-slate-300">{category.icon}</div>
			<p className="text-sm font-semibold text-white">{category.label}</p>
		</div>
	);
}

function CenterNode() {
	return (
		<div className="flex flex-col items-center gap-2 rounded-2xl border border-green-500/40 bg-green-500/10 px-7 py-5 text-center shadow-[0_22px_60px_rgba(32,178,107,0.18)]">
			<img src={logoMarkLight} alt="" aria-hidden="true" className="h-8 w-auto" />
			<p className="text-sm font-semibold text-green-400">The revenue layer</p>
		</div>
	);
}

function InwardArrow({ direction }: { direction: "down" | "up" | "left" | "right" }) {
	const rotation = { down: "rotate-0", up: "rotate-180", left: "rotate-90", right: "-rotate-90" }[direction];
	return (
		<svg className={`size-6 text-green-400/70 ${rotation}`} viewBox="0 0 24 24" fill="none" aria-hidden="true">
			<path d="M12 4v13m0 0-5-5m5 5 5-5" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
		</svg>
	);
}

/**
 * "Your existing tools feed into Rally26" diagram (How It Works section).
 * Deliberately uses generic categories, not real vendor logos/names — Rally26
 * has no confirmed integration with any specific product, and implying one with
 * a real trademark would be misleading. Swap in real logos only once actual
 * integrations exist and the org has the rights to display them.
 */
export function IntegrationsDiagram() {
	const [registration, scheduling, rosters, communication] = TOOL_CATEGORIES;

	return (
		<div>
			<div
				className="hidden lg:grid lg:items-center lg:justify-items-center lg:gap-3"
				style={{
					gridTemplateColumns: "1fr auto 1fr auto 1fr",
					gridTemplateAreas: `". . top . ."\n". . arrowDown . ."\n"left arrowRight center arrowLeft right"\n". . arrowUp . ."\n". . bottom . ."`,
				}}
			>
				<div style={{ gridArea: "top" }}>
					<ToolNode category={registration} />
				</div>
				<div style={{ gridArea: "arrowDown" }}>
					<InwardArrow direction="down" />
				</div>
				<div style={{ gridArea: "left" }}>
					<ToolNode category={communication} />
				</div>
				<div style={{ gridArea: "arrowRight" }}>
					<InwardArrow direction="right" />
				</div>
				<div style={{ gridArea: "center" }}>
					<CenterNode />
				</div>
				<div style={{ gridArea: "arrowLeft" }}>
					<InwardArrow direction="left" />
				</div>
				<div style={{ gridArea: "right" }}>
					<ToolNode category={scheduling} />
				</div>
				<div style={{ gridArea: "arrowUp" }}>
					<InwardArrow direction="up" />
				</div>
				<div style={{ gridArea: "bottom" }}>
					<ToolNode category={rosters} />
				</div>
			</div>

			<div className="flex flex-col items-center gap-3 lg:hidden">
				{TOOL_CATEGORIES.map((category) => (
					<div key={category.label} className="flex flex-col items-center gap-3">
						<ToolNode category={category} />
						<InwardArrow direction="down" />
					</div>
				))}
				<CenterNode />
			</div>
		</div>
	);
}
