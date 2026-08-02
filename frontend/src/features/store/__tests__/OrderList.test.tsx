import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { renderWithProviders } from "../../../test/testUtils";
import { OrderList } from "../OrderList";
import type { Order, OrderPage } from "../types";

const organizationId = "11111111-1111-1111-1111-111111111111";
const storeId = "22222222-2222-2222-2222-222222222222";

function jsonResponse(body: unknown, status = 200) {
	return new Response(JSON.stringify(body), { status, headers: { "content-type": "application/json" } });
}

const confirmedOrder: Order = {
	id: "33333333-3333-3333-3333-333333333333",
	storeId,
	status: "CONFIRMED", paymentSource: "STRIPE",
	currency: "USD",
	supporterName: "Jane Doe",
	supporterEmail: "jane@example.com",
	shippingAddress: null,
	confirmedAt: new Date().toISOString(),
	refundedAt: null,
	createdAt: new Date().toISOString(),
};

describe("OrderList", () => {
	afterEach(() => {
		vi.unstubAllGlobals();
	});

	it("shows an empty state when there are no confirmed orders", async () => {
		const emptyOrders: OrderPage = { items: [], page: 0, size: 20, totalElements: 0 };
		vi.stubGlobal("fetch", vi.fn().mockResolvedValue(jsonResponse(emptyOrders)));

		renderWithProviders(<OrderList organizationId={organizationId} storeId={storeId} />);

		expect(await screen.findByText(/no confirmed orders yet/i)).toBeInTheDocument();
	});

	it("shows a Refund button for a confirmed order and calls the refund endpoint", async () => {
		const fetchMock = vi.fn().mockImplementation((url: string, init?: RequestInit) => {
			if (init?.method === "POST" && url.includes("/refund")) {
				return Promise.resolve(jsonResponse({ ...confirmedOrder, status: "REFUNDED" }));
			}
			if (url.includes("/fulfillment")) return Promise.resolve(jsonResponse(null));
			return Promise.resolve(jsonResponse({ items: [confirmedOrder], page: 0, size: 20, totalElements: 1 }));
		});
		vi.stubGlobal("fetch", fetchMock);
		const user = userEvent.setup();

		renderWithProviders(<OrderList organizationId={organizationId} storeId={storeId} />);
		await screen.findByText("Jane Doe");

		await user.click(screen.getByRole("button", { name: /refund/i }));

		await waitFor(() =>
			expect(fetchMock).toHaveBeenCalledWith(
				expect.stringContaining(`/organizations/${organizationId}/orders/${confirmedOrder.id}/refund`),
				expect.objectContaining({ method: "POST" }),
			),
		);
	});

	it("does not show a Refund button for an already-refunded order", async () => {
		const refundedOrder: Order = { ...confirmedOrder, status: "REFUNDED", refundedAt: new Date().toISOString() };
		const fetchMock = vi.fn().mockImplementation((url: string) => {
			if (url.includes("/fulfillment")) return Promise.resolve(jsonResponse(null));
			return Promise.resolve(jsonResponse({ items: [refundedOrder], page: 0, size: 20, totalElements: 1 }));
		});
		vi.stubGlobal("fetch", fetchMock);

		renderWithProviders(<OrderList organizationId={organizationId} storeId={storeId} />);
		await screen.findByText("Jane Doe");

		expect(screen.getByText(/refunded/i)).toBeInTheDocument();
		expect(screen.queryByRole("button", { name: /refund/i })).not.toBeInTheDocument();
	});
});
