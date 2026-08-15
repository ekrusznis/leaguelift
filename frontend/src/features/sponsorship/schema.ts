import { z } from "zod";

export const createSponsorshipPackageSchema = z.object({
	name: z.string().trim().min(1, "Name is required.").max(120),
	description: z.string().trim().max(2000).optional().or(z.literal("")),
	priceDollars: z.coerce.number().min(0, "Price must be 0 or greater."),
	currency: z.string().length(3).default("USD"),
	maxQuantity: z.coerce.number().int().min(1).optional().or(z.literal("")),
	exclusive: z.boolean().default(false),
	placementStartDate: z.string().optional().or(z.literal("")),
	placementEndDate: z.string().optional().or(z.literal("")),
});

export type CreateSponsorshipPackageFormValues = z.infer<typeof createSponsorshipPackageSchema>;

export const createSponsorshipCheckoutSchema = z.object({
	sponsorName: z.string().trim().min(1, "Sponsor/company name is required.").max(120),
	sponsorContactEmail: z.string().trim().email("Enter a valid email address.").max(254).optional().or(z.literal("")),
});

export type CreateSponsorshipCheckoutFormValues = z.infer<typeof createSponsorshipCheckoutSchema>;

export const updateSponsorSchema = z.object({
	name: z.string().trim().max(200).optional().or(z.literal("")),
	contactEmail: z.string().trim().email("Enter a valid email address.").max(254).optional().or(z.literal("")),
	phone: z.string().trim().max(40).optional().or(z.literal("")),
	companyName: z.string().trim().max(200).optional().or(z.literal("")),
	notes: z.string().trim().max(2000).optional().or(z.literal("")),
});

export type UpdateSponsorFormValues = z.infer<typeof updateSponsorSchema>;
