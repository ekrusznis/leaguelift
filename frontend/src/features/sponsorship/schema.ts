import { z } from "zod";

/**
 * Mirrors the backend's CreateSponsorshipPackageRequest
 * (backend/src/main/kotlin/com/leaguelift/sponsorship/web/SponsorshipDto.kt). Client-side
 * validation is for UX only — the backend remains authoritative. Applies the RHF/Zod
 * three-generic pattern from the start (see DESIGN-DOC.md's known-bugs note on
 * `z.coerce.number()`/React-Hook-Form) — no `as Resolver` cast needed here.
 */
export const createSponsorshipPackageSchema = z.object({
	name: z.string().trim().min(1, "Name is required.").max(120),
	description: z.string().trim().max(2000).optional().or(z.literal("")),
	priceMinor: z.coerce.number().int("Price must be a whole number of cents.").min(0, "Price must be 0 or greater."),
	maxQuantity: z.coerce.number().int().min(1).optional().or(z.literal("")),
	exclusive: z.boolean().default(false),
	placementStartDate: z.string().optional().or(z.literal("")),
	placementEndDate: z.string().optional().or(z.literal("")),
});

export type CreateSponsorshipPackageFormValues = z.infer<typeof createSponsorshipPackageSchema>;

/**
 * Mirrors the backend's CreateSponsorshipCheckoutRequest. Public purchase form — no
 * logo field here (ADR-018: sponsor logo is an admin-side action after confirmation,
 * not a public checkout upload).
 */
export const createSponsorshipCheckoutSchema = z.object({
	sponsorName: z.string().trim().min(1, "Sponsor/company name is required.").max(120),
	sponsorContactEmail: z.string().trim().email("Enter a valid email address.").max(254).optional().or(z.literal("")),
});

export type CreateSponsorshipCheckoutFormValues = z.infer<typeof createSponsorshipCheckoutSchema>;

/** Mirrors the backend's UpdateSponsorRequest (Phase 6 remainder, ADR-019) — sponsor-contact CRM widening. Every field optional; an empty string is treated as "leave unchanged" the same way the org-admin package-edit form already works. */
export const updateSponsorSchema = z.object({
	name: z.string().trim().max(200).optional().or(z.literal("")),
	contactEmail: z.string().trim().email("Enter a valid email address.").max(254).optional().or(z.literal("")),
	phone: z.string().trim().max(40).optional().or(z.literal("")),
	companyName: z.string().trim().max(200).optional().or(z.literal("")),
	notes: z.string().trim().max(2000).optional().or(z.literal("")),
});

export type UpdateSponsorFormValues = z.infer<typeof updateSponsorSchema>;
