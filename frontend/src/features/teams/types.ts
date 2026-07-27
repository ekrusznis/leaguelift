export interface Team {
	id: string;
	organizationId: string;
	name: string;
	sport: string;
	season: string | null;
	status: "ACTIVE" | "ARCHIVED";
	contactEmail: string | null;
	createdAt: string;
	updatedAt: string;
}

export interface TeamPage {
	items: Team[];
	page: number;
	size: number;
	totalElements: number;
}
