const PALETTE = ["bg-green-500", "bg-info-600", "bg-gold-500", "bg-navy-700"];

function paletteFor(name: string) {
	const index = name.charCodeAt(0) % PALETTE.length;
	return PALETTE[index];
}

function initials(name: string) {
	const parts = name.trim().split(/\s+/);
	return ((parts[0]?.[0] ?? "") + (parts[1]?.[0] ?? "")).toUpperCase();
}

type AvatarProps = {
	name: string;
	size?: "sm" | "md" | "lg";
	/** Demo/uploaded photo URL. Falls back to initials when absent (docs/LEAGUELIFT_MEDIA_ASSET_DESIGN.md section 15.1). */
	src?: string;
};

/** Renders a photo when one is available, otherwise a deterministic initials badge. */
export function Avatar({ name, size = "md", src }: AvatarProps) {
	const dimension = { sm: "size-8 text-xs", md: "size-10 text-sm", lg: "size-14 text-base" }[size];

	if (src) {
		return (
			<img
				src={src}
				alt={`Profile image for ${name}`}
				className={`shrink-0 rounded-full object-cover ${dimension}`}
			/>
		);
	}

	return (
		<span
			className={`flex shrink-0 items-center justify-center rounded-full font-heading font-bold text-white ${paletteFor(name)} ${dimension}`}
			aria-hidden="true"
		>
			{initials(name)}
		</span>
	);
}
