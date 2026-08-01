export interface SeasonRolloverRequest {
	sourceTeamId: string;
	newTeamName: string;
	newSeason: string;
	archiveSourceTeam: boolean;
	copyRoster: boolean;
	copyStaff: boolean;
	copyBranding: boolean;
}

export interface SeasonRolloverTeam {
	id: string | null;
	name: string;
	sport: string;
	season: string | null;
	contactEmail: string | null;
}

export interface SeasonRolloverRosterItem {
	participantId: string;
	displayName: string;
	priorJoinedAt: string | null;
}

export interface SeasonRolloverStaffItem {
	assignmentId: string;
	userId: string;
	displayName: string;
	email: string;
	role: string;
}

export interface SeasonRolloverBrandingItem {
	assignmentId: string;
	assetId: string;
	usageSlot: string;
	fileName: string;
	publicationStatus: string;
	visibility: string;
	altText: string | null;
}

export interface SeasonRolloverPreview {
	confirmationHash: string;
	sourceTeam: SeasonRolloverTeam;
	destinationTeam: SeasonRolloverTeam;
	archiveSourceTeam: boolean;
	roster: SeasonRolloverRosterItem[];
	staff: SeasonRolloverStaffItem[];
	branding: SeasonRolloverBrandingItem[];
	warnings: string[];
	excludedData: string[];
}

export interface SeasonRolloverResult {
	runId: string;
	confirmationHash: string;
	sourceTeamId: string;
	destinationTeam: SeasonRolloverTeam;
	sourceArchived: boolean;
	rosterCopiedCount: number;
	staffCopiedCount: number;
	brandingCopiedCount: number;
	completedAt: string;
}
