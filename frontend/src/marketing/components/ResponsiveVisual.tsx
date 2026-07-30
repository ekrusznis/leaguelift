type ResponsiveVisualProps = {
	src: string;
	alt: string;
	/** The image's real pixel width/height — used only to reserve layout space (aspect ratio) before the image loads, never to force a crop. */
	width: number;
	height: number;
};

/**
 * A full-bleed infographic-style PNG that already bakes in its own heading/copy
 * (unlike every other marketing image, which sits behind separately-rendered text).
 * Scales to the container's width at its native aspect ratio — never cropped via
 * `object-fit`, since cropping would cut off baked-in text at the edges. `width`/
 * `height` reserve the correct aspect ratio before load (avoiding layout shift);
 * actual rendered size is controlled entirely by CSS (`w-full h-auto`).
 *
 * Trade-off worth knowing: because the text is rasterized into the image, it gets
 * proportionally smaller (not reflowed) on narrow screens — there's no way around
 * that short of rebuilding the graphic as HTML/CSS. Acceptable for this preview
 * page; revisit if these visuals graduate to the production site and mobile
 * legibility turns out to matter.
 */
export function ResponsiveVisual({ src, alt, width, height }: ResponsiveVisualProps) {
	return (
		<div className="overflow-hidden rounded-[22px] border border-white/[0.12] bg-white shadow-[0_22px_60px_rgba(0,0,0,0.22)]">
			<img src={src} alt={alt} width={width} height={height} loading="lazy" className="h-auto w-full" />
		</div>
	);
}
