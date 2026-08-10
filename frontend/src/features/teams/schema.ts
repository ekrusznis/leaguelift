import { z } from "zod";

export const createTeamSchema = z.object({
	name: z.string().trim().min(1, "Name is required.").max(120),
	sport: z.string().trim().min(1, "Sport is required.").max(60),
	season: z.string().trim().max(60).optional().or(z.literal("")),
	contactEmail: z.string().trim().email("Enter a valid email address.").optional().or(z.literal("")),
	ageGroup: z.string().trim().max(60).optional().or(z.literal("")),
	genderCategory: z.enum(["BOYS", "GIRLS", "COED", "MENS", "WOMENS", "OPEN"]).optional().or(z.literal("")),
	level: z.string().trim().max(60).optional().or(z.literal("")),
});

export type CreateTeamFormValues = z.infer<typeof createTeamSchema>;
