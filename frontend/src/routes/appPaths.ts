export type OrganizationSection =
	| "overview"
	| "onboarding"
	| "corrections"
	| "teams"
	| "tournaments"
	| "households"
	| "fees"
	| "fundraising"
	| "swag-shop"
	| "financial-operations"
	| "sponsorships"
	| "events"
	| "reports"
	| "documents"
	| "eligibility"
	| "members"
	| "integrations"
	| "settings";

export type HouseholdSection = "profile" | "participants" | "fees" | "events" | "documents" | "media" | "corrections";
export const appPaths = {
	dashboard: (hash?: string) => `/app${hash ? `#${hash}` : ""}`,
	organizations: () => "/app/organizations",
	organization: (organizationId: string, section: OrganizationSection = "overview") =>
		`/app/organizations/${organizationId}/${section}`,
	organizationBilling: (organizationId: string) => `/app/organizations/${organizationId}/billing`,
	collections: (organizationId: string) => `/app/organizations/${organizationId}/collections`,
	disputes: (organizationId: string) => `/app/organizations/${organizationId}/disputes`,
	swagShopOrder: (organizationId: string) => `/app/organizations/${organizationId}/swag-shop/order`,
	household: (organizationId: string, householdId: string, section: HouseholdSection = "profile") =>
		`/app/organizations/${organizationId}/households/${householdId}/${section}`,
	organizationEvents: (organizationId: string) => `/app/organizations/${organizationId}/events`,
	teamEvents: (organizationId: string, teamId: string) =>
		`/app/organizations/${organizationId}/teams/${teamId}/events`,
	teamRoster: (organizationId: string, teamId: string) =>
		`/app/organizations/${organizationId}/teams/${teamId}/roster`,
	tournamentEvents: (organizationId: string, tournamentId: string) =>
		`/app/organizations/${organizationId}/tournaments/${tournamentId}/events`,
	householdEvents: (organizationId: string, householdId: string) =>
		`/app/organizations/${organizationId}/households/${householdId}/events`,
	participantEvents: (organizationId: string, participantId: string) =>
		`/app/organizations/${organizationId}/participants/${participantId}/events`,
	event: (organizationId: string, eventId: string, search?: URLSearchParams) =>
		`/app/organizations/${organizationId}/events/${eventId}${search && search.size > 0 ? `?${search.toString()}` : ""}`,
	actionCenter: () => "/app/action-center",
	announcements: () => "/app/announcements",
	messages: () => "/app/messages",
	personalIntegrations: () => "/app/integrations",
	help: () => "/app/help",
	helpArticle: (slug: string) => `/app/help/${slug}`,
	helpSupport: () => "/app/help/support",
	platformOrganizations: () => "/app/platform/organizations",
	platformOrganization: (organizationId: string) => `/app/platform/organizations/${organizationId}`,
	platformUsers: () => "/app/platform/users",
	platformDuplicateIdentities: () => "/app/platform/data-integrity/duplicates",
	platformOperations: () => "/app/platform/operations",
	platformReports: () => "/app/platform/reports",
	platformAudit: () => "/app/platform/audit",
	platformSupportSessions: () => "/app/platform/support-sessions",
	platformHelpArticles: () => "/app/platform/help-articles",
	platformSupportCases: () => "/app/platform/support-cases",
	platformSwagShop: () => "/app/platform/swag-shop",
	platformPayments: () => "/app/platform/payments",
	platformRoster: () => "/app/platform/roster",
	platformSubscriptions: () => "/app/platform/subscriptions",
} as const;
