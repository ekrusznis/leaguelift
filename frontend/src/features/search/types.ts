export type SearchResultType = "TEAM" | "PARTICIPANT" | "HOUSEHOLD" | "ORGANIZATION";

export interface SearchHit {
	type: SearchResultType;
	id: string;
	label: string;
	subtitle: string | null;
}

export interface SearchResponse {
	items: SearchHit[];
}
