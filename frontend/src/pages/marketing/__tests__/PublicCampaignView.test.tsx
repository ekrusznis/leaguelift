import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { Route, Routes } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";
import { renderWithProviders } from "../../../test/testUtils";
import { PublicCampaignView } from "../PublicCampaignView";
import type { ContributionStatusResult, PublicCampaign } from "../../../features/fundraising/types";

const campaign: PublicCampaign = {
	id: "22222222-2222-2222-2222-222222222222",
	organizationId: "11111111-1111-1111-1111-111111111111",
	teamId: null,
	name: "Spring Trip Fund",
	slug: "spring-trip-fund",
	description: "Help send the team to regionals.",
	campaignType: "TRAVEL",
	goalAmountMinor: 400000,
	currency: "USD",
	startDate: "2026-01-01",
	endDate: "2026-06-01",
	status: "ACTIVE",
	publishedAt: new Date().toISOString(),
	raisedMinor: 100000,
};

function jsonResponse(body: unknown, status = 200) {
	return new Response(JSON.stringify(body), { status, headers: { "content-type": "application/json" } });
}

function renderAt(path: string) {
	return renderWithProviders(
		<Routes>
			<Route path="/campaigns/:slug" element={<PublicCampaignView />} />
		</Routes>,
		{ route: path },
	);
}

describe("PublicCampaignView", () => {
	afterEach(() => {
		vi.unstubAllGlobals();
	});

	it("renders a published campaign's real progress bar", async () => {
		vi.stubGlobal("fetch", vi.fn().mockResolvedValue(jsonResponse(campaign)));

		renderAt("/campaigns/spring-trip-fund");

		expect(await screen.findByRole("heading", { level: 1, name: /spring trip fund/i })).toBeInTheDocument();
		expect(screen.getByText("$1,000.00")).toBeInTheDocument();
		expect(screen.getByText(/raised of \$4,000\.00 goal/i)).toBeInTheDocument();
		expect(screen.getByRole("progressbar")).toHaveAttribute("aria-valuenow", "25");
	});

	it("shows an error state for an unpublished or unknown slug", async () => {
		vi.stubGlobal(
			"fetch",
			vi.fn().mockResolvedValue(
				jsonResponse({ code: "CAMPAIGN_NOT_FOUND", message: "not found", requestId: "r", fieldErrors: [] }, 404),
			),
		);

		renderAt("/campaigns/unknown-slug");

		expect(await screen.findByText(/could not be found or is not currently active/i)).toBeInTheDocument();
	});

	it("submits the contribution form and redirects the browser to Stripe Checkout", async () => {
		const originalLocation = window.location;
		Object.defineProperty(window, "location", { writable: true, value: { ...originalLocation, href: "" } });

		const fetchMock = vi.fn((url: string, init?: RequestInit) => {
			if (init?.method === "POST" && url.includes("/contributions")) {
				return Promise.resolve(
					jsonResponse({ contributionId: "33333333-3333-3333-3333-333333333333", checkoutUrl: "https://checkout.stripe.com/test-session" }),
				);
			}
			return Promise.resolve(jsonResponse(campaign));
		});
		vi.stubGlobal("fetch", fetchMock);
		const user = userEvent.setup();

		renderAt("/campaigns/spring-trip-fund");
		await screen.findByRole("heading", { level: 1, name: /spring trip fund/i });

		await user.clear(screen.getByLabelText(/contribution amount/i));
		await user.type(screen.getByLabelText(/contribution amount/i), "2500");
		await user.type(screen.getByLabelText(/your name/i), "Jane Doe");
		await user.click(screen.getByRole("button", { name: /^contribute$/i }));

		await waitFor(() =>
			expect(fetchMock).toHaveBeenCalledWith(
				expect.stringContaining("/public/campaigns/spring-trip-fund/contributions"),
				expect.objectContaining({ method: "POST" }),
			),
		);
		await waitFor(() => expect(window.location.href).toBe("https://checkout.stripe.com/test-session"));

		Object.defineProperty(window, "location", { writable: true, value: originalLocation });
	});

	it("disables the name field and drops it from the request when giving anonymously", async () => {
		const fetchMock = vi.fn((url: string, init?: RequestInit) => {
			if (init?.method === "POST" && url.includes("/contributions")) {
				return Promise.resolve(jsonResponse({ contributionId: "id", checkoutUrl: "https://checkout.stripe.com/test" }));
			}
			return Promise.resolve(jsonResponse(campaign));
		});
		vi.stubGlobal("fetch", fetchMock);
		const user = userEvent.setup();

		renderAt("/campaigns/spring-trip-fund");
		await screen.findByRole("heading", { level: 1, name: /spring trip fund/i });

		await user.click(screen.getByLabelText(/give anonymously/i));
		expect(screen.getByLabelText(/your name/i)).toBeDisabled();

		await user.click(screen.getByRole("button", { name: /^contribute$/i }));

		await waitFor(() => expect(fetchMock).toHaveBeenCalledWith(expect.stringContaining("/contributions"), expect.objectContaining({ method: "POST" })));
		const call = fetchMock.mock.calls.find(([, init]) => (init as RequestInit | undefined)?.method === "POST");
		const postInit = call?.[1] as RequestInit;
		const body = JSON.parse(postInit.body as string);
		expect(body.isAnonymous).toBe(true);
		expect(body.supporterName).toBeNull();
	});

	it("shows a confirmation panel once the webhook has confirmed the contribution", async () => {
		const status: ContributionStatusResult = {
			id: "33333333-3333-3333-3333-333333333333",
			status: "CONFIRMED",
			amountMinor: 2500,
			currency: "USD",
			confirmedAt: new Date().toISOString(),
		};
		vi.stubGlobal(
			"fetch",
			vi.fn((url: string) => {
				if (url.includes("/contributions/")) return Promise.resolve(jsonResponse(status));
				return Promise.resolve(jsonResponse(campaign));
			}),
		);

		renderAt("/campaigns/spring-trip-fund?contributionId=33333333-3333-3333-3333-333333333333");

		expect(await screen.findByText(/thank you for your contribution/i)).toBeInTheDocument();
		expect(screen.getByText(/\$25\.00/)).toBeInTheDocument();
	});

	it("shows a processing state while the webhook confirmation is still pending", async () => {
		const status: ContributionStatusResult = {
			id: "33333333-3333-3333-3333-333333333333",
			status: "PENDING",
			amountMinor: 2500,
			currency: "USD",
			confirmedAt: null,
		};
		vi.stubGlobal(
			"fetch",
			vi.fn((url: string) => {
				if (url.includes("/contributions/")) return Promise.resolve(jsonResponse(status));
				return Promise.resolve(jsonResponse(campaign));
			}),
		);

		renderAt("/campaigns/spring-trip-fund?contributionId=33333333-3333-3333-3333-333333333333");

		expect(await screen.findByText(/confirming your contribution/i)).toBeInTheDocument();
	});
});
