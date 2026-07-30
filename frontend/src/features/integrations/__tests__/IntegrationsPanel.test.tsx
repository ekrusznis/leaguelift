import { screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { renderWithProviders } from "../../../test/testUtils";
import { IntegrationsPanel } from "../IntegrationsPanel";

describe("IntegrationsPanel", () => {
	afterEach(() => {
		vi.unstubAllGlobals();
	});

	it("shows platform-operated connectors and coming-soon connectors", async () => {
		vi.stubGlobal(
			"fetch",
			vi.fn().mockResolvedValue(new Response(JSON.stringify([]), { status: 200, headers: { "content-type": "application/json" } })),
		);

		renderWithProviders(<IntegrationsPanel organizationId="org-1" />);

		expect(screen.getByText("Stripe")).toBeInTheDocument();
		expect(screen.getByText("MaxPreps")).toBeInTheDocument();
		expect(screen.getAllByText("Coming soon").length).toBeGreaterThan(0);
	});

	it("lists connected ICS feeds returned by the API", async () => {
		vi.stubGlobal(
			"fetch",
			vi.fn().mockResolvedValue(
				new Response(
					JSON.stringify([
						{
							id: "conn-1",
							provider: "ICS_FEED",
							label: "Varsity Soccer Schedule",
							feedUrl: "https://example.com/feed.ics",
							status: "ACTIVE",
							lastSyncedAt: null,
							lastSyncStatus: null,
							lastSyncError: null,
							createdAt: new Date().toISOString(),
						},
					]),
					{ status: 200, headers: { "content-type": "application/json" } },
				),
			),
		);

		renderWithProviders(<IntegrationsPanel organizationId="org-1" />);

		await waitFor(() => expect(screen.getByText("Varsity Soccer Schedule")).toBeInTheDocument());
		expect(screen.getByText("Not yet synced")).toBeInTheDocument();
		expect(screen.getByRole("button", { name: /disconnect/i })).toBeInTheDocument();
	});
});
