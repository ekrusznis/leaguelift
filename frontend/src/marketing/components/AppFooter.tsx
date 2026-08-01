import { Link } from "react-router-dom";
import { FOOTER_LEGAL_LINKS } from "../content/footer";
import { SOCIAL_LINKS } from "../content/social";
import { Logo } from "./Logo";

export function AppFooter({ className = "", authenticated = true }: { className?: string; authenticated?: boolean }) {
	const year = new Date().getFullYear();
	const helpBase = authenticated ? "/app/help" : "/help";

	return (
		<footer className={`border-t border-white/10 bg-navy-950 text-slate-400 ${className}`}>
			<div className="mx-auto flex max-w-7xl flex-col gap-5 px-4 py-7 sm:px-6 lg:px-8">
				<div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
					<div className="flex items-center gap-4">
						<Logo tone="dark" to={authenticated ? "/app" : "/"} />
						<p className="hidden text-sm text-slate-400 md:block">More revenue. Lower fees. Stronger programs.</p>
					</div>
					<nav aria-label="Support and legal" className="flex flex-wrap gap-x-5 gap-y-2 text-sm">
						<Link to={helpBase} className="hover:text-white">Help Center</Link>
						<Link to={`${helpBase}/support`} className="hover:text-white">Support</Link>
						{FOOTER_LEGAL_LINKS.map((link) => <Link key={link.to} to={link.to} className="hover:text-white">{link.label}</Link>)}
					</nav>
				</div>
				<div className="flex flex-col gap-3 border-t border-white/10 pt-4 text-xs sm:flex-row sm:items-center sm:justify-between">
					<p>© {year} LeagueLift. All rights reserved.</p>
					{SOCIAL_LINKS.length > 0 && (
						<nav aria-label="LeagueLift social profiles" className="flex flex-wrap gap-4">
							{SOCIAL_LINKS.map((link) => (
								<a key={link.label} href={link.href} target="_blank" rel="noreferrer" className="hover:text-white">{link.label}</a>
							))}
						</nav>
					)}
				</div>
			</div>
		</footer>
	);
}
