import { screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { renderWithProviders } from "../../../test/testUtils";
import { ContributionList } from "../ContributionList";
import type { Contribution, ContributionPage } from "../types";

const organizationId = "11111111-1111-1111-1111-111111111111";
const campaignId = "22222222-2222-2222-2222-222222222222";

function jsonResponse(body: unknown, status = 200) {
	return new Response(JSON.stringify(body), { status, headers: { "content-type": "application/json" } });
}

const confirmedContribution: Contribution = {
	id: "33333333-3333-3333-3333-333333333333",
	status: "CONFIRMED", paymentSource: "STRIPE",
	amountMinor: 10_000,
	currency: "USD",
	supporterName: "Jane Doe",
	isAnonymous: false,
	supporterEmail: "jane@example.com",
	confirmedAt: new Date().toISOString(),
	refundedAt: null,
	createdAt: new Date().toISOString(),
};

describe("ContributionList", () => {
	afterEach(() => {
		vi.unstubAllGlobals();
	});

	it("shows an empty state when there are no contributions", async () => {
		const emptyContributions: ContributionPage = { items: [], page: 0, size: 20, totalElements: 0 };
		vi.stubGlobal("fetch", vi.fn().mockResolvedValue(jsonResponse(emptyContributions)));

		renderWithProviders(<ContributionList organizationId={organizationId} campaignId={campaignId} />);

		expect(await screen.findByText(/no contributions yet/i)).toBeInTheDocument();
	});

	it("links a confirmed Stripe contribution to the controlled refund preview", async () => {
		vi.stubGlobal("fetch", vi.fn().mockResolvedValue(jsonResponse({ items: [confirmedContribution], page: 0, size: 20, totalElements: 1 })));

		renderWithProviders(<ContributionList organizationId={organizationId} campaignId={campaignId} />);
		await screen.findByText("Jane Doe");

		const link = screen.getByRole("link", { name: /preview refund/i });
		expect(link).toHaveAttribute(
			"href",
			`/app/organizations/${organizationId}/financial-operations?targetType=CONTRIBUTION&targetId=${confirmedContribution.id}`,
		);
	});

	it("does not show a refund-preview link for an already-refunded contribution", async () => {
		const refunded: Contribution = { ...confirmedContribution, status: "REFUNDED", refundedAt: new Date().toISOString() };
		vi.stubGlobal("fetch", vi.fn().mockResolvedValue(jsonResponse({ items: [refunded], page: 0, size: 20, totalElements: 1 })));

		renderWithProviders(<ContributionList organizationId={organizationId} campaignId={campaignId} />);
		await screen.findByText("Jane Doe");

		expect(screen.getByText(/refunded/i)).toBeInTheDocument();
		expect(screen.queryByRole("link", { name: /preview refund/i })).not.toBeInTheDocument();
	});
});
