import { z } from "zod";
import type { MediaUsageSlot } from "./types";

const RASTER_TYPES = ["image/png", "image/jpeg", "image/webp"];
const LOGO_TYPES = [...RASTER_TYPES, "image/svg+xml"];

const LOGO_RASTER_MAX_BYTES = 10 * 1024 * 1024;
const LOGO_SVG_MAX_BYTES = 2 * 1024 * 1024;
const COVER_MAX_BYTES = 15 * 1024 * 1024;
const PROFILE_PHOTO_MAX_BYTES = 5 * 1024 * 1024;
const PRODUCT_DESIGN_RASTER_MAX_BYTES = 15 * 1024 * 1024;
const PRODUCT_DESIGN_SVG_MAX_BYTES = 2 * 1024 * 1024;

function maxBytesFor(usageSlot: MediaUsageSlot, contentType: string): number {
	if (usageSlot === "COVER") return COVER_MAX_BYTES;
	if (usageSlot === "PROFILE_PHOTO") return PROFILE_PHOTO_MAX_BYTES;
	if (usageSlot === "PRODUCT_DESIGN") return contentType === "image/svg+xml" ? PRODUCT_DESIGN_SVG_MAX_BYTES : PRODUCT_DESIGN_RASTER_MAX_BYTES;
	return contentType === "image/svg+xml" ? LOGO_SVG_MAX_BYTES : LOGO_RASTER_MAX_BYTES;
}

export function fileSchemaFor(usageSlot: MediaUsageSlot) {
	const allowedTypes = usageSlot === "COVER" || usageSlot === "PROFILE_PHOTO" ? RASTER_TYPES : LOGO_TYPES;
	return z
		.instanceof(File)
		.refine((file) => allowedTypes.includes(file.type), {
			message:
				usageSlot === "PROFILE_PHOTO"
					? "Profile photo must be PNG, JPEG, or WEBP."
					: usageSlot === "COVER"
						? "Cover image must be PNG, JPEG, or WEBP."
						: "Image must be PNG, JPEG, WEBP, or SVG.",
		})
		.refine((file) => file.size <= maxBytesFor(usageSlot, file.type), {
			message: "This file is too large.",
		});
}
