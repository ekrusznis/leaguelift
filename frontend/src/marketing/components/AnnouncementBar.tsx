import { useState } from "react";
import { Link } from "react-router-dom";
import { featureFlags } from "../featureFlags";

const DISMISS_KEY = "r26_announcement_dismissed";

/** Dismissible per browser session (section 12.1) — no fake scarcity language. */
export function AnnouncementBar() {
	const [dismissed, setDismissed] = useState(() => sessionStorage.getItem(DISMISS_KEY) === "1");

	if (!featureFlags.pilotAnnouncementBar || dismissed) return null;

	return (
		<div className="relative bg-gradient-to-r from-orange-600 to-orange-500 text-white">
			<div className="mx-auto flex w-[min(100%-40px,1360px)] items-center justify-center gap-3 py-2 text-center text-sm font-medium">
				<span>Rally26 is open for new organizations.</span>
				<Link to="/auth/register" className="font-semibold underline underline-offset-2 hover:no-underline">
					Get Started
				</Link>
			</div>
			<button
				type="button"
				aria-label="Dismiss announcement"
				onClick={() => {
					sessionStorage.setItem(DISMISS_KEY, "1");
					setDismissed(true);
				}}
				className="absolute right-3 top-1/2 -translate-y-1/2 rounded-full p-1 hover:bg-white/15 focus-visible:outline focus-visible:outline-2 focus-visible:outline-white"
			>
				<svg className="size-4" viewBox="0 0 16 16" fill="none" aria-hidden="true">
					<path d="m4 4 8 8m0-8-8 8" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" />
				</svg>
			</button>
		</div>
	);
}
