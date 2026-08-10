export type TeamGenderCategory = "BOYS" | "GIRLS" | "COED" | "MENS" | "WOMENS" | "OPEN";

export interface Team {
	id: string;
	organizationId: string;
	name: string;
	sport: string;
	season: string | null;
	status: "ACTIVE" | "ARCHIVED";
	contactEmail: string | null;
	/** Phase 24 slice 24.5 (ADR-071): an IANA time zone id overriding the organization default, or null to inherit it. */
	timezoneOverride: string | null;
	/** Phase 35 (ADR-099): organization-defined free text, deliberately not a hardcoded global list. */
	ageGroup: string | null;
	genderCategory: TeamGenderCategory | null;
	level: string | null;
	/** Phase 35 (ADR-099): resolved (always non-null) — Rally26's default brand color when the team has no override. */
	primaryColor: string;
	secondaryColor: string;
	createdAt: string;
	updatedAt: string;
}

export interface TeamPage {
	items: Team[];
	page: number;
	size: number;
	totalElements: number;
}
