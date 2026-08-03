import { screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { renderWithProviders } from "../../../test/testUtils";
import { PersonalIntegrationsPage } from "../PersonalIntegrationsPage";

function response(body: unknown) {
	return Promise.resolve(new Response(JSON.stringify(body), { status: 200, headers: { "content-type": "application/json" } }));
}

describe("PersonalIntegrationsPage", () => {
	afterEach(() => vi.unstubAllGlobals());

	it("shows fail-closed Google readiness and keeps ICS available", async () => {
		vi.stubGlobal("fetch", vi.fn(() => response({
			catalog: {
				provider: "GOOGLE_CALENDAR",
				displayName: "Google Calendar",
				category: "CALENDAR",
				ownerType: "USER",
				authMode: "OAUTH2",
				supportedAuthModes: ["OAUTH2"],
				readiness: "NOT_CONFIGURED",
				adapterMode: "OAUTH_SCAFFOLD",
				description: "Personal Google Calendar connection.",
				activationRequirement: "Requires a verified Google OAuth application.",
				defaultScopes: [],
				stub: false,
				connection: null,
			},
			setting: null,
			mappingCount: 0,
			icsFallbackAvailable: true,
			automaticSyncAvailable: false,
		})));

		renderWithProviders(<PersonalIntegrationsPage />, { route: "/app/integrations" });

		await waitFor(() => expect(screen.getByRole("heading", { name: "Google Calendar" })).toBeInTheDocument());
		expect(screen.getByText("Not configured")).toBeInTheDocument();
		expect(screen.getByRole("button", { name: "Connect Google Calendar" })).toBeDisabled();
		expect(screen.getByRole("heading", { name: "ICS always remains available" })).toBeInTheDocument();
		expect(screen.getByText(/does not request Google credentials/i)).toBeInTheDocument();
	});
});
