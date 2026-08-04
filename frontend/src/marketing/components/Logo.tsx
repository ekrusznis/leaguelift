import { Link } from "react-router-dom";
import logoFullDark from "../../assets/rally26-logo-dark.svg";
import logoFullLight from "../../assets/rally26-logo-light.svg";
import logoMarkDark from "../../assets/rally26-mark-dark.svg";
import logoMarkLight from "../../assets/rally26-mark-light.svg";

type LogoProps = { tone?: "dark" | "light"; to?: string; compact?: boolean };

/**
 * Renders the real Rally26 logo assets (frontend/src/assets/rally26-*.svg).
 * `tone` describes the background the logo sits on, not the logo's own color —
 * `tone="dark"` (a dark navy header/panel) needs the white-on-transparent
 * "-light" file; `tone="light"` needs the navy-on-transparent "-dark" file.
 * Logo links to "/" (section 8.1) unless `to` overrides it (e.g. auth pages link home too).
 */
export function Logo({ tone = "dark", to = "/", compact = false }: LogoProps) {
	const markSrc = tone === "dark" ? logoMarkLight : logoMarkDark;
	const fullSrc = tone === "dark" ? logoFullLight : logoFullDark;

	return (
		<Link to={to} className="inline-flex items-center focus-visible:outline-3 focus-visible:outline-orange-400" aria-label="Rally26 home">
			{compact ? (
				<img src={markSrc} alt="Rally26" className="h-8 w-auto" />
			) : (
				<img src={fullSrc} alt="Rally26" className="h-7 w-auto sm:h-8" />
			)}
		</Link>
	);
}
