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

export interface QuickBooksConnectionSetting {
	connectionId: string;
	realmId: string | null;
	companyName: string | null;
	environment: "SANDBOX" | "PRODUCTION";
	exportPolicy: "READ_ONLY" | "EXPORT_PREVIEW_ONLY";
	accountingBasis: "ACCRUAL" | "CASH";
	defaultCurrency: string | null;
	lastCompanyReadAt: string | null;
	lastAccountsReadAt: string | null;
	updatedAt: string;
}

export type QuickBooksMappingType =
	| "SALES_INCOME"
	| "CONTRIBUTION_INCOME"
	| "SPONSORSHIP_INCOME"
	| "REFUNDS"
	| "FEES_RECEIVABLE"
	| "BANK_CLEARING"
	| "PAYOUT_CLEARING";


export interface QuickBooksAccount {
	id: string;
	name: string;
	accountType: string;
	active: boolean;
}

export interface QuickBooksExportCandidateCounts {
	contributions: number;
	sponsorships: number;
	orders: number;
	feePayments: number;
	corrections: number;
	total: number;
}

export interface QuickBooksExportPreview {
	periodStart: string;
	periodEnd: string;
	counts: QuickBooksExportCandidateCounts;
	missingMappings: QuickBooksMappingType[];
	exportAllowed: false;
	reason: string;
}
export interface QuickBooksAccountMapping {
	id: string;
	mappingType: QuickBooksMappingType;
	externalAccountId: string;
	externalAccountName: string;
	externalAccountType: string | null;
	updatedAt: string;
}

export interface QuickBooksExportBatch {
	id: string;
	status: "PREVIEWED" | "READY" | "BLOCKED" | "EXPORTED" | "PARTIAL" | "FAILED";
	periodStart: string;
	periodEnd: string;
	candidateCount: number;
	exportedCount: number;
	failedCount: number;
	createdAt: string;
	completedAt: string | null;
}

export interface QuickBooksOverview {
	catalog: IntegrationCatalogItem;
	setting: QuickBooksConnectionSetting | null;
	mappings: QuickBooksAccountMapping[];
	recentBatches: QuickBooksExportBatch[];
	providerWritesEnabled: boolean;
	accountingReviewRequired: boolean;
}

export interface SportsDataImportRun {
	id: string;
	provider: "SPORTSENGINE" | "GAMECHANGER" | "MAXPREPS";
	sourceMode: "OAUTH" | "API_TOKEN" | "FILE_IMPORT" | "ICS";
	status: "PREVIEWED" | "READY" | "BLOCKED" | "IMPORTED" | "PARTIAL" | "FAILED";
	commitAllowed: boolean;
	discoveredCount: number;
	validCount: number;
	duplicateCount: number;
	conflictCount: number;
	errorCount: number;
	previewHash: string;
	requestedAt: string;
	completedAt: string | null;
}

export interface SportsDataOverview {
	providers: IntegrationCatalogItem[];
	recentRuns: SportsDataImportRun[];
	directProviderImportEnabled: boolean;
	reviewedFileImportAvailable: boolean;
}

export interface SportsDataImportIssue {
	id: string;
	rowNumber: number | null;
	entityType: string | null;
	externalEntityId: string | null;
	severity: "WARNING" | "ERROR";
	code: string;
	message: string;
}

export interface SportsDataExternalRecord {
	entityType: string;
	externalId: string;
	externalParentId: string | null;
	name: string | null;
	payload: Record<string, string | null>;
}

export interface SportsDataPreview {
	run: SportsDataImportRun;
	issues: SportsDataImportIssue[];
	records: SportsDataExternalRecord[];
	directImportEnabled: false;
	message: string;
}

export interface IntegrationSyncRun {
	id: string;
	connectionId: string | null;
	provider: IntegrationProvider;
	ownerType: "PLATFORM" | "ORGANIZATION" | "USER";
	organizationId: string | null;
	userId: string | null;
	direction: "READ" | "WRITE" | "WEBHOOK" | "HEALTH";
	trigger: "MANUAL" | "SCHEDULED" | "OUTBOX" | "WEBHOOK" | "STUB";
	status: "QUEUED" | "RUNNING" | "SUCCEEDED" | "PARTIAL" | "FAILED" | "CANCELLED";
	discoveredCount: number;
	createdCount: number;
	updatedCount: number;
	skippedCount: number;
	failedCount: number;
	rateLimitRemaining: number | null;
	rateLimitResetsAt: string | null;
	errorCode: string | null;
	errorMessage: string | null;
	requestedAt: string;
	startedAt: string | null;
	completedAt: string | null;
}

export interface ProviderContractCapability {
	name: string;
	status: "READY" | "SCAFFOLDED" | "NOT_CONFIGURED" | "NOT_APPLICABLE";
	summary: string;
}

export interface PlatformProviderContract {
	provider: IntegrationProvider;
	configurationStatus: "CONFIGURED" | "PARTIAL" | "NOT_CONFIGURED" | "BUILT_IN";
	credentialOwnership: string;
	credentialRotation: string;
	healthCheckMode: string;
	liveProbeEnabled: boolean;
	webhookVerification: string;
	idempotency: string;
	productionStubBlocked: boolean;
	capabilities: ProviderContractCapability[];
}
