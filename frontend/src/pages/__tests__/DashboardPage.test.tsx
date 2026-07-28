import { screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { renderWithProviders } from "../../test/testUtils";
import { DashboardPage } from "../DashboardPage";

/**
 * Only `/me/dashboard-context` is mocked per test — every other dashboard-card
 * endpoint intentionally 404s so each card renders its own isolated error state
 * rather than crashing the page (DESIGN-DOC.md section 10.1/10.2: routing is real,
 * per-card data loads independently). These tests exercise the routing decision, not
 * every card's content.
 */
function mockDashboardContext(role: string, extra: Record<string, unknown> = {}) {
	vi.stubGlobal(
		"fetch",
		vi.fn().mockImplementation((url: string) => {
			if (url.includes("/me/dashboard-context")) {
				return Promise.resolve(
					new Response(JSON.stringify({ role, organizationId: null, householdId: null, ...extra }), {
						status: 200,
						headers: { "content-type": "application/json" },
					}),
				);
			}
			return Promise.resolve(
				new Response(JSON.stringify({ code: "NOT_FOUND", message: "not mocked", requestId: "req_1", fieldErrors: [] }), {
					status: 404,
					headers: { "content-type": "application/json" },
				}),
			);
		}),
	);
}

describe("DashboardPage", () => {
	afterEach(() => {
		vi.unstubAllGlobals();
	});

	it("routes to the Owner dashboard", async () => {
		mockDashboardContext("OWNER", { organizationId: "org-1" });
		renderWithProviders(<DashboardPage />);

		expect(await screen.findByRole("heading", { name: /organization overview/i })).toBeInTheDocument();
	});

	it("routes to the Coach dashboard", async () => {
		mockDashboardContext("COACH", { organizationId: "org-1" });
		renderWithProviders(<DashboardPage />);

		expect(await screen.findByRole("heading", { name: /team overview/i })).toBeInTheDocument();
	});

	it("routes to the Parent dashboard", async () => {
		mockDashboardContext("PARENT", { organizationId: "org-1", householdId: "household-1" });
		renderWithProviders(<DashboardPage />);

		expect(await screen.findByRole("heading", { name: /family overview/i })).toBeInTheDocument();
	});

	it("routes to the Athlete dashboard", async () => {
		mockDashboardContext("ATHLETE");
		renderWithProviders(<DashboardPage />);

		expect(await screen.findByRole("heading", { name: /welcome/i })).toBeInTheDocument();
	});

	it("shows an unauthorized state when Owner context is missing an organizationId", async () => {
		mockDashboardContext("OWNER", { organizationId: null });
		renderWithProviders(<DashboardPage />);

		expect(await screen.findByText(/no organization found/i)).toBeInTheDocument();
	});
});
