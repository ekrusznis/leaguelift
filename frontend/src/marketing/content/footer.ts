export const FOOTER_COLUMNS: { heading: string; links: { label: string; to: string }[] }[] = [
	{
		heading: "Product",
		links: [
			{ label: "Features", to: "/#features" },
			{ label: "How It Works", to: "/how-it-works" },
			{ label: "Pricing", to: "/pricing" },
			{ label: "Talk to Sales", to: "/talk-to-sales" },
		],
	},
	{
		heading: "Solutions",
		links: [
			{ label: "Leagues", to: "/solutions" },
			{ label: "Clubs", to: "/solutions" },
			{ label: "Teams", to: "/solutions/team-pages" },
			{ label: "Tournaments", to: "/solutions/tournament-pages" },
		],
	},
	{
		heading: "Resources",
		links: [
			{ label: "Help", to: "/help" },
			{ label: "Security", to: "/security" },
			{ label: "Contact", to: "/contact" },
		],
	},
	{
		heading: "Company",
		links: [
			{ label: "About", to: "/about" },
			{ label: "Privacy", to: "/privacy" },
			{ label: "Terms", to: "/terms" },
			{ label: "Accessibility", to: "/accessibility" },
		],
	},
];

export const FOOTER_LEGAL_LINKS = [
	{ label: "Privacy", to: "/privacy" },
	{ label: "Terms", to: "/terms" },
	{ label: "Accessibility", to: "/accessibility" },
	{ label: "Security", to: "/security" },
];
