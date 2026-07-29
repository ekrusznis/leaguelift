import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { Route, Routes } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";
import { renderWithProviders } from "../../../test/testUtils";
import { PublicSponsorshipView } from "../PublicSponsorshipView";
import type { PublicSponsorshipPackage, SponsorDirectoryEntry, SponsorshipStatusResult } from "../../../features/sponsorship/types";

const publishedPackage: PublicSponsorshipPackage = {
	id: "22222222-2222-2222-2222-222222222222",
	organizationId: "11111111-1111-1111-1111-111111111111",
	name: "Gold Sponsor",
	description: "Top-tier sponsorship",
	priceMinor: 50000,
	currency: "USD",
	maxQuantity: 5,
	exclusive: false,
	placementStartDate: null,
	placementEndDate: null,
	confirmedCount: 0,
	soldOut: false,
};

function jsonResponse(body: unknown, status = 200) {
	return new Response(JSON.stringify(body), { status, headers: { "content-type": "application/json" } });
}

/** Both the package list and the sponsor directory fetch in parallel once the org slug resolves — every mock below must answer both. */
function fetchRouter(handlers: { packages?: () => Response | Promise<Response>; directory?: () => Response | Promise<Response> } = {}) {
	return vi.fn((url: string) => {
		if (url.includes("/sponsorship-packages/")) {
			// status-poll call — not exercised by the default handlers, tests that need it override this branch.
			return Promise.resolve(jsonResponse({}));
		}
		if (url.includes("/sponsorship-packages")) {
			return Promise.resolve((handlers.packages ?? (() => jsonResponse([publishedPackage])))());
		}
		if (url.includes("/sponsors")) {
			return Promise.resolve((handlers.directory ?? (() => jsonResponse([])))());
		}
		return Promise.resolve(jsonResponse({}));
	});
}

function renderAt(path: string) {
	return renderWithProviders(
		<Routes>
			<Route path="/sponsors/:slug" element={<PublicSponsorshipView />} />
		</Routes>,
		{ route: path },
	);
}

describe("PublicSponsorshipView", () => {
	afterEach(() => {
		vi.unstubAllGlobals();
	});

	it("renders an organization's published sponsorship packages", async () => {
		vi.stubGlobal("fetch", fetchRouter());

		renderAt("/sponsors/riverside-fc");

		expect(await screen.findByText(/gold sponsor/i)).toBeInTheDocument();
		expect(screen.getByText(/top-tier sponsorship/i)).toBeInTheDocument();
		expect(screen.getByText("$500.00")).toBeInTheDocument();
	});

	it("shows an empty state when there are no published packages", async () => {
		vi.stubGlobal("fetch", fetchRouter({ packages: () => jsonResponse([]) }));

		renderAt("/sponsors/riverside-fc");

		expect(await screen.findByText(/no sponsorship packages are available/i)).toBeInTheDocument();
	});

	it("shows an error state for an unknown organization slug", async () => {
		vi.stubGlobal(
			"fetch",
			vi.fn().mockResolvedValue(
				jsonResponse({ code: "ORGANIZATION_NOT_FOUND", message: "not found", requestId: "r", fieldErrors: [] }, 404),
			),
		);

		renderAt("/sponsors/unknown-org");

		expect(await screen.findByText(/sponsorship packages could not be found/i)).toBeInTheDocument();
	});

	it("renders the sponsor directory when confirmed sponsors exist", async () => {
		const directory: SponsorDirectoryEntry[] = [
			{ sponsorId: "s1", sponsorName: "Acme Co", packageId: publishedPackage.id, packageName: "Gold Sponsor", logoUrl: null },
		];
		vi.stubGlobal("fetch", fetchRouter({ directory: () => jsonResponse(directory) }));

		renderAt("/sponsors/riverside-fc");

		expect(await screen.findByRole("heading", { name: /our sponsors/i })).toBeInTheDocument();
		expect(screen.getByText("Acme Co")).toBeInTheDocument();
	});

	it("starts checkout with the sponsor's name and redirects to Stripe", async () => {
		const originalLocation = window.location;
		Object.defineProperty(window, "location", { writable: true, value: { ...originalLocation, href: "" } });

		const fetchMock = vi.fn((url: string, init?: RequestInit) => {
			if (init?.method === "POST" && url.includes("/sponsorships")) {
				return Promise.resolve(
					jsonResponse({ sponsorshipId: "s-1", sponsorId: "sp-1", checkoutUrl: "https://checkout.stripe.com/test-sponsorship" }),
				);
			}
			if (url.includes("/sponsorship-packages")) return Promise.resolve(jsonResponse([publishedPackage]));
			if (url.includes("/sponsors")) return Promise.resolve(jsonResponse([]));
			return Promise.resolve(jsonResponse({}));
		});
		vi.stubGlobal("fetch", fetchMock);
		const user = userEvent.setup();

		renderAt("/sponsors/riverside-fc");
		await screen.findByText(/gold sponsor/i);

		await user.click(screen.getByRole("button", { name: /sponsor this/i }));
		await user.type(screen.getByLabelText(/your name \/ company/i), "Acme Co");
		await user.click(screen.getByRole("button", { name: /continue to checkout/i }));

		await waitFor(() =>
			expect(fetchMock).toHaveBeenCalledWith(
				expect.stringContaining(`/public/sponsorship-packages/${publishedPackage.id}/sponsorships`),
				expect.objectContaining({ method: "POST" }),
			),
		);
		await waitFor(() => expect(window.location.href).toBe("https://checkout.stripe.com/test-sponsorship"));

		Object.defineProperty(window, "location", { writable: true, value: originalLocation });
	});

	it("shows a confirmation panel once the webhook has confirmed the sponsorship", async () => {
		const status: SponsorshipStatusResult = {
			id: "s-1",
			status: "CONFIRMED",
			amountMinor: 50000,
			currency: "USD",
			confirmedAt: new Date().toISOString(),
		};
		const fetchMock = vi.fn((url: string) => {
			if (url.includes("/sponsorships/s-1")) return Promise.resolve(jsonResponse(status));
			if (url.includes("/sponsorship-packages")) return Promise.resolve(jsonResponse([publishedPackage]));
			if (url.includes("/sponsors")) return Promise.resolve(jsonResponse([]));
			return Promise.resolve(jsonResponse({}));
		});
		vi.stubGlobal("fetch", fetchMock);

		renderAt(`/sponsors/riverside-fc?packageId=${publishedPackage.id}&sponsorshipId=s-1`);

		expect(await screen.findByText(/thank you for your sponsorship/i)).toBeInTheDocument();
	});
});
