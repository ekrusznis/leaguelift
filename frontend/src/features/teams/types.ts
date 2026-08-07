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
	createdAt: string;
	updatedAt: string;
}

export interface TeamPage {
	items: Team[];
	page: number;
	size: number;
	totalElements: number;
}
