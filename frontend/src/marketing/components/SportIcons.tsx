type IconProps = { className?: string };

/**
 * Sport-neutral line icons (LEAGUELIFT_SALES_SITE_DESIGN.md section 4.3 forbids
 * single-sport logo marks, and no licensed athlete photography is available yet —
 * these stand in for the reference image's photo collage without fabricating
 * imagery we don't have rights to).
 */
export function HockeyIcon({ className = "" }: IconProps) {
	return (
		<svg viewBox="0 0 24 24" fill="none" className={className} aria-hidden="true">
			<path d="M5 19 17 5" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" />
			<path d="M17 5h2.4c.3 0 .5.24.45.53l-.5 3a.5.5 0 0 1-.5.42H15" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" />
			<circle cx="6" cy="19" r="2" stroke="currentColor" strokeWidth="1.6" />
		</svg>
	);
}

export function SoccerIcon({ className = "" }: IconProps) {
	return (
		<svg viewBox="0 0 24 24" fill="none" className={className} aria-hidden="true">
			<circle cx="12" cy="12" r="8.5" stroke="currentColor" strokeWidth="1.6" />
			<path
				d="m12 7 3.5 2.5-1.3 4.1H9.8L8.5 9.5 12 7ZM12 7V4.5M8.5 9.5 5 8.3M15.5 9.5 19 8.3M9.8 13.6 8 17M14.2 13.6 16 17"
				stroke="currentColor"
				strokeWidth="1.3"
				strokeLinecap="round"
				strokeLinejoin="round"
			/>
		</svg>
	);
}

export function BaseballIcon({ className = "" }: IconProps) {
	return (
		<svg viewBox="0 0 24 24" fill="none" className={className} aria-hidden="true">
			<circle cx="12" cy="12" r="8.5" stroke="currentColor" strokeWidth="1.6" />
			<path d="M6 6.5c2.2 1.8 2.2 9.2 0 11M18 6.5c-2.2 1.8-2.2 9.2 0 11" stroke="currentColor" strokeWidth="1.2" strokeLinecap="round" />
		</svg>
	);
}

export function BasketballIcon({ className = "" }: IconProps) {
	return (
		<svg viewBox="0 0 24 24" fill="none" className={className} aria-hidden="true">
			<circle cx="12" cy="12" r="8.5" stroke="currentColor" strokeWidth="1.6" />
			<path
				d="M12 3.5v17M3.5 12h17M5.6 6.1C8.8 9 8.8 15 5.6 17.9M18.4 6.1C15.2 9 15.2 15 18.4 17.9"
				stroke="currentColor"
				strokeWidth="1.2"
				strokeLinecap="round"
			/>
		</svg>
	);
}

export function VolleyballIcon({ className = "" }: IconProps) {
	return (
		<svg viewBox="0 0 24 24" fill="none" className={className} aria-hidden="true">
			<circle cx="12" cy="12" r="8.5" stroke="currentColor" strokeWidth="1.6" />
			<path
				d="M12 3.5c3 2 4.5 5.5 3.6 9M12 3.5c-3 2-4.5 5.5-3.6 9M4.2 14.5c3.4-.6 6.7.7 8.4 3.4M19.8 14.5c-3.4-.6-6.7.7-8.4 3.4"
				stroke="currentColor"
				strokeWidth="1.1"
				strokeLinecap="round"
			/>
		</svg>
	);
}

export const SPORT_ICONS = [
	{ label: "Hockey", Icon: HockeyIcon },
	{ label: "Soccer", Icon: SoccerIcon },
	{ label: "Baseball", Icon: BaseballIcon },
	{ label: "Basketball", Icon: BasketballIcon },
	{ label: "& More", Icon: VolleyballIcon },
];

export function SportIconRow({ tone = "dark" }: { tone?: "dark" | "light" }) {
	const textColor = tone === "dark" ? "text-slate-300" : "text-slate-700";
	const iconColor = tone === "dark" ? "text-green-400" : "text-green-600";

	return (
		<ul className="flex flex-wrap items-center justify-center gap-6 sm:justify-start" aria-label="Sports LeagueLift supports">
			{SPORT_ICONS.map(({ label, Icon }) => (
				<li key={label} className={`flex flex-col items-center gap-2 text-xs font-semibold ${textColor}`}>
					<Icon className={`size-7 ${iconColor}`} />
					{label}
				</li>
			))}
		</ul>
	);
}
