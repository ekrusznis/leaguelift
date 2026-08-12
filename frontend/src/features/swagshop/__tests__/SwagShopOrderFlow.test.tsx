import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";
import { AuthProvider } from "../../../auth/AuthContext";
import type { AuthorizationContext } from "../../../authorization/types";
import type { Participant } from "../../households/types";
import type { SwagShopApparelType, SwagShopOrderHistoryItem } from "../../store/types";
import { SwagShopOrderFlow } from "../SwagShopOrderFlow";

const organizationId = "11111111-1111-1111-1111-111111111111";
const householdId = "22222222-2222-2222-2222-222222222222";
const productId = "33333333-3333-3333-3333-333333333333";
const variantId = "44444444-4444-4444-4444-444444444444";
const participantId = "55555555-5555-5555-5555-555555555555";

function jsonResponse(body: unknown, status = 200) {
	return new Response(JSON.stringify(body), { status, headers: { "content-type": "application/json" } });
}

function renderFlow() {
	const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
	return render(
		<QueryClientProvider client={queryClient}>
			<AuthProvider>
				<MemoryRouter initialEntries={[`/app/organizations/${organizationId}/swag-shop/order`]}>
					<Routes>
						<Route path="/app/organizations/:organizationId/swag-shop/order" element={<SwagShopOrderFlow />} />
					</Routes>
				</MemoryRouter>
			</AuthProvider>
		</QueryClientProvider>,
	);
}

const householdContext: AuthorizationContext = {
	contextType: "HOUSEHOLD",
	resourceId: householdId,
	organizationId,
	label: "My Household",
	role: "GUARDIAN",
	capabilities: ["household.order.create"],
};

const apparelType: SwagShopApparelType = {
	storeId: "66666666-6666-6666-6666-666666666666",
	storeName: "Team Store",
	productId,
	productName: "Team Tee",
	description: null,
	hasSwagLogo: true,
	logoPreviewUrl: "https://signed.example.com/logo.png",
	variants: [
		{
			id: variantId,
			productId,
			catalogSource: "PRINTIFY",
			label: "M / Navy",
			sku: null,
			size: "M",
			color: "Navy",
			printifyPrintProviderId: 5,
			printifyVariantId: 100,
			currency: "USD",
			costMinor: 1200,
			priceMinor: 2500,
			isActive: true,
			printAreaWidthPx: 1000,
			printAreaHeightPx: 1000,
			backPrintAreaWidthPx: 800,
			backPrintAreaHeightPx: 900,
			mockupFrontUrl: "https://signed.example.com/mockup-front.png",
			mockupBackUrl: "https://signed.example.com/mockup-back.png",
		},
	],
};

const participant: Participant = {
	id: participantId,
	householdId,
	organizationId,
	firstName: "Maya",
	lastName: "Johnson",
	dateOfBirth: null,
	notes: null,
	status: "ACTIVE",
	createdAt: new Date().toISOString(),
	updatedAt: new Date().toISOString(),
};

const pastOrder: SwagShopOrderHistoryItem = {
	orderId: "88888888-8888-8888-8888-888888888888",
	confirmedAt: new Date("2026-06-01T00:00:00Z").toISOString(),
	participantId,
	participantName: "Maya Johnson",
	productId,
	productName: "Team Tee",
	variantId,
	variantLabel: "M / Navy",
	size: "M",
	color: "Navy",
	mockupFrontUrl: "https://signed.example.com/mockup-front.png",
	personalizationName: "Johnson",
	personalizationNumber: "7",
	personalizationPlacement: "BACK",
	personalizationLogoSize: "LARGE",
	unitPriceMinor: 2500,
	currency: "USD",
	isReorderable: true,
};

function stubFetch(pastOrders: SwagShopOrderHistoryItem[] = []): ReturnType<typeof vi.fn> {
	const fetchMock = vi.fn().mockImplementation((url: string) => {
		if (url.includes("/me/contexts")) return Promise.resolve(jsonResponse([householdContext]));
		if (url.includes("/swag-shop/my-orders")) return Promise.resolve(jsonResponse(pastOrders));
		if (url.includes("/swag-shop/apparel-types")) return Promise.resolve(jsonResponse([apparelType]));
		if (url.includes("/participants")) return Promise.resolve(jsonResponse([participant]));
		if (url.includes("/swag-shop/orders")) {
			return Promise.resolve(jsonResponse({ orderId: "77777777-7777-7777-7777-777777777777", checkoutUrl: "https://checkout.stripe.com/test" }));
		}
		return Promise.resolve(jsonResponse(null));
	});
	vi.stubGlobal("fetch", fetchMock);
	return fetchMock;
}

describe("SwagShopOrderFlow", () => {
	afterEach(() => {
		vi.unstubAllGlobals();
	});

	it("offers CENTER_FRONT placement and a small/standard/large logo size choice", async () => {
		stubFetch();
		const user = userEvent.setup();
		renderFlow();

		await user.selectOptions(await screen.findByLabelText(/apparel type/i), productId);
		await user.selectOptions(await screen.findByLabelText(/size \/ color/i), variantId);
		await user.selectOptions(await screen.findByLabelText(/athlete/i), participantId);
		await user.click(screen.getByLabelText(/add name and\/or number/i));

		const placementSelect = screen.getByLabelText(/placement/i) as HTMLSelectElement;
		const placementValues = Array.from(placementSelect.options).map((o) => o.value);
		expect(placementValues).toEqual(["LEFT_CHEST", "RIGHT_CHEST", "CENTER_FRONT", "BACK"]);

		const logoSizeSelect = screen.getByLabelText(/logo size/i) as HTMLSelectElement;
		const logoSizeValues = Array.from(logoSizeSelect.options).map((o) => o.value);
		expect(logoSizeValues).toEqual(["SMALL", "STANDARD", "LARGE"]);
	});

	it("submits the chosen placement and logo size with the order", async () => {
		const fetchMock = stubFetch();
		const user = userEvent.setup();
		renderFlow();

		await user.selectOptions(await screen.findByLabelText(/apparel type/i), productId);
		await user.selectOptions(await screen.findByLabelText(/size \/ color/i), variantId);
		await user.selectOptions(await screen.findByLabelText(/athlete/i), participantId);
		await user.click(screen.getByLabelText(/add name and\/or number/i));
		await user.type(screen.getByLabelText(/^name$/i), "Johnson");
		await user.type(screen.getByLabelText(/^number$/i), "7");
		await user.selectOptions(screen.getByLabelText(/placement/i), "CENTER_FRONT");
		await user.selectOptions(screen.getByLabelText(/logo size/i), "LARGE");
		await user.click(screen.getByRole("button", { name: /order/i }));

		await waitFor(() => {
			const orderCall = fetchMock.mock.calls.find((call: unknown[]) => (call[0] as string).includes("/swag-shop/orders"));
			expect(orderCall).toBeDefined();
			const body = JSON.parse((orderCall![1] as { body: string }).body);
			expect(body.personalizationPlacement).toBe("CENTER_FRONT");
			expect(body.personalizationLogoSize).toBe("LARGE");
		});
	});

	it("shows an empty state when there are no past orders", async () => {
		stubFetch([]);
		renderFlow();

		expect(await screen.findByText(/no past orders yet/i)).toBeInTheDocument();
	});

	it("lists a past order and prefills the form on Reorder", async () => {
		stubFetch([pastOrder]);
		const user = userEvent.setup();
		renderFlow();

		expect(await screen.findByText(/team tee — m \/ navy/i)).toBeInTheDocument();
		expect(screen.getByText(/for maya johnson/i)).toBeInTheDocument();

		await user.click(screen.getByRole("button", { name: /reorder/i }));

		expect(await screen.findByLabelText(/size \/ color/i)).toHaveValue(variantId);
		expect(screen.getByLabelText(/athlete/i)).toHaveValue(participantId);
		expect(screen.getByLabelText(/add name and\/or number/i)).toBeChecked();
		expect(screen.getByLabelText(/^name$/i)).toHaveValue("Johnson");
		expect(screen.getByLabelText(/^number$/i)).toHaveValue("7");
	});

	it("disables Reorder and labels it unavailable when the item is no longer reorderable", async () => {
		stubFetch([{ ...pastOrder, isReorderable: false }]);
		renderFlow();

		const button = await screen.findByRole("button", { name: /no longer available/i });
		expect(button).toBeDisabled();
	});
});
