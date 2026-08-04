import { screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { renderWithProviders } from "../../../test/testUtils";
import { IntegrationsPanel } from "../IntegrationsPanel";

function response(body: unknown) {
	return Promise.resolve(new Response(JSON.stringify(body), { status: 200, headers: { "content-type": "application/json" } }));
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
					{ provider: "GAMECHANGER", displayName: "GameChanger", category: "SPORTS_DATA", ownerType: "ORGANIZATION", authMode: "FILE_IMPORT", supportedAuthModes: ["FILE_IMPORT"], readiness: "PARTNER_PENDING", adapterMode: "PARTNER_PENDING", description: "Partner pending", activationRequirement: "Requires official access.", defaultScopes: [], stub: false, connection: null },
				]);
			}
			return response([]);
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
			return response([]);
		}));

		renderWithProviders(<IntegrationsPanel organizationId="org-1" />);

		await waitFor(() => expect(screen.getByText("Varsity Soccer Schedule")).toBeInTheDocument());
		expect(screen.getByText("Not yet synced")).toBeInTheDocument();
		expect(screen.getByRole("button", { name: /disconnect/i })).toBeInTheDocument();
	});
});
