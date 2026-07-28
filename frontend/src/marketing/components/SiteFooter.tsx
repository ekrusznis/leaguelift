import { Link } from "react-router-dom";
import { FOOTER_COLUMNS, FOOTER_LEGAL_LINKS } from "../content/footer";
import { Logo } from "./Logo";
import { PageContainer } from "./PageContainer";

/** Global footer (section 9) — social icons stay hidden until real profiles exist. */
export function SiteFooter() {
	const year = new Date().getFullYear();

	return (
		<footer className="bg-navy-950 text-slate-300">
			<PageContainer className="grid gap-10 py-16 sm:grid-cols-2 lg:grid-cols-5">
				<div className="sm:col-span-2 lg:col-span-1">
					<Logo tone="dark" />
					<p className="mt-4 text-sm font-medium text-slate-200">More revenue. Lower fees. Stronger programs.</p>
					<p className="mt-2 max-w-xs text-sm text-slate-500">
						A revenue and payment-management platform for youth sports organizations.
					</p>
				</div>

				{FOOTER_COLUMNS.map((column) => (
					<div key={column.heading}>
						<h3 className="font-heading text-sm font-semibold text-white">{column.heading}</h3>
						<ul className="mt-4 flex flex-col gap-3">
							{column.links.map((link) => (
								<li key={link.label}>
									<Link to={link.to} className="text-sm text-slate-400 hover:text-white">
										{link.label}
									</Link>
								</li>
							))}
						</ul>
					</div>
				))}
			</PageContainer>

			<div className="border-t border-white/10">
				<PageContainer className="flex flex-col items-center gap-4 py-6 text-sm text-slate-500 sm:flex-row sm:justify-between">
					<p>
						© {year} LeagueLift
					</p>
					<nav aria-label="Legal" className="flex flex-wrap items-center justify-center gap-x-6 gap-y-2">
						{FOOTER_LEGAL_LINKS.map((link) => (
							<Link key={link.to} to={link.to} className="hover:text-white">
								{link.label}
							</Link>
						))}
					</nav>
				</PageContainer>
			</div>
		</footer>
	);
}
