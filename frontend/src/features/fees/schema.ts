import { z } from "zod";

export const createFeeTemplateSchema = z.object({
	name: z.string().trim().min(1, "Name is required.").max(120),
	description: z.string().trim().max(500).optional().or(z.literal("")),
	amountMinor: z.coerce.number().int("Amount must be a whole number of cents.").min(0, "Amount must be 0 or greater."),
	currency: z.string().length(3).default("USD"),
});

export type CreateFeeTemplateFormValues = z.infer<typeof createFeeTemplateSchema>;

export const createFeeAssignmentSchema = z.object({
	description: z.string().trim().min(1, "Description is required.").max(255),
	originalAmountMinor: z.coerce.number().int("Amount must be a whole number of cents.").min(0, "Amount must be 0 or greater."),
	currency: z.string().length(3).default("USD"),
	dueDate: z.string().optional().or(z.literal("")),
	feeTemplateId: z.string().optional().or(z.literal("")),
	participantId: z.string().optional().or(z.literal("")),
});

export type CreateFeeAssignmentFormValues = z.infer<typeof createFeeAssignmentSchema>;
