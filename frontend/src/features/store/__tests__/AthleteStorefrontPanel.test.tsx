import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { renderWithProviders } from "../../../test/testUtils";
import { AthleteStorefrontPanel } from "../AthleteStorefrontPanel";
import type { AthleteStorefront, AthleteStorefrontPage } from "../athleteStorefrontApi";
import type { ProductPage, StorePage } from "../types";
import type { TeamPage } from "../../teams/types";
import type { Participant } from "../../households/types";
import type { OrganizationParticipantPage } from "../../onboarding/types";

const organizationId = "11111111-1111-1111-1111-111111111111";
const teamId = "22222222-2222-2222-2222-222222222222";
const storeId = "33333333-3333-3333-3333-333333333333";
const productId = "44444444-4444-4444-4444-444444444444";
const participantId = "55555555-5555-5555-5555-555555555555";
const storefrontId = "66666666-6666-6666-6666-666666666666";
const now = new Date().toISOString();

function jsonResponse(body: unknown, status = 200) {
	return new Response(JSON.stringify(body), { status, headers: { "content-type": "application/json" } });
}

const emptyStorefronts: AthleteStorefrontPage = { items: [], page: 0, size: 100, totalElements: 0 };

const team: TeamPage = {
	items: [
		{
			id: teamId,
			organizationId,
			name: "Riverside U10",
			sport: "SOCCER",
			sportOtherLabel: null,
			season: null,
			status: "ACTIVE",
			contactEmail: null,
			timezoneOverride: null,
			ageGroup: null,
			genderCategory: null,
			level: null,
			primaryColor: "#0B1F33",
			secondaryColor: "#20B26B",
			createdAt: now,
			updatedAt: now,
		},
	],
	page: 0,
	size: 20,
	totalElements: 1,
};

const stores: StorePage = {
	items: [{ id: storeId, organizationId, teamId, name: "Riverside Swag Shop", slug: "riverside-swag-shop", status: "ACTIVE", createdAt: now, updatedAt: now }],
	page: 0,
	size: 20,
	totalElements: 1,
};

const products: ProductPage = {
	items: [{ id: productId, organizationId, storeId, name: "Youth Hoodie", description: null, catalogSource: "MANUAL", manualVendorId: null, manualVendorName: null, printifyBlueprintId: null, printifyPrintPosition: "front", hasDesign: true, hasSwagLogo: false, status: "ACTIVE", createdAt: now, updatedAt: now }],
	page: 0,
	size: 20,
	totalElements: 1,
};

const roster: Participant[] = [
	{ id: participantId, householdId: "77777777-7777-7777-7777-777777777777", organizationId, firstName: "Maya", lastName: "Johnson", dateOfBirth: null, notes: null, status: "ACTIVE", createdAt: now, updatedAt: now },
];

const orgParticipants: OrganizationParticipantPage = {
	items: [{ id: participantId, householdId: "77777777-7777-7777-7777-777777777777", organizationId, firstName: "Maya", lastName: "Johnson", dateOfBirth: null, notes: null, status: "ACTIVE", createdAt: now, updatedAt: now }],
	page: 0,
	size: 500,
	totalElements: 1,
};

function draftStorefront(): AthleteStorefront {
	return { id: storefrontId, organizationId, participantId, teamId, storeId, slug: "maya-johnson", status: "DRAFT", publishedAt: null, createdAt: now, updatedAt: now };
}

describe("AthleteStorefrontPanel", () => {
	afterEach(() => {
		vi.unstubAllGlobals();
	});

	it("shows an empty state when there are no storefronts", async () => {
		vi.stubGlobal(
			"fetch",
			vi.fn((url: string) => {
				if (url.includes("/athlete-storefronts")) return Promise.resolve(jsonResponse(emptyStorefronts));
				if (url.includes("/teams")) return Promise.resolve(jsonResponse(team));
				if (url.includes("/stores")) return Promise.resolve(jsonResponse(stores));
				if (url.includes("/participants")) return Promise.resolve(jsonResponse(orgParticipants));
				return Promise.resolve(jsonResponse(null));
			}),
		);

		renderWithProviders(<AthleteStorefrontPanel organizationId={organizationId} />);

		expect(await screen.findByText(/no athlete storefronts yet/i)).toBeInTheDocument();
	});

	it("creates an athlete storefront from the roster/store/product picker form", async () => {
		const fetchMock = vi.fn((url: string, init?: RequestInit) => {
			if (init?.method === "POST" && url.includes("/athlete-storefronts")) return Promise.resolve(jsonResponse(draftStorefront(), 201));
			if (url.includes("/athlete-storefronts")) return Promise.resolve(jsonResponse(emptyStorefronts));
			if (url.includes(`/teams/${teamId}/participants`)) return Promise.resolve(jsonResponse(roster));
			if (url.includes("/teams")) return Promise.resolve(jsonResponse(team));
			if (url.includes(`/stores/${storeId}/products`)) return Promise.resolve(jsonResponse(products));
			if (url.includes("/stores")) return Promise.resolve(jsonResponse(stores));
			if (url.includes("/participants")) return Promise.resolve(jsonResponse(orgParticipants));
			return Promise.resolve(jsonResponse(null));
		});
		vi.stubGlobal("fetch", fetchMock);
		const user = userEvent.setup();

		renderWithProviders(<AthleteStorefrontPanel organizationId={organizationId} />);
		await screen.findByText(/no athlete storefronts yet/i);

		await user.click(screen.getByRole("button", { name: /add storefront/i }));
		await user.selectOptions(await screen.findByLabelText(/^team$/i), teamId);
		await user.selectOptions(await screen.findByLabelText(/^athlete$/i), participantId);
		await user.selectOptions(await screen.findByLabelText(/swag shop/i), storeId);
		await user.click(await screen.findByLabelText(/youth hoodie/i));
		await user.type(screen.getByLabelText(/public url slug/i), "maya-johnson");
		await user.click(screen.getByRole("button", { name: /^create storefront$/i }));

		await waitFor(() => {
			const call = fetchMock.mock.calls.find(
				(entry: unknown[]) => (entry[1] as RequestInit | undefined)?.method === "POST" && (entry[0] as string).includes("/athlete-storefronts"),
			);
			expect(call).toBeDefined();
			const body = JSON.parse((call![1] as { body: string }).body);
			expect(body.participantId).toBe(participantId);
			expect(body.storeId).toBe(storeId);
			expect(body.productIds).toEqual([productId]);
			expect(body.slug).toBe("maya-johnson");
		});
	});

	it("publishes a draft storefront and reveals the copy-link/QR controls", async () => {
		let currentStatus: "DRAFT" | "PUBLISHED" = "DRAFT";
		const fetchMock = vi.fn((url: string) => {
			if (url.includes("/publish")) {
				currentStatus = "PUBLISHED";
				return Promise.resolve(jsonResponse({ ...draftStorefront(), status: "PUBLISHED", publishedAt: now }));
			}
			if (url.includes("/share-link-qr")) return Promise.resolve(jsonResponse({ qrDataUri: "data:image/png;base64,abc" }));
			if (url.includes("/athlete-storefronts")) {
				return Promise.resolve(jsonResponse({ items: [{ ...draftStorefront(), status: currentStatus, publishedAt: currentStatus === "PUBLISHED" ? now : null }], page: 0, size: 100, totalElements: 1 }));
			}
			if (url.includes("/teams")) return Promise.resolve(jsonResponse(team));
			if (url.includes("/stores")) return Promise.resolve(jsonResponse(stores));
			if (url.includes("/participants")) return Promise.resolve(jsonResponse(orgParticipants));
			return Promise.resolve(jsonResponse(null));
		});
		vi.stubGlobal("fetch", fetchMock);
		const user = userEvent.setup();

		renderWithProviders(<AthleteStorefrontPanel organizationId={organizationId} />);
		await screen.findByText(/maya johnson/i);

		await user.click(screen.getByRole("button", { name: /^publish$/i }));

		await waitFor(() =>
			expect(fetchMock).toHaveBeenCalledWith(
				expect.stringContaining(`/athlete-storefronts/${storefrontId}/publish`),
				expect.objectContaining({ method: "PATCH" }),
			),
		);

		await user.click(await screen.findByRole("button", { name: /show qr/i }));

		await waitFor(() =>
			expect(fetchMock).toHaveBeenCalledWith(
				expect.stringContaining("/athlete-storefronts/share-link-qr"),
				expect.objectContaining({ method: "POST" }),
			),
		);
		expect(await screen.findByAltText(/qr code for/i)).toBeInTheDocument();
	});
});
