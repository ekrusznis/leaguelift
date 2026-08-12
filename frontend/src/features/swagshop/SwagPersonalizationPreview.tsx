import type { PersonalizationPlacement, SwagLogoSize } from "../store/types";

interface SwagPersonalizationPreviewProps {
	mockupFrontUrl: string | null;
	mockupBackUrl: string | null;
	logoPreviewUrl: string | null;
	productName?: string;
	placement: PersonalizationPlacement | null;
	logoSize: SwagLogoSize;
	name: string;
	number: string;
}

/**
 * Path 2 (DESIGN-DOC.md section 14.1G 24.2) live preview. Mirrors the same
 * fixed-fraction placement zones SwagDesignCompositor uses server-side so the
 * preview stays conceptually consistent with the real print — but Printify's
 * catalog only exposes print-area width/height, never its pixel offset
 * within the garment mockup photo, so this can never be pixel-exact. The
 * backend remains the authoritative, exact design generator at checkout
 * confirmation.
 */
const SCALE_FACTOR: Record<SwagLogoSize, number> = {
	SMALL: 0.72,
	STANDARD: 1.0,
	LARGE: 1.3,
};

function clamp(value: number, min: number, max: number): number {
	return Math.min(max, Math.max(min, value));
}

function logoFraction(base: number, min: number, max: number, logoSize: SwagLogoSize): number {
	return clamp(base * SCALE_FACTOR[logoSize], min, max);
}

interface LogoZone {
	widthPct: number;
	leftPct: number;
	topPct: number;
}

function logoZoneFor(placement: PersonalizationPlacement | null, logoSize: SwagLogoSize): LogoZone {
	if (placement === "LEFT_CHEST" || placement === "RIGHT_CHEST") {
		const widthPct = logoFraction(0.28, 0.16, 0.38, logoSize) * 100;
		const leftPct = placement === "LEFT_CHEST" ? 8 : 92 - widthPct;
		return { widthPct, leftPct, topPct: 8 };
	}
	if (placement === "CENTER_FRONT") {
		const widthPct = logoFraction(0.4, 0.24, 0.55, logoSize) * 100;
		return { widthPct, leftPct: (100 - widthPct) / 2, topPct: 6 };
	}
	// BACK's own front canvas, and "no placement chosen yet": logo alone, centered.
	const widthPct = logoFraction(0.35, 0.2, 0.5, logoSize) * 100;
	return { widthPct, leftPct: (100 - widthPct) / 2, topPct: 6 };
}

function MockupWithOverlay({
	mockupUrl,
	altLabel,
	logoPreviewUrl,
	logoZone,
	showLogo,
	name,
	number,
	textTopPct,
}: {
	mockupUrl: string | null;
	altLabel: string;
	logoPreviewUrl: string | null;
	logoZone: LogoZone;
	showLogo: boolean;
	name: string;
	number: string;
	textTopPct: number;
}) {
	if (!mockupUrl) return null;
	return (
		<div className="relative h-48 w-48 overflow-hidden rounded-lg border border-slate-gray/20 bg-ice-white dark:bg-[#0f172a]">
			<img src={mockupUrl} alt={altLabel} className="h-full w-full object-cover" />
			{showLogo && logoPreviewUrl && (
				<img
					src={logoPreviewUrl}
					alt="Logo placement preview"
					className="absolute object-contain drop-shadow-sm"
					style={{
						left: `${logoZone.leftPct}%`,
						top: `${logoZone.topPct}%`,
						width: `${logoZone.widthPct}%`,
						aspectRatio: "1 / 1",
					}}
				/>
			)}
			{(name || number) && (
				<div
					className="absolute left-1/2 flex -translate-x-1/2 flex-col items-center text-center leading-tight"
					style={{ top: `${textTopPct}%` }}
				>
					{name && (
						<span
							className="text-[10px] font-bold uppercase text-white"
							style={{ textShadow: "-1px -1px 0 #000, 1px -1px 0 #000, -1px 1px 0 #000, 1px 1px 0 #000" }}
						>
							{name}
						</span>
					)}
					{number && (
						<span
							className="text-sm font-bold text-white"
							style={{ textShadow: "-1px -1px 0 #000, 1px -1px 0 #000, -1px 1px 0 #000, 1px 1px 0 #000" }}
						>
							{number}
						</span>
					)}
				</div>
			)}
		</div>
	);
}

export function SwagPersonalizationPreview({
	mockupFrontUrl,
	mockupBackUrl,
	logoPreviewUrl,
	productName,
	placement,
	logoSize,
	name,
	number,
}: SwagPersonalizationPreviewProps) {
	if (!mockupFrontUrl && !mockupBackUrl) return null;

	const isBack = placement === "BACK";
	const frontZone = logoZoneFor(isBack ? null : placement, logoSize);
	const frontTextTopPct = frontZone.topPct + frontZone.widthPct + 6;

	return (
		<div className="flex flex-col gap-2">
			<div className="flex flex-wrap gap-3">
				<div className="flex flex-col items-center gap-1">
					<MockupWithOverlay
						mockupUrl={mockupFrontUrl}
						altLabel={`${productName ?? "Apparel"} — front`}
						logoPreviewUrl={logoPreviewUrl}
						logoZone={frontZone}
						showLogo
						name={isBack ? "" : name}
						number={isBack ? "" : number}
						textTopPct={frontTextTopPct}
					/>
					<span className="text-xs text-slate-gray dark:text-[#cbd5e1]">Front</span>
				</div>
				{isBack && mockupBackUrl && (
					<div className="flex flex-col items-center gap-1">
						<MockupWithOverlay
							mockupUrl={mockupBackUrl}
							altLabel={`${productName ?? "Apparel"} — back`}
							logoPreviewUrl={null}
							logoZone={frontZone}
							showLogo={false}
							name={name}
							number={number}
							textTopPct={35}
						/>
						<span className="text-xs text-slate-gray dark:text-[#cbd5e1]">Back — your name/number prints here</span>
					</div>
				)}
			</div>
			<p className="text-xs text-slate-gray dark:text-[#cbd5e1]">Preview is approximate — final placement is set at checkout.</p>
		</div>
	);
}
