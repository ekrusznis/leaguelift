import type { SVGProps } from "react";

/**
 * Shared line-icon set for the dashboard shell and widgets
 * (docs/RALLY26_DASHBOARD_DESIGN.md section 4.1: "consistent icons and
 * typography"). Same stroke style as the marketing site's icons — no icon
 * library dependency added for a handful of glyphs.
 */
type IconProps = SVGProps<SVGSVGElement>;

function base(props: IconProps) {
	return { viewBox: "0 0 24 24", fill: "none", "aria-hidden": true, ...props } as const;
}

export function HomeIcon(props: IconProps) {
	return (
		<svg {...base(props)}>
			<path d="M4 11.5 12 4l8 7.5" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" />
			<path d="M6 10v9h12v-9" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" />
			<path d="M10 19v-5h4v5" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" />
		</svg>
	);
}

export function UserIcon(props: IconProps) {
	return (
		<svg {...base(props)}>
			<circle cx="12" cy="8" r="3.5" stroke="currentColor" strokeWidth="1.7" />
			<path d="M4.5 20c1.2-4 4-6 7.5-6s6.3 2 7.5 6" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" />
		</svg>
	);
}

export function UsersIcon(props: IconProps) {
	return (
		<svg {...base(props)}>
			<circle cx="9" cy="8" r="3" stroke="currentColor" strokeWidth="1.7" />
			<path d="M3 19c.9-3.2 3-5 6-5s5.1 1.8 6 5" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" />
			<path d="M15.5 5.5c1.4.3 2.5 1.5 2.5 3s-1.1 2.7-2.5 3" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" />
			<path d="M17 14c1.9.5 3.2 1.9 3.8 4.2" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" />
		</svg>
	);
}

export function CalendarIcon(props: IconProps) {
	return (
		<svg {...base(props)}>
			<rect x="4" y="5.5" width="16" height="14.5" rx="2" stroke="currentColor" strokeWidth="1.7" />
			<path d="M4 9.5h16M8 3.5v3M16 3.5v3" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" />
		</svg>
	);
}

export function HistoryIcon(props: IconProps) {
	return (
		<svg {...base(props)}>
			<path d="M4 12a8 8 0 1 1 2.6 5.9" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" />
			<path d="M4 8v4h4" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" />
			<path d="M12 8v4.5l3 2" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" />
		</svg>
	);
}

export function ShieldIcon(props: IconProps) {
	return (
		<svg {...base(props)}>
			<path d="M12 3.5 19 6.3v5.4c0 4.4-2.9 7.4-7 8.8-4.1-1.4-7-4.4-7-8.8V6.3Z" stroke="currentColor" strokeWidth="1.7" strokeLinejoin="round" />
		</svg>
	);
}

export function PackageIcon(props: IconProps) {
	return (
		<svg {...base(props)}>
			<path d="M3.5 8 12 4l8.5 4-8.5 4-8.5-4Z" stroke="currentColor" strokeWidth="1.7" strokeLinejoin="round" />
			<path d="M3.5 8v8L12 20l8.5-4V8M12 12v8" stroke="currentColor" strokeWidth="1.7" strokeLinejoin="round" />
		</svg>
	);
}

export function SearchIcon(props: IconProps) {
	return (
		<svg {...base(props)}>
			<circle cx="10.5" cy="10.5" r="6.5" stroke="currentColor" strokeWidth="1.7" />
			<path d="m19.5 19.5-4-4" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" />
		</svg>
	);
}

export function BellIcon(props: IconProps) {
	return (
		<svg {...base(props)}>
			<path d="M6 10a6 6 0 1 1 12 0c0 4 1.2 5.5 1.2 5.5H4.8S6 14 6 10Z" stroke="currentColor" strokeWidth="1.7" strokeLinejoin="round" />
			<path d="M9.5 18.5a2.5 2.5 0 0 0 5 0" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" />
		</svg>
	);
}

export function HelpIcon(props: IconProps) {
	return (
		<svg {...base(props)}>
			<circle cx="12" cy="12" r="8.5" stroke="currentColor" strokeWidth="1.7" />
			<path d="M9.5 9.3a2.5 2.5 0 1 1 3.6 2.3c-.8.4-1.1 1-1.1 1.9" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" />
			<circle cx="12" cy="17" r="0.9" fill="currentColor" stroke="none" />
		</svg>
	);
}

export function ChevronDownIcon(props: IconProps) {
	return (
		<svg {...base(props)}>
			<path d="m6 9 6 6 6-6" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
		</svg>
	);
}

export function ChevronRightIcon(props: IconProps) {
	return (
		<svg {...base(props)}>
			<path d="m9 6 6 6-6 6" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
		</svg>
	);
}

export function DollarIcon(props: IconProps) {
	return (
		<svg {...base(props)}>
			<path d="M12 3v18" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" />
			<path d="M16 7.5c0-1.7-1.8-2.5-4-2.5s-4 1-4 2.7c0 4.3 8 2 8 6.3 0 1.8-1.8 2.8-4 2.8s-4-1-4-2.8" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" />
		</svg>
	);
}

export function HeartHandshakeIcon(props: IconProps) {
	return (
		<svg {...base(props)}>
			<path
				d="M12 20s-7-4.4-7-9.5A4.5 4.5 0 0 1 12 8a4.5 4.5 0 0 1 7 2.5C19 15.6 12 20 12 20Z"
				stroke="currentColor"
				strokeWidth="1.7"
				strokeLinejoin="round"
			/>
		</svg>
	);
}

export function GiftIcon(props: IconProps) {
	return (
		<svg {...base(props)}>
			<rect x="4" y="9.5" width="16" height="10" rx="1.5" stroke="currentColor" strokeWidth="1.7" />
			<path d="M4 13h16M12 9.5V20" stroke="currentColor" strokeWidth="1.7" />
			<path d="M12 9.5c-2.8 0-4-1.2-4-2.7A2.1 2.1 0 0 1 12 5.5a2.1 2.1 0 0 1 4 1.3c0 1.5-1.2 2.7-4 2.7Z" stroke="currentColor" strokeWidth="1.7" strokeLinejoin="round" />
		</svg>
	);
}

export function BuildingIcon(props: IconProps) {
	return (
		<svg {...base(props)}>
			<rect x="5" y="4" width="10" height="16" rx="1" stroke="currentColor" strokeWidth="1.7" />
			<path d="M15 9h4v11h-4" stroke="currentColor" strokeWidth="1.7" strokeLinejoin="round" />
			<path d="M8 8h1M11 8h1M8 11.5h1M11 11.5h1M8 15h1M11 15h1" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" />
		</svg>
	);
}

export function TrophyIcon(props: IconProps) {
	return (
		<svg {...base(props)}>
			<path d="M7 4h10v5a5 5 0 0 1-10 0Z" stroke="currentColor" strokeWidth="1.7" strokeLinejoin="round" />
			<path d="M7 5.5H4v2a3 3 0 0 0 3 3M17 5.5h3v2a3 3 0 0 1-3 3" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" />
			<path d="M12 14v3M9 20h6M9.5 20c0-1.7.7-2.7 2.5-3 1.8.3 2.5 1.3 2.5 3" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" />
		</svg>
	);
}

export function ShirtIcon(props: IconProps) {
	return (
		<svg {...base(props)}>
			<path
				d="M8 4 4 7l2 3 2-1v11h8V9l2 1 2-3-4-3c-.5 1-1.5 1.5-2.9 1.5S9.4 5 8 4Z"
				stroke="currentColor"
				strokeWidth="1.7"
				strokeLinejoin="round"
			/>
		</svg>
	);
}

export function MegaphoneIcon(props: IconProps) {
	return (
		<svg {...base(props)}>
			<path d="M4 10v3l3 .8V16a1.5 1.5 0 0 0 3 0v-1.6l9 2.4V8.2L10 10.6V9.2L4 10Z" stroke="currentColor" strokeWidth="1.6" strokeLinejoin="round" />
		</svg>
	);
}

export function FileTextIcon(props: IconProps) {
	return (
		<svg {...base(props)}>
			<path d="M7 3.5h7l4 4V20a1 1 0 0 1-1 1H7a1 1 0 0 1-1-1V4.5a1 1 0 0 1 1-1Z" stroke="currentColor" strokeWidth="1.7" strokeLinejoin="round" />
			<path d="M9 12h6M9 15.5h6" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" />
		</svg>
	);
}

export function SettingsIcon(props: IconProps) {
	return (
		<svg {...base(props)}>
			<circle cx="12" cy="12" r="3" stroke="currentColor" strokeWidth="1.7" />
			<path
				d="M12 3.5v2M12 18.5v2M20.5 12h-2M5.5 12h-2M17.7 6.3l-1.4 1.4M7.7 16.3l-1.4 1.4M17.7 17.7l-1.4-1.4M7.7 7.7 6.3 6.3"
				stroke="currentColor"
				strokeWidth="1.7"
				strokeLinecap="round"
			/>
		</svg>
	);
}

export function UserPlusIcon(props: IconProps) {
	return (
		<svg {...base(props)}>
			<circle cx="9" cy="8" r="3.5" stroke="currentColor" strokeWidth="1.7" />
			<path d="M3 20c1-3.6 3.2-5.5 6-5.5s5 1.9 6 5.5" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" />
			<path d="M18.5 8v5M16 10.5h5" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" />
		</svg>
	);
}

export function PlusIcon(props: IconProps) {
	return (
		<svg {...base(props)}>
			<path d="M12 5v14M5 12h14" stroke="currentColor" strokeWidth="1.9" strokeLinecap="round" />
		</svg>
	);
}

export function MailIcon(props: IconProps) {
	return (
		<svg {...base(props)}>
			<rect x="3.5" y="5.5" width="17" height="13" rx="1.5" stroke="currentColor" strokeWidth="1.7" />
			<path d="m4.5 6.5 7.5 6 7.5-6" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" />
		</svg>
	);
}

export function ChartIcon(props: IconProps) {
	return (
		<svg {...base(props)}>
			<path d="M4 20V10M10 20V4M16 20v-7M4 20h16" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" />
		</svg>
	);
}

export function ClipboardCheckIcon(props: IconProps) {
	return (
		<svg {...base(props)}>
			<rect x="6" y="4.5" width="12" height="16" rx="1.5" stroke="currentColor" strokeWidth="1.7" />
			<path d="M9 4.5V3.8A1.3 1.3 0 0 1 10.3 2.5h3.4A1.3 1.3 0 0 1 15 3.8v.7" stroke="currentColor" strokeWidth="1.7" />
			<path d="m9 13 2 2 4-4.5" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" />
		</svg>
	);
}

export function AlertIcon(props: IconProps) {
	return (
		<svg {...base(props)}>
			<path d="M12 4 21 19H3Z" stroke="currentColor" strokeWidth="1.7" strokeLinejoin="round" />
			<path d="M12 10v4M12 16.5h.01" stroke="currentColor" strokeWidth="1.9" strokeLinecap="round" />
		</svg>
	);
}

export function CheckCircleIcon(props: IconProps) {
	return (
		<svg {...base(props)}>
			<circle cx="12" cy="12" r="8.5" stroke="currentColor" strokeWidth="1.7" />
			<path d="m8.3 12.2 2.4 2.4 5-5.2" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
		</svg>
	);
}

export function MapPinIcon(props: IconProps) {
	return (
		<svg {...base(props)}>
			<path d="M12 21s7-6.3 7-11.5A7 7 0 0 0 5 9.5C5 14.7 12 21 12 21Z" stroke="currentColor" strokeWidth="1.7" strokeLinejoin="round" />
			<circle cx="12" cy="9.5" r="2.3" stroke="currentColor" strokeWidth="1.7" />
		</svg>
	);
}

export function ArrowRightIcon(props: IconProps) {
	return (
		<svg {...base(props)}>
			<path d="M4.5 12h15m0 0-5-5m5 5-5 5" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" />
		</svg>
	);
}

export function DownloadIcon(props: IconProps) {
	return (
		<svg {...base(props)}>
			<path d="M12 3.5v11.5M8 11l4 4 4-4" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" />
			<path d="M4.5 17v2.5A1.5 1.5 0 0 0 6 21h12a1.5 1.5 0 0 0 1.5-1.5V17" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" />
		</svg>
	);
}

export function MessageSquareIcon(props: IconProps) {
	return (
		<svg {...base(props)}>
			<path d="M4.5 5.5h15v10h-8L7 19v-3.5H4.5Z" stroke="currentColor" strokeWidth="1.7" strokeLinejoin="round" />
		</svg>
	);
}

export function LayoutIcon(props: IconProps) {
	return (
		<svg {...base(props)}>
			<rect x="4" y="4.5" width="16" height="15" rx="1.5" stroke="currentColor" strokeWidth="1.7" />
			<path d="M4 9.5h16M9.5 9.5V19.5" stroke="currentColor" strokeWidth="1.7" />
		</svg>
	);
}
