import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { renderWithProviders } from "../../../test/testUtils";
import { SponsorshipPackageList } from "../SponsorshipPackageList";
import type { SponsorshipPackage, SponsorshipPackagePage } from "../types";

const organizationId = "11111111-1111-1111-1111-111111111111";

const emptyPackages: SponsorshipPackagePage = { items: [], page: 0, size: 20, totalElements: 0 };

function jsonResponse(body: unknown, status = 200) {
	return new Response(JSON.stringify(body), { status, headers: { "content-type": "application/json" } });
}

const draftPackage: SponsorshipPackage = {
	id: "22222222-2222-2222-2222-222222222222",
	organizationId,
	name: "Gold Sponsor",
	description: "Top-tier sponsorship",
	priceMinor: 50000,
	currency: "USD",
	maxQuantity: 5,
	exclusive: false,
	placementStartDate: null,
	placementEndDate: null,
	status: "DRAFT",
	createdAt: new Date().toISOString(),
	updatedAt: new Date().toISOString(),
	confirmedCount: 0,
	soldOut: false,
};

describe("SponsorshipPackageList", () => {
	afterEach(() => {
		vi.unstubAllGlobals();
	});

	it("shows an empty state when there are no sponsorship packages", async () => {
		vi.stubGlobal("fetch", vi.fn().mockResolvedValue(jsonResponse(emptyPackages)));

		renderWithProviders(<SponsorshipPackageList organizationId={organizationId} />);

		expect(await screen.findByText(/no sponsorship packages yet/i)).toBeInTheDocument();
	});

	it("creates a sponsorship package from the form", async () => {
		const fetchMock = vi.fn((_url: string, init?: RequestInit) => {
			if (init?.method === "POST") return Promise.resolve(jsonResponse(draftPackage, 201));
			return Promise.resolve(jsonResponse(emptyPackages));
		});
		vi.stubGlobal("fetch", fetchMock);
		const user = userEvent.setup();

		renderWithProviders(<SponsorshipPackageList organizationId={organizationId} />);
		await screen.findByText(/no sponsorship packages yet/i);

		await user.click(screen.getByRole("button", { name: /add sponsorship package/i }));
		await user.type(screen.getByLabelText(/^name/i), "Gold Sponsor");
		await user.type(screen.getByLabelText(/price \(cents\)/i), "50000");
		await user.click(screen.getByRole("button", { name: /create package/i }));

		await waitFor(() =>
			expect(fetchMock).toHaveBeenCalledWith(
				expect.stringContaining(`/organizations/${organizationId}/sponsorship-packages`),
				expect.objectContaining({ method: "POST" }),
			),
		);
	});

	it("publishes a draft sponsorship package", async () => {
		const fetchMock = vi.fn((url: string) => {
			if (url.includes("/publish")) return Promise.resolve(jsonResponse({ ...draftPackage, status: "PUBLISHED" }));
			return Promise.resolve(jsonResponse({ items: [draftPackage], page: 0, size: 20, totalElements: 1 }));
		});
		vi.stubGlobal("fetch", fetchMock);
		const user = userEvent.setup();

		renderWithProviders(<SponsorshipPackageList organizationId={organizationId} />);
		await screen.findByText(/gold sponsor/i);

		await user.click(screen.getByRole("button", { name: /publish/i }));

		await waitFor(() =>
			expect(fetchMock).toHaveBeenCalledWith(
				expect.stringContaining(`/sponsorship-packages/${draftPackage.id}/publish`),
				expect.objectContaining({ method: "POST" }),
			),
		);
	});

	it("shows a sold-out badge and hides the manage-sponsors expansion behind a toggle", async () => {
		const soldOutPackage: SponsorshipPackage = { ...draftPackage, status: "PUBLISHED", maxQuantity: 1, confirmedCount: 1, soldOut: true };
		vi.stubGlobal(
			"fetch",
			vi.fn().mockResolvedValue(jsonResponse({ items: [soldOutPackage], page: 0, size: 20, totalElements: 1 })),
		);

		renderWithProviders(<SponsorshipPackageList organizationId={organizationId} />);

		expect(await screen.findByText(/sold out/i)).toBeInTheDocument();
		expect(screen.queryByRole("button", { name: /publish/i })).not.toBeInTheDocument();
	});
});
