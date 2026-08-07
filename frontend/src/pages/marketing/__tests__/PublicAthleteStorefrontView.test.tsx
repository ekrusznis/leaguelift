import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { AthleteStorefrontPublic } from "../../../features/store/athleteStorefrontApi";
import { PublicAthleteStorefrontView } from "../PublicAthleteStorefrontView";

const slug = "maya-johnson";
const productId = "33333333-3333-3333-3333-333333333333";
const variantId = "44444444-4444-4444-4444-444444444444";

function jsonResponse(body: unknown, status = 200) {
	return new Response(JSON.stringify(body), { status, headers: { "content-type": "application/json" } });
}

function renderView() {
	const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
	return render(
		<QueryClientProvider client={queryClient}>
			<MemoryRouter initialEntries={[`/swag-shop/athlete/${slug}`]}>
				<Routes>
					<Route path="/swag-shop/athlete/:slug" element={<PublicAthleteStorefrontView />} />
				</Routes>
			</MemoryRouter>
		</QueryClientProvider>,
	);
}

const storefront: AthleteStorefrontPublic = {
	slug,
	organizationName: "Riverside Soccer",
	teamName: "Riverside U10",
	athletePublicLabel: "Maya J.",
	products: [
		{
			id: productId,
			name: "Youth Hoodie",
			description: null,
			hasSwagLogo: true,
			logoPreviewUrl: "https://signed.example.com/logo.png",
			variants: [
				{
					id: variantId,
					label: "Youth M",
					priceMinor: 2500,
					currency: "USD",
					printAreaWidthPx: 1000,
					printAreaHeightPx: 1000,
					backPrintAreaWidthPx: 800,
					backPrintAreaHeightPx: 900,
					mockupFrontUrl: "https://signed.example.com/mockup-front.png",
					mockupBackUrl: "https://signed.example.com/mockup-back.png",
				},
			],
		},
	],
};

function stubFetch(): ReturnType<typeof vi.fn> {
	const fetchMock = vi.fn().mockImplementation((url: string) => {
		if (url.includes(`/public/athlete-storefronts/${slug}/orders`)) {
			return Promise.resolve(jsonResponse({ orderId: "55555555-5555-5555-5555-555555555555", checkoutUrl: "https://checkout.stripe.com/test" }));
		}
		if (url.includes(`/public/athlete-storefronts/${slug}`)) return Promise.resolve(jsonResponse(storefront));
		return Promise.resolve(jsonResponse(null));
	});
	vi.stubGlobal("fetch", fetchMock);
	return fetchMock;
}

describe("PublicAthleteStorefrontView", () => {
	afterEach(() => {
		vi.unstubAllGlobals();
	});

	it("shows the athlete's public label and never the real last name", async () => {
		stubFetch();
		renderView();

		expect(await screen.findByText(/maya j\./i)).toBeInTheDocument();
		expect(screen.queryByText(/johnson/i)).not.toBeInTheDocument();
	});

	it("submits the chosen item, personalization, and supporter details to public checkout", async () => {
		const fetchMock = stubFetch();
		const user = userEvent.setup();
		renderView();

		await user.selectOptions(await screen.findByLabelText(/item/i), productId);
		await user.selectOptions(await screen.findByLabelText(/size \/ color/i), variantId);
		await user.click(screen.getByLabelText(/add name and\/or number/i));
		await user.type(screen.getByLabelText(/^name$/i), "Johnson");
		await user.type(screen.getByLabelText(/^number$/i), "7");
		await user.selectOptions(screen.getByLabelText(/placement/i), "CENTER_FRONT");
		await user.type(screen.getByLabelText(/your name/i), "Grandma Sue");
		await user.type(screen.getByLabelText(/email for your receipt/i), "sue@example.com");
		await user.click(screen.getByRole("button", { name: /^order$/i }));

		await waitFor(() => {
			const orderCall = fetchMock.mock.calls.find((call: unknown[]) => (call[0] as string).includes(`/public/athlete-storefronts/${slug}/orders`));
			expect(orderCall).toBeDefined();
			const body = JSON.parse((orderCall![1] as { body: string }).body);
			expect(body.productVariantId).toBe(variantId);
			expect(body.personalizationName).toBe("Johnson");
			expect(body.personalizationNumber).toBe("7");
			expect(body.personalizationPlacement).toBe("CENTER_FRONT");
			expect(body.supporterName).toBe("Grandma Sue");
			expect(body.supporterEmail).toBe("sue@example.com");
		});
	});

	it("shows an error state for an unpublished or unknown slug", async () => {
		vi.stubGlobal("fetch", vi.fn().mockImplementation(() => Promise.resolve(jsonResponse({ code: "ATHLETE_STOREFRONT_NOT_FOUND", message: "not found" }, 404))));

		renderView();

		expect(await screen.findByText(/could not be found|not currently open/i)).toBeInTheDocument();
	});
});
