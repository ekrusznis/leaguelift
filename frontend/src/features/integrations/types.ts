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

export interface AuthorizationStartResponse {
	provider: IntegrationProvider;
	connectionId: string;
	authorizationUrl: string;
	expiresAt: string;
}

export interface IntegrationHealthResponse {
	id: string;
	connectionId: string;
	status: "HEALTHY" | "DEGRADED" | "FAILED";
	latencyMs: number | null;
	errorCode: string | null;
	errorMessage: string | null;
	checkedAt: string;
}

export interface GoogleCalendarSetting {
	connectionId: string;
	selectedCalendarId: string | null;
	selectedCalendarName: string | null;
	selectedCalendarTimezone: string | null;
	syncDirection: "LEAGUELIFT_TO_GOOGLE";
	automaticSyncEnabled: false;
	lastCalendarListedAt: string | null;
	createdAt: string;
	updatedAt: string;
}

export interface GoogleCalendarOverview {
	catalog: IntegrationCatalogItem;
	setting: GoogleCalendarSetting | null;
	mappingCount: number;
	icsFallbackAvailable: boolean;
	automaticSyncAvailable: boolean;
}

export interface GoogleCalendarDescriptor {
	id: string;
	name: string;
	timezone: string | null;
	primary: boolean;
	writable: boolean;
}

export interface GoogleCalendarEventMapping {
	id: string;
	connectionId: string;
	eventId: string;
	externalCalendarId: string;
	externalEventId: string;
	externalEtag: string | null;
	syncStatus: "PENDING" | "SYNCED" | "FAILED" | "DELETED";
	lastExportHash: string | null;
	lastSyncedAt: string | null;
	lastErrorCode: string | null;
	lastErrorMessage: string | null;
	createdAt: string;
	updatedAt: string;
}

export interface PlatformIntegrationConfigurationCheck {
	label: string;
	configured: boolean;
}

export interface PlatformIntegrationReadiness {
	provider: IntegrationProvider;
	displayName: string;
	category: string;
	status: "CONFIGURED" | "PARTIAL" | "NOT_CONFIGURED" | "BUILT_IN";
	mode: string;
	summary: string;
	checks: PlatformIntegrationConfigurationCheck[];
}
