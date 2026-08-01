import { screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { renderWithProviders } from "../../../test/testUtils";
import { ActionCenterPage } from "../ActionCenterPage";

function jsonResponse(body: unknown) {
	return new Response(JSON.stringify(body), { status: 200, headers: { "content-type": "application/json" } });
}

describe("ActionCenterPage", () => {
	afterEach(() => vi.unstubAllGlobals());

	it("renders a role-aware action with its real destination", async () => {
		vi.stubGlobal("fetch", vi.fn().mockResolvedValue(jsonResponse({
			items: [{
				id: "fee:1",
				type: "FEE_PAYMENT",
				priority: "URGENT",
				title: "Overdue payment",
				description: "$50.00 remaining for club dues.",
				actionPath: "/app/organizations/org-1/households/hh-1/fees",
				organizationId: "org-1",
				contextType: "HOUSEHOLD",
				contextId: "hh-1",
				dueAt: "2026-07-31T00:00:00Z",
				createdAt: "2026-07-01T00:00:00Z",
			}],
			totalCount: 1,
			highPriorityCount: 1,
		})));

		renderWithProviders(<ActionCenterPage />, { route: "/app/action-center" });

		expect(await screen.findByRole("heading", { name: "Overdue payment" })).toBeInTheDocument();
		expect(screen.getByRole("link", { name: "Open" })).toHaveAttribute("href", "/app/organizations/org-1/households/hh-1/fees");
	});
});
