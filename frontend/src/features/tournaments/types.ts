export interface Tournament {
	id: string;
	organizationId: string;
	name: string;
	sport: string | null;
	status: "ACTIVE" | "ARCHIVED";
	startDate: string | null;
	endDate: string | null;
	location: string | null;
	contactEmail: string | null;
	/** Phase 24 slice 24.5 (ADR-071): an IANA time zone id overriding the organization default, or null to inherit it. */
	timezoneOverride: string | null;
	createdAt: string;
	updatedAt: string;
}

export interface TournamentPage {
	items: Tournament[];
	page: number;
	size: number;
	totalElements: number;
}
