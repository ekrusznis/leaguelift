import { Link } from "react-router-dom";
import logoFullDark from "../../assets/leaguelift-logo-dark.svg";
import logoFullLight from "../../assets/leaguelift-logo-light.svg";
import logoMarkDark from "../../assets/leaguelift-mark-dark.svg";
import logoMarkLight from "../../assets/leaguelift-mark-light.svg";

type LogoProps = { tone?: "dark" | "light"; to?: string; compact?: boolean };

/**
 * Renders the real LeagueLift logo assets (frontend/src/assets/leaguelift-*.svg).
 * `tone` describes the background the logo sits on, not the logo's own color —
 * `tone="dark"` (a dark navy header/panel) needs the white-on-transparent
 * "-light" file; `tone="light"` needs the navy-on-transparent "-dark" file.
 * Logo links to "/" (section 8.1) unless `to` overrides it (e.g. auth pages link home too).
 */
export function Logo({ tone = "dark", to = "/", compact = false }: LogoProps) {
	const markSrc = tone === "dark" ? logoMarkLight : logoMarkDark;
	const fullSrc = tone === "dark" ? logoFullLight : logoFullDark;

	return (
		<Link to={to} className="inline-flex items-center focus-visible:outline-3 focus-visible:outline-green-400" aria-label="LeagueLift home">
			{compact ? (
				<img src={markSrc} alt="LeagueLift" className="h-8 w-auto" />
			) : (
				<img src={fullSrc} alt="LeagueLift" className="h-7 w-auto sm:h-8" />
			)}
		</Link>
	);
}
