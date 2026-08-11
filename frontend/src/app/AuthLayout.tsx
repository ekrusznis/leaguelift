import { Outlet } from "react-router-dom";
import { Logo } from "../marketing/components/Logo";
import { SportIconRow } from "../marketing/components/SportIcons";
import { heroImages } from "../marketing/heroImages";
import { AppFooter } from "../marketing/components/AppFooter";

const VALUE_STATEMENTS = [
	"Built for multiple sports",
	"Adult-managed access",
	"Role-based organization controls",
	"Clear fees and credits",
];

/**
 * Two-column split layout for /auth/* (section 23). Left brand panel is decorative
 * and hidden on small screens (section 23.2: compact logo/tagline instead); right
 * panel renders the active auth page's card via <Outlet/>.
 */
export function AuthLayout() {
	return (
		<div className="flex min-h-screen flex-col bg-navy-950 lg:flex-row">
			<a
				href="#auth-main"
				className="sr-only focus:not-sr-only focus:absolute focus:left-2 focus:top-2 focus:z-50 focus:rounded-md focus:bg-white focus:dark:bg-[#111827] focus:p-2 focus:text-navy-900 focus:dark:text-[#f8fafc]"
			>
				Skip to main content
			</a>

			<div className="relative flex shrink-0 flex-col justify-between overflow-hidden px-6 py-8 lg:w-[58%] lg:px-16 lg:py-14">
				<img
					src={heroImages.multisportWalkout}
					alt=""
					aria-hidden="true"
					className="absolute inset-0 size-full object-cover"
				/>
				<div
					className="pointer-events-none absolute inset-0"
					style={{
						background:
							"linear-gradient(180deg, rgba(6,19,33,0.72) 0%, rgba(6,19,33,0.55) 45%, rgba(6,19,33,0.95) 100%)",
					}}
				/>

				<div className="relative">
					<Logo tone="dark" />
					<p className="mt-3 text-sm font-medium text-slate-300 lg:mt-4 lg:text-base">
						More revenue. Lower fees. Stronger programs.
					</p>
				</div>

				<div className="relative mt-10 hidden flex-col gap-8 lg:flex">
					<SportIconRow tone="dark" />
					<ul className="grid grid-cols-2 gap-3">
						{VALUE_STATEMENTS.map((statement) => (
							<li key={statement} className="flex items-center gap-2 text-sm text-slate-200">
								<svg className="size-4 shrink-0 text-green-400" viewBox="0 0 16 16" fill="none" aria-hidden="true">
									<path d="m3 8.5 3 3 7-7" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
								</svg>
								{statement}
							</li>
						))}
					</ul>
				</div>
			</div>

			<div className="flex min-w-0 flex-1 flex-col bg-ice-50 dark:bg-[#0f172a]">
				<div id="auth-main" className="flex flex-1 items-center justify-center px-5 py-10 sm:px-10 lg:px-14">
					<div className="w-full max-w-md"><Outlet /></div>
				</div>
				<AppFooter authenticated={false} />
			</div>
		</div>
	);
}
