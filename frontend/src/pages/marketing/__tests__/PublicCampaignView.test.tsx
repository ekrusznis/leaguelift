import { screen } from "@testing-library/react";
import { Route, Routes } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";
import { renderWithProviders } from "../../../test/testUtils";
import { PublicCampaignView } from "../PublicCampaignView";
import type { PublicCampaign } from "../../../features/fundraising/types";

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
};

function renderAt(slug: string) {
	return renderWithProviders(
		<Routes>
			<Route path="/campaigns/:slug" element={<PublicCampaignView />} />
		</Routes>,
		{ route: `/campaigns/${slug}` },
	);
}

describe("PublicCampaignView", () => {
	afterEach(() => {
		vi.unstubAllGlobals();
	});

	it("renders a published campaign's goal and dates", async () => {
		vi.stubGlobal(
			"fetch",
			vi.fn().mockResolvedValue(new Response(JSON.stringify(campaign), { status: 200, headers: { "content-type": "application/json" } })),
		);

		renderAt("spring-trip-fund");

		expect(await screen.findByRole("heading", { name: /spring trip fund/i })).toBeInTheDocument();
		expect(screen.getByText("$4,000.00")).toBeInTheDocument();
		expect(screen.getByText(/isn.t available yet/i)).toBeInTheDocument();
	});

	it("shows an error state for an unpublished or unknown slug", async () => {
		vi.stubGlobal(
			"fetch",
			vi.fn().mockResolvedValue(
				new Response(JSON.stringify({ code: "CAMPAIGN_NOT_FOUND", message: "not found", requestId: "r", fieldErrors: [] }), {
					status: 404,
					headers: { "content-type": "application/json" },
				}),
			),
		);

		renderAt("unknown-slug");

		expect(await screen.findByText(/could not be found or is not currently active/i)).toBeInTheDocument();
	});
});
