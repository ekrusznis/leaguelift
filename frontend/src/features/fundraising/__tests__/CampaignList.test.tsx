import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { renderWithProviders } from "../../../test/testUtils";
import { CampaignList } from "../CampaignList";
import type { Campaign, CampaignPage } from "../types";

const organizationId = "11111111-1111-1111-1111-111111111111";

const emptyCampaigns: CampaignPage = { items: [], page: 0, size: 20, totalElements: 0 };
const emptyTeams = { items: [], page: 0, size: 20, totalElements: 0 };

function jsonResponse(body: unknown, status = 200) {
	return new Response(JSON.stringify(body), { status, headers: { "content-type": "application/json" } });
}

const draftCampaign: Campaign = {
	id: "22222222-2222-2222-2222-222222222222",
	organizationId,
	teamId: null,
	name: "Spring Trip Fund",
	slug: "spring-trip-fund",
	description: "Help send the team to regionals.",
	campaignType: "TRAVEL",
	goalAmountMinor: 400000,
	currency: "USD",
	startDate: null,
	endDate: null,
	status: "DRAFT",
	publishedAt: null,
	createdAt: new Date().toISOString(),
	updatedAt: new Date().toISOString(),
	raisedMinor: 0,
};

describe("CampaignList", () => {
	afterEach(() => {
		vi.unstubAllGlobals();
	});

	it("shows an empty state when there are no campaigns", async () => {
		const fetchMock = vi.fn((url: string) => {
			if (url.includes("/teams")) return Promise.resolve(jsonResponse(emptyTeams));
			return Promise.resolve(jsonResponse(emptyCampaigns));
		});
		vi.stubGlobal("fetch", fetchMock);

		renderWithProviders(<CampaignList organizationId={organizationId} />);

		expect(await screen.findByText(/no campaigns yet/i)).toBeInTheDocument();
	});

	it("creates a campaign from the form", async () => {
		const fetchMock = vi.fn((url: string, init?: RequestInit) => {
			if (url.includes("/teams")) return Promise.resolve(jsonResponse(emptyTeams));
			if (init?.method === "POST") return Promise.resolve(jsonResponse(draftCampaign, 201));
			return Promise.resolve(jsonResponse(emptyCampaigns));
		});
		vi.stubGlobal("fetch", fetchMock);
		const user = userEvent.setup();

		renderWithProviders(<CampaignList organizationId={organizationId} />);
		await screen.findByText(/no campaigns yet/i);

		await user.click(screen.getByRole("button", { name: /add campaign/i }));
		await user.type(screen.getByLabelText(/^name/i), "Spring Trip Fund");
		await user.type(screen.getByLabelText(/public url slug/i), "spring-trip-fund");
		await user.type(screen.getByLabelText(/goal \(cents\)/i), "400000");
		await user.click(screen.getByRole("button", { name: /create campaign/i }));

		await waitFor(() =>
			expect(fetchMock).toHaveBeenCalledWith(
				expect.stringContaining(`/organizations/${organizationId}/campaigns`),
				expect.objectContaining({ method: "POST" }),
			),
		);
	});

	it("publishes a draft campaign", async () => {
		const fetchMock = vi.fn((url: string) => {
			if (url.includes("/teams")) return Promise.resolve(jsonResponse(emptyTeams));
			if (url.includes("/publish")) return Promise.resolve(jsonResponse({ ...draftCampaign, status: "ACTIVE" }));
			return Promise.resolve(jsonResponse({ items: [draftCampaign], page: 0, size: 20, totalElements: 1 }));
		});
		vi.stubGlobal("fetch", fetchMock);
		const user = userEvent.setup();

		renderWithProviders(<CampaignList organizationId={organizationId} />);
		await screen.findByText(/spring trip fund/i);

		await user.click(screen.getByRole("button", { name: /publish/i }));

		await waitFor(() =>
			expect(fetchMock).toHaveBeenCalledWith(
				expect.stringContaining(`/campaigns/${draftCampaign.id}/publish`),
				expect.objectContaining({ method: "POST" }),
			),
		);
	});
});
