type TeamLogoProps = {
	src: string;
	alt: string;
	size?: "sm" | "md" | "lg";
};

const SIZES = { sm: "size-8 p-1", md: "size-11 p-1.5", lg: "size-14 p-2" };

/**
 * Small dark chip housing a team/org crest (docs/LEAGUELIFT_MEDIA_ASSET_DESIGN.md
 * section 14.3). The generated demo crests keep their moody vignette background
 * (they weren't rendered on transparent backgrounds) — a dark rounded chip is a
 * cheap, consistent way to house that on light cards rather than showing a stray
 * dark rectangle.
 */
export function TeamLogo({ src, alt, size = "md" }: TeamLogoProps) {
	return (
		<span className={`flex shrink-0 items-center justify-center overflow-hidden rounded-lg bg-navy-900 ${SIZES[size]}`}>
			<img src={src} alt={alt} className="size-full object-contain" />
		</span>
	);
}
