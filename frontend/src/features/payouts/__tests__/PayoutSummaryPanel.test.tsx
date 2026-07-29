import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { renderWithProviders } from "../../../test/testUtils";
import { PayoutSummaryPanel } from "../PayoutSummaryPanel";
import type { PayoutSummary } from "../types";

const organizationId = "11111111-1111-1111-1111-111111111111";

function jsonResponse(body: unknown, status = 200) {
	return new Response(JSON.stringify(body), { status, headers: { "content-type": "application/json" } });
}

const positiveSummary: PayoutSummary = { eligibleMinor: 9_500, heldMinor: 0, pendingDebitsMinor: 0, netAvailableMinor: 9_500 };
const negativeSummary: PayoutSummary = { eligibleMinor: 0, heldMinor: 0, pendingDebitsMinor: 9_500, netAvailableMinor: -9_500 };

describe("PayoutSummaryPanel", () => {
	afterEach(() => {
		vi.unstubAllGlobals();
	});

	it("shows the eligible/held/pending/net figures", async () => {
		vi.stubGlobal("fetch", vi.fn().mockResolvedValue(jsonResponse(positiveSummary)));

		renderWithProviders(<PayoutSummaryPanel organizationId={organizationId} />);

		expect(await screen.findAllByText("$95.00")).toHaveLength(2); // "Eligible now" and "Net available" both read $95.00
		expect(screen.getByRole("button", { name: /transfer now/i })).toBeEnabled();
	});

	it("disables the transfer button and explains a negative balance", async () => {
		vi.stubGlobal("fetch", vi.fn().mockResolvedValue(jsonResponse(negativeSummary)));

		renderWithProviders(<PayoutSummaryPanel organizationId={organizationId} />);

		await screen.findByText(/deducted from the next transfer/i);
		expect(screen.getByRole("button", { name: /transfer now/i })).toBeDisabled();
	});

	it("triggers a transfer and refreshes the summary", async () => {
		const fetchMock = vi.fn().mockImplementation((url: string, init?: RequestInit) => {
			if (init?.method === "POST" && url.includes("/transfer")) {
				return Promise.resolve(jsonResponse({ eligibleMinor: 0, heldMinor: 0, pendingDebitsMinor: 0, netAvailableMinor: 0 }));
			}
			return Promise.resolve(jsonResponse(positiveSummary));
		});
		vi.stubGlobal("fetch", fetchMock);
		const user = userEvent.setup();

		renderWithProviders(<PayoutSummaryPanel organizationId={organizationId} />);
		await screen.findAllByText("$95.00");

		await user.click(screen.getByRole("button", { name: /transfer now/i }));

		await waitFor(() =>
			expect(fetchMock).toHaveBeenCalledWith(
				expect.stringContaining(`/organizations/${organizationId}/payout-account/transfer`),
				expect.objectContaining({ method: "POST" }),
			),
		);
	});

	it("surfaces the backend error message when a transfer fails", async () => {
		const fetchMock = vi.fn().mockImplementation((url: string, init?: RequestInit) => {
			if (init?.method === "POST" && url.includes("/transfer")) {
				return Promise.resolve(
					jsonResponse(
						{ code: "PAYOUT_ACCOUNT_NOT_ENABLED", message: "This organization's connected account isn't yet enabled to receive payouts.", requestId: "req_1", fieldErrors: [] },
						422,
					),
				);
			}
			return Promise.resolve(jsonResponse(positiveSummary));
		});
		vi.stubGlobal("fetch", fetchMock);
		const user = userEvent.setup();

		renderWithProviders(<PayoutSummaryPanel organizationId={organizationId} />);
		await screen.findAllByText("$95.00");

		await user.click(screen.getByRole("button", { name: /transfer now/i }));

		expect(await screen.findByRole("alert")).toHaveTextContent(/isn't yet enabled to receive payouts/i);
	});
});
