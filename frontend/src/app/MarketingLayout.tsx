import { Outlet } from "react-router-dom";
import { SiteFooter } from "../marketing/components/SiteFooter";
import { SiteHeader } from "../marketing/components/SiteHeader";

/** Shared shell for every public marketing page (LEAGUELIFT_SALES_SITE_DESIGN.md section 32). */
export function MarketingLayout() {
	return (
		<div className="flex min-h-screen flex-col bg-ice-50">
			<a
				href="#main-content"
				className="sr-only focus:not-sr-only focus:absolute focus:left-2 focus:top-2 focus:z-50 focus:rounded-md focus:bg-white focus:p-2 focus:text-navy-900"
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
