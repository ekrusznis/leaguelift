import type { ReactNode } from "react";

type CardBackgroundProps = {
	src: string;
	children: ReactNode;
	className?: string;
	overlay?: string;
};

/**
 * Background photo + gradient overlay wrapper (docs/LEAGUELIFT_MEDIA_ASSET_DESIGN.md
 * section 14.4). The image is decorative (empty alt, section 16.2) — the overlay
 * exists purely to keep foreground text readable per the sales-site's imagery rules.
 */
export function CardBackground({ src, children, className = "", overlay }: CardBackgroundProps) {
	return (
		<div className={`relative overflow-hidden rounded-2xl shadow-[0_8px_24px_rgba(11,31,51,0.06)] ${className}`}>
			<img src={src} alt="" aria-hidden="true" className="absolute inset-0 size-full object-cover" />
			<div
				className="absolute inset-0"
				style={{ background: overlay ?? "linear-gradient(135deg, rgba(6,19,33,0.92) 20%, rgba(6,19,33,0.55) 100%)" }}
			/>
			<div className="relative">{children}</div>
		</div>
	);
}
