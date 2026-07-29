import { z } from "zod";
import type { MediaUsageSlot } from "./types";

const RASTER_TYPES = ["image/png", "image/jpeg", "image/webp"];
const LOGO_TYPES = [...RASTER_TYPES, "image/svg+xml"];

const LOGO_RASTER_MAX_BYTES = 10 * 1024 * 1024;
const LOGO_SVG_MAX_BYTES = 2 * 1024 * 1024;
const COVER_MAX_BYTES = 15 * 1024 * 1024;

function maxBytesFor(usageSlot: MediaUsageSlot, contentType: string): number {
	if (usageSlot === "COVER") return COVER_MAX_BYTES;
	return contentType === "image/svg+xml" ? LOGO_SVG_MAX_BYTES : LOGO_RASTER_MAX_BYTES;
}

/**
 * Client-side pre-checks only, mirroring the backend's UploadLimits.kt for early
 * feedback before spending a network round trip. The backend re-derives the real
 * content type from the uploaded bytes and remains authoritative (ADR-012).
 */
export function fileSchemaFor(usageSlot: MediaUsageSlot) {
	const allowedTypes = usageSlot === "LOGO" ? LOGO_TYPES : RASTER_TYPES;
	return z
		.instanceof(File)
		.refine((file) => allowedTypes.includes(file.type), {
			message: usageSlot === "LOGO" ? "Logo must be PNG, JPEG, WEBP, or SVG." : "Cover image must be PNG, JPEG, or WEBP.",
		})
		.refine((file) => file.size <= maxBytesFor(usageSlot, file.type), {
			message: "This file is too large.",
		});
}
