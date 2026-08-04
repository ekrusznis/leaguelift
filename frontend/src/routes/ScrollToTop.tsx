import { useEffect } from "react";
import { useLocation } from "react-router-dom";

/**
 * React Router doesn't reset scroll position on navigation (unlike a full page
 * load), so pushing a new route while scrolled down a previous page — e.g.
 * clicking into a Solutions detail page from the bottom of the Solutions list —
 * leaves the viewport scrolled down on the new page too. Renders nothing; just
 * scrolls to top whenever the pathname changes.
 */
export function ScrollToTop() {
	const { pathname } = useLocation();

	useEffect(() => {
		window.scrollTo(0, 0);
	}, [pathname]);

	return null;
}
