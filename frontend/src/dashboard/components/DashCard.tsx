import type { ReactNode } from "react";
import { Link } from "react-router-dom";

type DashCardAction =
	| { label: string; to: string; onClick?: never }
	| { label: string; onClick: () => void; to?: never };

type DashCardProps = {
	id?: string;
	title?: string;
	action?: DashCardAction;
	children: ReactNode;
	className?: string;
	tone?: "light" | "dark";
};

/** White (or dark-elevated) card — the base unit for every dashboard widget. */
export function DashCard({ id, title, action, children, className = "", tone = "light" }: DashCardProps) {
	const surface =
		tone === "dark"
			? "border-white/10 bg-navy-900 text-white"
			: "border-slate-200 bg-white text-navy-900";

	return (
		<section id={id} className={`scroll-mt-24 rounded-2xl border p-5 shadow-[0_8px_24px_rgba(11,31,51,0.06)] ${surface} ${className}`}>
			{(title || action) && (
				<div className="mb-4 flex items-center justify-between gap-3">
					{title && <h2 className="font-heading text-base font-bold">{title}</h2>}
					{action &&
						(action.to ? (
							<Link to={action.to} className="flex items-center gap-1 text-sm font-semibold text-green-600 hover:text-green-500 hover:underline">
								{action.label}
								<ChevronMini />
							</Link>
						) : (
							<button
								type="button"
								onClick={action.onClick}
								className="flex items-center gap-1 text-sm font-semibold text-green-600 hover:text-green-500 hover:underline"
							>
								{action.label}
								<ChevronMini />
							</button>
						))}
				</div>
			)}
			{children}
		</section>
	);
}

function ChevronMini() {
	return (
		<svg className="size-3.5" viewBox="0 0 16 16" fill="none" aria-hidden="true">
			<path d="M6 3.5 10.5 8 6 12.5" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" />
		</svg>
	);
}
