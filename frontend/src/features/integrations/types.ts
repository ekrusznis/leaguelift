export type EventSourceProvider = "ICS_FEED" | "MAXPREPS" | "GAMECHANGER";
export type EventSourceConnectionStatus = "ACTIVE" | "DISCONNECTED";
export type EventSourceSyncStatus = "SUCCESS" | "FAILED";

export interface EventSourceConnection {
	id: string;
	provider: EventSourceProvider;
	label: string;
	feedUrl: string | null;
	timezone: string;
	teamId: string | null;
	status: EventSourceConnectionStatus;
	lastSyncedAt: string | null;
	lastSyncStatus: EventSourceSyncStatus | null;
	lastSyncError: string | null;
	createdAt: string;
}

export interface CsvImportRowError {
	rowNumber: number;
	message: string;
}

export interface CsvImportResult {
	createdCount: number;
	updatedCount: number;
	unchangedCount: number;
	errors: CsvImportRowError[];
}
