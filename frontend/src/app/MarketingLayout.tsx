import { Outlet } from "react-router-dom";
import { SiteFooter } from "../marketing/components/SiteFooter";
import { SiteHeader } from "../marketing/components/SiteHeader";

/** Shared shell for every public marketing page (RALLY26_SALES_SITE_DESIGN.md section 32). */
export function MarketingLayout() {
	return (
		<div className="flex min-h-screen flex-col bg-ice-50 dark:bg-[#0f172a]">
			<a
				href="#main-content"
				className="sr-only focus:not-sr-only focus:absolute focus:left-2 focus:top-2 focus:z-50 focus:rounded-md focus:bg-white focus:dark:bg-[#111827] focus:p-2 focus:text-navy-900 focus:dark:text-[#f8fafc]"
			>
				Skip to main content
			</a>
			<SiteHeader />
			<main id="main-content" className="flex-1">
				<Outlet />
			</main>
			<SiteFooter />
		</div>
	);
}
