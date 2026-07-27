import { z } from "zod";

export const createHouseholdSchema = z.object({
	displayName: z.string().trim().min(1, "Display name is required.").max(120),
	contactEmail: z.string().trim().email("Enter a valid email address.").optional().or(z.literal("")),
	contactPhone: z.string().trim().max(30).optional().or(z.literal("")),
	notes: z.string().trim().max(1000).optional().or(z.literal("")),
});

export type CreateHouseholdFormValues = z.infer<typeof createHouseholdSchema>;

export const addAdultSchema = z.object({
	firstName: z.string().trim().min(1, "First name is required.").max(60),
	lastName: z.string().trim().min(1, "Last name is required.").max(60),
	email: z.string().trim().email("Enter a valid email address.").optional().or(z.literal("")),
	phone: z.string().trim().max(30).optional().or(z.literal("")),
	relationship: z.string().trim().max(60).optional().or(z.literal("")),
	isPrimary: z.boolean().default(false),
});

export type AddAdultFormValues = z.infer<typeof addAdultSchema>;

export const createParticipantSchema = z.object({
	firstName: z.string().trim().min(1, "First name is required.").max(60),
	lastName: z.string().trim().min(1, "Last name is required.").max(60),
	dateOfBirth: z.string().optional().or(z.literal("")),
	notes: z.string().trim().max(1000).optional().or(z.literal("")),
});

export type CreateParticipantFormValues = z.infer<typeof createParticipantSchema>;
