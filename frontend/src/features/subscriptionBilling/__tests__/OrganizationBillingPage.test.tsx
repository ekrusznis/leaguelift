import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { Route, Routes } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { renderWithProviders } from "../../../test/testUtils";
import { OrganizationBillingPage } from "../OrganizationBillingPage";

const ORG_ID = "11111111-1111-1111-1111-111111111111";

function jsonResponse(body: unknown) {
	return {
		status: 200,
		ok: true,
		headers: { get: (name: string) => (name === "content-type" ? "application/json" : null) },
		json: async () => body,
	} as unknown as Response;
}

function subscription(overrides: Partial<Record<string, unknown>> = {}) {
	return {
		id: "sub-1",
		organizationId: ORG_ID,
		planCode: "STARTER",
		planName: "Starter",
		amountMinor: 4900,
		currency: "USD",
		billingInterval: "MONTHLY",
		status: "ACTIVE",
		recoveryState: "CURRENT",
		lastPaymentFailureAt: null,
		lastPaymentSuccessAt: null,
		billingPortalAvailable: true,
		cancelAtPeriodEnd: false,
		downgradeToPlanCode: null,
		currentPeriodEnd: null,
		...overrides,
	};
}

const PLANS = [
	{ code: "FREE", name: "Free", description: "For a single team.", amountMinor: 0, currency: "USD", billingInterval: null },
	{ code: "STARTER", name: "Starter", description: "For a small club.", amountMinor: 4900, currency: "USD", billingInterval: "MONTHLY" },
	{ code: "FOUNDING_CLUB", name: "Club", description: "For growing clubs.", amountMinor: 14900, currency: "USD", billingInterval: "MONTHLY" },
];

function renderPage() {
	return renderWithProviders(
		<Routes>
			<Route path="/app/organizations/:organizationId/billing" element={<OrganizationBillingPage />} />
		</Routes>,
		{ route: `/app/organizations/${ORG_ID}/billing` },
	);
}

describe("OrganizationBillingPage plan change", () => {
	beforeEach(() => {
		vi.stubGlobal("fetch", vi.fn());
	});

	afterEach(() => {
		vi.unstubAllGlobals();
	});

	it("renders a downgrade violation with its action link and a support link instead of applying it", async () => {
		vi.mocked(fetch).mockImplementation(async (input, init) => {
			const url = String(input);
			if (url.includes("/subscription/plans")) return jsonResponse(PLANS);
			if (url.includes("/subscription/plan-change/preview")) {
				return jsonResponse({
					currentPlanCode: "STARTER",
					targetPlanCode: "FREE",
					direction: "DOWNGRADE",
					violations: [
						{
							code: "TEAM_COUNT_OVER_LIMIT",
							message: "You have 3 teams, but this plan allows up to 1. Remove teams first, or contact support for help.",
							actionLink: `/app/organizations/${ORG_ID}/teams`,
						},
					],
				});
			}
			if (url.includes("/subscription/plan-change/apply")) throw new Error("apply should not be called when blocked");
			if (url.endsWith(`/organizations/${ORG_ID}/subscription`)) return jsonResponse(subscription());
			throw new Error(`Unexpected fetch: ${String(init?.method ?? "GET")} ${url}`);
		});
		const user = userEvent.setup();

		renderPage();

		await user.click(await screen.findByRole("button", { name: "Switch to Free" }));

		expect(await screen.findByText(/this plan can.t be applied yet/i)).toBeInTheDocument();
		expect(screen.getByText(/you have 3 teams/i)).toBeInTheDocument();
		expect(screen.getByRole("link", { name: "Go fix this" })).toHaveAttribute("href", `/app/organizations/${ORG_ID}/teams`);
		expect(screen.getByRole("link", { name: "Contact support" })).toHaveAttribute("href", "/app/help/support");
		expect(screen.queryByRole("button", { name: "Confirm" })).not.toBeInTheDocument();
	});

	it("applies a clean paid-to-paid plan change and shows a success banner", async () => {
		vi.mocked(fetch).mockImplementation(async (input) => {
			const url = String(input);
			if (url.includes("/subscription/plans")) return jsonResponse(PLANS);
			if (url.includes("/subscription/plan-change/preview")) {
				return jsonResponse({ currentPlanCode: "STARTER", targetPlanCode: "FOUNDING_CLUB", direction: "UPGRADE", violations: [] });
			}
			if (url.includes("/subscription/plan-change/apply")) {
				return jsonResponse({ outcome: "APPLIED", violations: null, checkoutUrl: null, effectiveAt: null });
			}
			if (url.endsWith(`/organizations/${ORG_ID}/subscription`)) return jsonResponse(subscription());
			throw new Error(`Unexpected fetch: ${url}`);
		});
		const user = userEvent.setup();

		renderPage();

		await user.click(await screen.findByRole("button", { name: "Switch to Club" }));
		await user.click(await screen.findByRole("button", { name: "Confirm" }));

		await waitFor(() => expect(screen.getByText(/plan changed successfully/i)).toBeInTheDocument());
	});
});
