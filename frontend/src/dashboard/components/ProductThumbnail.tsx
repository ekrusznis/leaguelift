import type { ReactNode } from "react";

type ProductThumbnailProps = {
	src?: string;
	alt: string;
	fallbackIcon: ReactNode;
	size?: "sm" | "md" | "lg";
};

const SIZES = { sm: "size-10", md: "size-12", lg: "size-14" };

/** Square product image container with a category-icon fallback (docs/LEAGUELIFT_MEDIA_ASSET_DESIGN.md section 14.5/15.6). */
export function ProductThumbnail({ src, alt, fallbackIcon, size = "md" }: ProductThumbnailProps) {
	if (src) {
		return (
			<span className={`flex shrink-0 items-center justify-center overflow-hidden rounded-lg border border-slate-200 bg-white ${SIZES[size]}`}>
				<img src={src} alt={alt} className="size-full object-contain" />
			</span>
		);
	}

	return (
		<span className={`flex shrink-0 items-center justify-center rounded-lg bg-navy-900 text-white ${SIZES[size]}`}>
			{fallbackIcon}
		</span>
	);
}
