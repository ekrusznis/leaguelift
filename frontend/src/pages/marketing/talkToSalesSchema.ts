import { z } from "zod";

export const APPLICANT_ROLE_OPTIONS = [
	{ value: "ORGANIZATION_OWNER", label: "Organization owner" },
	{ value: "DIRECTOR", label: "League or club director" },
	{ value: "TREASURER", label: "Treasurer" },
	{ value: "BOARD_MEMBER", label: "Board member" },
	{ value: "TEAM_MANAGER", label: "Team manager" },
	{ value: "TOURNAMENT_DIRECTOR", label: "Tournament director" },
	{ value: "FUNDRAISING_COORDINATOR", label: "Fundraising coordinator" },
	{ value: "MERCHANDISE_COORDINATOR", label: "Merchandise coordinator" },
	{ value: "OTHER", label: "Other" },
] as const;

export const ORGANIZATION_TYPE_OPTIONS = [
	{ value: "RECREATIONAL_LEAGUE", label: "Recreational league" },
	{ value: "TRAVEL_CLUB", label: "Travel club" },
	{ value: "INDIVIDUAL_TEAM", label: "Individual team" },
	{ value: "TOURNAMENT_OPERATOR", label: "Tournament operator" },
	{ value: "BOOSTER_ORGANIZATION", label: "Booster organization" },
	{ value: "MULTISPORT_FACILITY", label: "Multisport facility" },
	{ value: "OTHER", label: "Other" },
] as const;

export const PRODUCT_INTEREST_OPTIONS = [
	{ value: "TEAM_PAGES", label: "Team Pages" },
	{ value: "TOURNAMENT_PAGES", label: "Tournament Pages" },
	{ value: "FUNDRAISING", label: "Fundraising" },
	{ value: "APPAREL", label: "Apparel" },
	{ value: "DUES_AND_FEES", label: "Dues & Fees" },
	{ value: "FAMILY_CREDITS", label: "Family Credits" },
	{ value: "SPONSORSHIPS", label: "Sponsorships" },
	{ value: "REPORTING", label: "Reporting" },
] as const;

const wholeDollar = z
	.string()
	.trim()
	.regex(/^\d*$/, "Enter a whole-dollar amount.")
	.optional()
	.or(z.literal(""));

export const talkToSalesSchema = z.object({
	firstName: z.string().trim().min(1, "First name is required."),
	lastName: z.string().trim().min(1, "Last name is required."),
	workEmail: z.string().trim().min(1, "Work email is required.").email("Enter a valid email address."),
	phone: z.string().trim().min(1, "Phone number is required."),
	applicantRole: z.string().min(1, "Select your role."),

	organizationName: z.string().trim().min(1, "Organization name is required."),
	organizationWebsite: z.string().trim().url("Enter a valid URL.").optional().or(z.literal("")),
	city: z.string().trim().min(1, "City is required."),
	state: z.string().trim().min(1, "State is required."),
	organizationType: z.string().min(1, "Select an organization type."),
	sports: z.string().trim().min(1, "List at least one sport."),
	numberOfTeams: z.string().trim().regex(/^\d*$/, "Enter a whole number.").optional().or(z.literal("")),
	approxAthletes: z.string().trim().regex(/^\d*$/, "Enter a whole number.").optional().or(z.literal("")),

	currentSoftware: z.string().trim().max(200).optional().or(z.literal("")),
	currentMerchProvider: z.string().trim().max(200).optional().or(z.literal("")),
	estMerchRevenue: wholeDollar,
	estFundraisingRevenue: wholeDollar,
	estSponsorshipRevenue: wholeDollar,
	currentDuesMethod: z.string().trim().max(200).optional().or(z.literal("")),

	productInterest: z.array(z.string()).min(1, "Select at least one area of interest."),

	biggestChallenge: z.string().trim().max(800, "Keep this under 800 characters.").optional().or(z.literal("")),
	desiredLaunchMonth: z.string().trim().optional().or(z.literal("")),
	additionalComments: z.string().trim().max(800, "Keep this under 800 characters.").optional().or(z.literal("")),

	howHeard: z.string().trim().max(200).optional().or(z.literal("")),

	consentContacted: z.literal(true, { error: "You must consent to be contacted about your request." }),
	confirmAdult: z.literal(true, { error: "You must confirm you are at least 18 years old." }),
	agreePrivacy: z.literal(true, { error: "You must agree to the Privacy Policy." }),

	/** Honeypot: must stay empty. Real bot/rate-limit protection still needs a backend (see docs/launch-checklist.md). */
	company: z.string().max(0).optional().or(z.literal("")),
});

export type TalkToSalesFormValues = z.infer<typeof talkToSalesSchema>;
