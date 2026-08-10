import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { Route, Routes } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";
import { renderWithProviders } from "../../../test/testUtils";
import { PublicStoreView } from "../PublicStoreView";
import type { OrderStatusResult, PublicStore } from "../../../features/store/types";

const store: PublicStore = {
	id: "22222222-2222-2222-2222-222222222222",
	name: "Spring Store",
	slug: "spring-store",
	primaryColor: "#0B1F33",
	secondaryColor: "#20B26B",
	products: [
		{
			id: "33333333-3333-3333-3333-333333333333",
			name: "Team Hoodie",
			description: "Warm and cozy.",
			designUrl: null,
			variants: [
				{ id: "44444444-4444-4444-4444-444444444444", label: "M / Navy", priceMinor: 2500, currency: "USD" },
			],
		},
	],
};

function jsonResponse(body: unknown, status = 200) {
	return new Response(JSON.stringify(body), { status, headers: { "content-type": "application/json" } });
}

function renderAt(path: string) {
	return renderWithProviders(
		<Routes>
			<Route path="/stores/:slug" element={<PublicStoreView />} />
		</Routes>,
		{ route: path },
	);
}

describe("PublicStoreView", () => {
	afterEach(() => {
		vi.unstubAllGlobals();
	});

	it("renders a published store's products and variants", async () => {
		vi.stubGlobal("fetch", vi.fn().mockResolvedValue(jsonResponse(store)));

		renderAt("/stores/spring-store");

		expect(await screen.findByRole("heading", { level: 1, name: /spring store/i })).toBeInTheDocument();
		expect(screen.getByText(/team hoodie/i)).toBeInTheDocument();
		expect(screen.getByText(/M \/ Navy — \$25\.00/)).toBeInTheDocument();
	});

	it("shows an error state for an unpublished or unknown slug", async () => {
		vi.stubGlobal(
			"fetch",
			vi.fn().mockResolvedValue(jsonResponse({ code: "STORE_NOT_FOUND", message: "not found", requestId: "r", fieldErrors: [] }, 404)),
		);

		renderAt("/stores/unknown-slug");

		expect(await screen.findByText(/could not be found or is not currently open/i)).toBeInTheDocument();
	});

	it("adds a variant to the cart and redirects to Stripe Checkout", async () => {
		const originalLocation = window.location;
		Object.defineProperty(window, "location", { writable: true, value: { ...originalLocation, href: "" } });

		const fetchMock = vi.fn((url: string, init?: RequestInit) => {
			if (init?.method === "POST" && url.includes("/orders")) {
				return Promise.resolve(
					jsonResponse({ orderId: "55555555-5555-5555-5555-555555555555", checkoutUrl: "https://checkout.stripe.com/test-order" }),
				);
			}
			return Promise.resolve(jsonResponse(store));
		});
		vi.stubGlobal("fetch", fetchMock);
		const user = userEvent.setup();

		renderAt("/stores/spring-store");
		await screen.findByRole("heading", { level: 1, name: /spring store/i });

		const quantityInput = screen.getByLabelText(/quantity for m \/ navy/i);
		await user.clear(quantityInput);
		await user.type(quantityInput, "2");

		await user.click(await screen.findByRole("button", { name: /checkout \(2 items\)/i }));

		await waitFor(() =>
			expect(fetchMock).toHaveBeenCalledWith(
				expect.stringContaining("/public/stores/spring-store/orders"),
				expect.objectContaining({ method: "POST" }),
			),
		);
		const call = fetchMock.mock.calls.find(([, init]) => (init as RequestInit | undefined)?.method === "POST");
		const postInit = call?.[1] as RequestInit;
		const body = JSON.parse(postInit.body as string);
		expect(body.items).toEqual([{ productVariantId: "44444444-4444-4444-4444-444444444444", quantity: 2 }]);
		await waitFor(() => expect(window.location.href).toBe("https://checkout.stripe.com/test-order"));

		Object.defineProperty(window, "location", { writable: true, value: originalLocation });
	});

	it("shows a confirmation panel once the webhook has confirmed the order", async () => {
		const status: OrderStatusResult = {
			id: "55555555-5555-5555-5555-555555555555",
			status: "CONFIRMED",
			currency: "USD",
			confirmedAt: new Date().toISOString(),
		};
		vi.stubGlobal(
			"fetch",
			vi.fn((url: string) => {
				if (url.includes("/orders/")) return Promise.resolve(jsonResponse(status));
				return Promise.resolve(jsonResponse(store));
			}),
		);

		renderAt("/stores/spring-store?orderId=55555555-5555-5555-5555-555555555555");

		expect(await screen.findByText(/thank you for your order/i)).toBeInTheDocument();
	});
});
