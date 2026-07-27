import { z } from "zod";

export const createTournamentSchema = z
	.object({
		name: z.string().trim().min(1, "Name is required.").max(120),
		sport: z.string().trim().max(60).optional().or(z.literal("")),
		startDate: z.string().optional().or(z.literal("")),
		endDate: z.string().optional().or(z.literal("")),
		location: z.string().trim().max(200).optional().or(z.literal("")),
		contactEmail: z.string().trim().email("Enter a valid email address.").optional().or(z.literal("")),
	})
	.refine(
		(data) => {
			if (data.startDate && data.endDate) {
				return data.endDate >= data.startDate;
			}
			return true;
		},
		{ message: "End date must not be before start date.", path: ["endDate"] },
	);

export type CreateTournamentFormValues = z.infer<typeof createTournamentSchema>;
