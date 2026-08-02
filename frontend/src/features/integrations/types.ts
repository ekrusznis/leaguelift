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

export type IntegrationProvider =
	| "GOOGLE_CALENDAR"
	| "QUICKBOOKS_ONLINE"
	| "SPORTSENGINE"
	| "GAMECHANGER"
	| "MAXPREPS"
	| "ICS_FEED"
	| "CSV_IMPORT"
	| "STRIPE"
	| "PRINTIFY"
	| "RESEND"
	| "TWILIO"
	| "DIGITALOCEAN_SPACES"
	| "GOOGLE_MAPS";

export type IntegrationReadiness = "AVAILABLE" | "NOT_CONFIGURED" | "PARTNER_PENDING" | "PLATFORM_MANAGED" | "UNSUPPORTED";
export type IntegrationConnectionStatus =
	| "NOT_CONFIGURED"
	| "AVAILABLE"
	| "AUTHORIZATION_PENDING"
	| "CONNECTED"
	| "DEGRADED"
	| "REVOKED"
	| "DISCONNECTED"
	| "UNSUPPORTED";

export interface IntegrationConnectionSummary {
	id: string;
	provider: IntegrationProvider;
	category: string;
	ownerType: "PLATFORM" | "ORGANIZATION" | "USER";
	organizationId: string | null;
	userId: string | null;
	authMode: string;
	status: IntegrationConnectionStatus;
	grantedScopes: string[];
	externalAccountId: string | null;
	externalAccountName: string | null;
	hasStoredCredential: boolean;
	accessTokenExpiresAt: string | null;
	lastSuccessfulSyncAt: string | null;
	lastHealthCheckAt: string | null;
	lastErrorCode: string | null;
	lastErrorMessage: string | null;
	createdAt: string;
	updatedAt: string;
	connectedAt: string | null;
	revokedAt: string | null;
	disconnectedAt: string | null;
}

export interface IntegrationCatalogItem {
	provider: IntegrationProvider;
	displayName: string;
	category: string;
	ownerType: "PLATFORM" | "ORGANIZATION" | "USER";
	authMode: string;
	supportedAuthModes: string[];
	readiness: IntegrationReadiness;
	adapterMode: string;
	description: string;
	activationRequirement: string;
	defaultScopes: string[];
	stub: boolean;
	connection: IntegrationConnectionSummary | null;
}
