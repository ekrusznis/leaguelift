const PALETTE = ["bg-green-500", "bg-info-600", "bg-gold-500", "bg-navy-700"];

function paletteFor(name: string) {
	const index = name.charCodeAt(0) % PALETTE.length;
	return PALETTE[index];
}

function initials(name: string) {
	const parts = name.trim().split(/\s+/);
	return ((parts[0]?.[0] ?? "") + (parts[1]?.[0] ?? "")).toUpperCase();
}

type OrganizationLogoProps = {
	name: string;
	src?: string;
	size?: "sm" | "md" | "lg";
};

const SIZES = { sm: "size-8 p-1", md: "size-11 p-1.5", lg: "size-14 p-2" };

/**
 * Dark-chip crest for an organization's own logo, mirroring TeamLogo's presentation.
 * Falls back to an initials badge rather than TeamLogo's "org logo -> initials" tier,
 * since there's no parent logo to inherit for the organization's own logo.
 */
export function OrganizationLogo({ name, src, size = "md" }: OrganizationLogoProps) {
	if (src) {
		return (
			<span className={`flex shrink-0 items-center justify-center overflow-hidden rounded-lg bg-navy-900 ${SIZES[size]}`}>
				<img src={src} alt={`${name} logo`} className="size-full object-contain" />
			</span>
		);
	}

	return (
		<span
			className={`flex shrink-0 items-center justify-center rounded-lg font-heading font-bold text-white ${paletteFor(name)} ${SIZES[size]}`}
			aria-hidden="true"
		>
			{initials(name)}
		</span>
	);
}
