import { z } from "zod";

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

export const manualVendorSchema = z.object({
	name: z.string().trim().min(1, "Vendor name is required.").max(160),
	contactName: z.string().trim().max(160).optional().or(z.literal("")),
	contactEmail: z.string().trim().email("Enter a valid email.").max(254).optional().or(z.literal("")),
	phone: z.string().trim().max(40).optional().or(z.literal("")),
	websiteUrl: z.string().trim().url("Enter a valid URL.").refine((value) => value === "" || value.startsWith("https://"), "Use an HTTPS URL.").optional().or(z.literal("")),
	notes: z.string().trim().max(2000).optional().or(z.literal("")),
});
export type ManualVendorFormValues = z.infer<typeof manualVendorSchema>;

export const createProductSchema = z.object({
	name: z.string().trim().min(1, "Name is required.").max(120),
	description: z.string().trim().max(2000).optional().or(z.literal("")),
	catalogSource: z.enum(["PRINTIFY", "MANUAL"]),
	manualVendorId: z.string().optional().or(z.literal("")),
	printifyBlueprintId: z.coerce.number().int().optional(),
	printifyPrintPosition: z.string().trim().min(1).default("front"),
}).superRefine((value, ctx) => {
	if (value.catalogSource === "PRINTIFY" && (!value.printifyBlueprintId || value.printifyBlueprintId <= 0)) {
		ctx.addIssue({ code: "custom", path: ["printifyBlueprintId"], message: "Choose a product type." });
	}
});
export type CreateProductFormValues = z.infer<typeof createProductSchema>;

export const createProductVariantSchema = z.object({
	printifyPrintProviderId: z.coerce.number().int("Choose a print provider."),
	printifyVariantId: z.coerce.number().int("Choose a size/color."),
	label: z.string().trim().min(1, "Label is required.").max(120),
	/** Blank means "compute from the organization's markup rule" (MarkupRuleService). */
	priceMinor: z.preprocess(
		(value) => (value === "" || value === null || value === undefined ? undefined : value),
		z.coerce.number().int("Price must be a whole number of cents.").min(0, "Price must be 0 or greater.").optional(),
	),
});
export type CreateProductVariantFormValues = z.infer<typeof createProductVariantSchema>;

export const manualProductVariantSchema = z.object({
	label: z.string().trim().min(1, "Label is required.").max(120),
	sku: z.string().trim().max(120).optional().or(z.literal("")),
	size: z.string().trim().max(80).optional().or(z.literal("")),
	color: z.string().trim().max(80).optional().or(z.literal("")),
	currency: z.string().trim().length(3, "Use a three-letter currency code.").default("USD"),
	costMinor: z.coerce.number().int().min(0, "Cost must be 0 or greater."),
	priceMinor: z.coerce.number().int().min(0, "Price must be 0 or greater."),
	isActive: z.boolean().default(true),
}).refine((value) => value.priceMinor >= value.costMinor, { path: ["priceMinor"], message: "Price cannot be below cost." });
export type ManualProductVariantFormValues = z.infer<typeof manualProductVariantSchema>;
