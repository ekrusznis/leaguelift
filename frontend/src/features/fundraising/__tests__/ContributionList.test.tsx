import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
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

	it("shows a Refund button for a confirmed contribution and calls the refund endpoint", async () => {
		const fetchMock = vi.fn().mockImplementation((url: string, init?: RequestInit) => {
			if (init?.method === "POST" && url.includes("/refund")) {
				return Promise.resolve(jsonResponse({ ...confirmedContribution, status: "REFUNDED" }));
			}
			return Promise.resolve(jsonResponse({ items: [confirmedContribution], page: 0, size: 20, totalElements: 1 }));
		});
		vi.stubGlobal("fetch", fetchMock);
		const user = userEvent.setup();

		renderWithProviders(<ContributionList organizationId={organizationId} campaignId={campaignId} />);
		await screen.findByText("Jane Doe");

		await user.click(screen.getByRole("button", { name: /refund/i }));

		await waitFor(() =>
			expect(fetchMock).toHaveBeenCalledWith(
				expect.stringContaining(`/organizations/${organizationId}/campaigns/${campaignId}/contributions/${confirmedContribution.id}/refund`),
				expect.objectContaining({ method: "POST" }),
			),
		);
	});

	it("does not show a Refund button for an already-refunded contribution", async () => {
		const refunded: Contribution = { ...confirmedContribution, status: "REFUNDED", refundedAt: new Date().toISOString() };
		vi.stubGlobal("fetch", vi.fn().mockResolvedValue(jsonResponse({ items: [refunded], page: 0, size: 20, totalElements: 1 })));

		renderWithProviders(<ContributionList organizationId={organizationId} campaignId={campaignId} />);
		await screen.findByText("Jane Doe");

		expect(screen.getByText(/refunded/i)).toBeInTheDocument();
		expect(screen.queryByRole("button", { name: /refund/i })).not.toBeInTheDocument();
	});
});
