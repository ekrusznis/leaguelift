import { z } from "zod";

/**
 * Mirrors the backend's CreateStoreRequest/CreateProductRequest/CreateProductVariantRequest
 * (backend/src/main/kotlin/com/leaguelift/store/web/*.kt). Client-side validation is
 * for UX only — the backend remains authoritative.
 */
export const createStoreSchema = z.object({
	teamId: z.string().optional().or(z.literal("")),
	name: z.string().trim().min(1, "Name is required.").max(120),
	slug: z
		.string()
		.trim()
		.min(1, "Slug is required.")
		.regex(/^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?$/, "Use lowercase letters, numbers, and hyphens only."),
});

export type CreateStoreFormValues = z.infer<typeof createStoreSchema>;

export const createProductSchema = z.object({
	name: z.string().trim().min(1, "Name is required.").max(120),
	description: z.string().trim().max(2000).optional().or(z.literal("")),
	printifyBlueprintId: z.coerce.number().int("Choose a product type."),
	printifyPrintPosition: z.string().trim().min(1).default("front"),
});

export type CreateProductFormValues = z.infer<typeof createProductSchema>;

export const createProductVariantSchema = z.object({
	printifyPrintProviderId: z.coerce.number().int("Choose a print provider."),
	printifyVariantId: z.coerce.number().int("Choose a size/color."),
	label: z.string().trim().min(1, "Label is required.").max(120),
	priceMinor: z.coerce.number().int("Price must be a whole number of cents.").min(0, "Price must be 0 or greater."),
});

export type CreateProductVariantFormValues = z.infer<typeof createProductVariantSchema>;
