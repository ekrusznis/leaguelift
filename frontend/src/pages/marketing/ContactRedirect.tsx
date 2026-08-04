import { useEffect } from "react";
import { useNavigate } from "react-router-dom";
import { HOMEPAGE_SECTION_IDS } from "../../marketing/content/nav";

/**
 * `/contact` used to be a standalone (non-functional mock) page. It's now the
 * homepage's real, backend-wired Contact Us section instead (ADR-059) — this route
 * still exists purely so the many existing `<Link to="/contact">` references
 * scattered across the app (auth pages, legal pages, error states, etc.) keep
 * working without having to touch every one of those call sites. Reuses the same
 * `state.scrollTo` handoff `usePendingHomeScroll` (see marketing/useScrollToHash.ts)
 * already reads on `HomePage` mount, so this is just another producer of that
 * contract, not a new scrolling mechanism.
 */
export function ContactRedirect() {
	const navigate = useNavigate();

	useEffect(() => {
		navigate("/", { replace: true, state: { scrollTo: HOMEPAGE_SECTION_IDS.contactUs } });
	}, [navigate]);

	return null;
}
