import { useCallback } from "react";
import { useLocation, useNavigate } from "react-router-dom";

/**
 * Drives the "auto-scroll" header nav items (Product > Overview / Features, see
 * content/nav.ts) — smoothly scrolls to a homepage section id, navigating home
 * first if we're on another page.
 */
export function useScrollToHash() {
	const navigate = useNavigate();
	const location = useLocation();

	return useCallback(
		(hash: string) => {
			const behavior: ScrollBehavior = window.matchMedia("(prefers-reduced-motion: reduce)").matches
				? "auto"
				: "smooth";

			if (location.pathname === "/") {
				document.getElementById(hash)?.scrollIntoView({ behavior, block: "start" });
				return;
			}

			navigate("/", { state: { scrollTo: hash } });
		},
		[location.pathname, navigate],
	);
}

/** Consumed by HomePage on mount to finish a cross-page "auto-scroll" nav click. */
export function usePendingHomeScroll() {
	const location = useLocation();

	return useCallback(() => {
		const state = location.state as { scrollTo?: string } | null;
		if (!state?.scrollTo) return;

		const behavior: ScrollBehavior = window.matchMedia("(prefers-reduced-motion: reduce)").matches
			? "auto"
			: "smooth";
		requestAnimationFrame(() => {
			document.getElementById(state.scrollTo!)?.scrollIntoView({ behavior, block: "start" });
		});
	}, [location.state]);
}
