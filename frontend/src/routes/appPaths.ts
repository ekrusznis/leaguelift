export type OrganizationSection =
	| "overview"
	| "onboarding"
	| "teams"
	| "tournaments"
	| "households"
	| "fees"
	| "fundraising"
	| "stores"
	| "sponsorships"
	| "events"
	| "reports"
	| "documents"
	| "members"
	| "settings";

export type HouseholdSection = "profile" | "participants" | "fees" | "events" | "documents";

export const appPaths = {
	dashboard: (hash?: string) => `/app${hash ? `#${hash}` : ""}`,
	organizations: () => "/app/organizations",
	organization: (organizationId: string, section: OrganizationSection = "overview") =>
		`/app/organizations/${organizationId}/${section}`,
	collections: (organizationId: string) => `/app/organizations/${organizationId}/collections`,
	household: (organizationId: string, householdId: string, section: HouseholdSection = "profile") =>
		`/app/organizations/${organizationId}/households/${householdId}/${section}`,
	organizationEvents: (organizationId: string) => `/app/organizations/${organizationId}/events`,
	teamEvents: (organizationId: string, teamId: string) =>
		`/app/organizations/${organizationId}/teams/${teamId}/events`,
	tournamentEvents: (organizationId: string, tournamentId: string) =>
		`/app/organizations/${organizationId}/tournaments/${tournamentId}/events`,
	householdEvents: (organizationId: string, householdId: string) =>
		`/app/organizations/${organizationId}/households/${householdId}/events`,
	participantEvents: (organizationId: string, participantId: string) =>
		`/app/organizations/${organizationId}/participants/${participantId}/events`,
	event: (organizationId: string, eventId: string, search?: URLSearchParams) =>
		`/app/organizations/${organizationId}/events/${eventId}${search && search.size > 0 ? `?${search.toString()}` : ""}`,
	platformOrganizations: () => "/app/platform/organizations",
	platformOrganization: (organizationId: string) => `/app/platform/organizations/${organizationId}`,
	platformUsers: () => "/app/platform/users",
	platformOperations: () => "/app/platform/operations",
	platformReports: () => "/app/platform/reports",
	platformAudit: () => "/app/platform/audit",
	platformSupportSessions: () => "/app/platform/support-sessions",
} as const;
