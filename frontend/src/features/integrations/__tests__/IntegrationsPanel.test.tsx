import { screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { renderWithProviders } from "../../../test/testUtils";
import { IntegrationsPanel } from "../IntegrationsPanel";

function response(body: unknown) {
	return Promise.resolve(new Response(JSON.stringify(body), { status: 200, headers: { "content-type": "application/json" } }));
}

// IntegrationsPanel always renders QuickBooksScaffoldPanel + SportsDataScaffoldPanel alongside
// whatever the test itself cares about, so every stubbed fetch needs well-formed fallbacks for
// their overview endpoints or those components throw during render before any assertion runs.
function defaultFetchStub(url: string) {
	if (url.includes("/integrations/quickbooks/mapping-rules")) return response([]);
	if (url.includes("/integrations/quickbooks")) {
		return response({
			catalog: { provider: "QUICKBOOKS_ONLINE", displayName: "QuickBooks Online", category: "ACCOUNTING", ownerType: "ORGANIZATION", authMode: "OAUTH2", supportedAuthModes: ["OAUTH2"], readiness: "NOT_CONFIGURED", adapterMode: "OAUTH_SCAFFOLD", description: "", activationRequirement: "", defaultScopes: [], stub: false, connection: null },
			setting: null,
			mappings: [],
			recentBatches: [],
			activationReadiness: { stage: "NOT_CONFIGURED", activationAllowed: false, credentialedProviderVerified: false, providerWritesEnabled: false, gates: [] },
			providerWritesEnabled: false,
			accountingReviewRequired: false,
		});
	}
	if (url.includes("/integrations/sports-data")) {
		return response({ providers: [], recentRuns: [], directProviderImportEnabled: false });
	}
	return response([]);
}

describe("IntegrationsPanel", () => {
	afterEach(() => {
		vi.unstubAllGlobals();
	});

	it("shows backend readiness without exposing platform credentials", async () => {
		vi.stubGlobal("fetch", vi.fn((input: RequestInfo | URL) => {
			const url = String(input);
			if (url.includes("/integrations/catalog")) {
				return response([
					{ provider: "QUICKBOOKS_ONLINE", displayName: "QuickBooks Online", category: "ACCOUNTING", ownerType: "ORGANIZATION", authMode: "OAUTH2", supportedAuthModes: ["OAUTH2"], readiness: "NOT_CONFIGURED", adapterMode: "OAUTH_SCAFFOLD", description: "Accounting connection", activationRequirement: "Requires verified credentials.", defaultScopes: [], stub: false, connection: null },
					{ provider: "TEAMSNAP", displayName: "TeamSnap", category: "SPORTS_DATA", ownerType: "ORGANIZATION", authMode: "FILE_IMPORT", supportedAuthModes: ["FILE_IMPORT"], readiness: "PARTNER_PENDING", adapterMode: "PARTNER_PENDING", description: "Partner pending", activationRequirement: "Requires official access.", defaultScopes: [], stub: false, connection: null },
				]);
			}
			return defaultFetchStub(url);
		}));

		renderWithProviders(<IntegrationsPanel organizationId="org-1" />);

		expect(screen.getByText("Rally26-managed providers")).toBeInTheDocument();
		await waitFor(() => expect(screen.getByText("QuickBooks Online")).toBeInTheDocument());
		expect(screen.getByText("Not configured")).toBeInTheDocument();
		expect(screen.getByText("Partner access required")).toBeInTheDocument();
		expect(screen.queryByText("Stripe")).not.toBeInTheDocument();
	});

	it("lists connected ICS feeds returned by the API", async () => {
		vi.stubGlobal("fetch", vi.fn((input: RequestInfo | URL) => {
			const url = String(input);
			if (url.includes("/event-source-connections")) {
				return response([{ id: "conn-1", provider: "ICS_FEED", label: "Varsity Soccer Schedule", feedUrl: "https://example.com/feed.ics", timezone: "America/New_York", teamId: null, status: "ACTIVE", lastSyncedAt: null, lastSyncStatus: null, lastSyncError: null, createdAt: new Date().toISOString() }]);
			}
			return defaultFetchStub(url);
		}));

		renderWithProviders(<IntegrationsPanel organizationId="org-1" />);

		await waitFor(() => expect(screen.getByText("Varsity Soccer Schedule")).toBeInTheDocument());
		expect(screen.getByText("Not yet synced")).toBeInTheDocument();
		expect(screen.getByRole("button", { name: /disconnect/i })).toBeInTheDocument();
	});
});
