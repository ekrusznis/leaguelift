import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { renderWithProviders } from "../../../test/testUtils";
import { StoreList } from "../StoreList";
import type { Store, StorePage } from "../types";

const organizationId = "11111111-1111-1111-1111-111111111111";

const emptyStores: StorePage = { items: [], page: 0, size: 20, totalElements: 0 };

function jsonResponse(body: unknown, status = 200) {
	return new Response(JSON.stringify(body), { status, headers: { "content-type": "application/json" } });
}

const draftStore: Store = {
	id: "22222222-2222-2222-2222-222222222222",
	organizationId,
	teamId: null,
	name: "Spring Store",
	slug: "spring-store",
	status: "DRAFT",
	createdAt: new Date().toISOString(),
	updatedAt: new Date().toISOString(),
};

describe("StoreList", () => {
	afterEach(() => {
		vi.unstubAllGlobals();
	});

	it("shows an empty state when there are no stores", async () => {
		vi.stubGlobal("fetch", vi.fn().mockImplementation(() => Promise.resolve(jsonResponse(emptyStores))));

		renderWithProviders(<StoreList organizationId={organizationId} />);

		expect(await screen.findByText(/no swag shops yet/i)).toBeInTheDocument();
	});

	it("creates a store from the form", async () => {
		const fetchMock = vi.fn((_url: string, init?: RequestInit) => {
			if (init?.method === "POST") return Promise.resolve(jsonResponse(draftStore, 201));
			return Promise.resolve(jsonResponse(emptyStores));
		});
		vi.stubGlobal("fetch", fetchMock);
		const user = userEvent.setup();

		renderWithProviders(<StoreList organizationId={organizationId} />);
		await screen.findByText(/no swag shops yet/i);

		await user.click(screen.getByRole("button", { name: /add swag shop/i }));
		await user.type(screen.getByLabelText(/^name/i), "Spring Store");
		await user.type(screen.getByLabelText(/public url slug/i), "spring-store");
		await user.click(screen.getByRole("button", { name: /create swag shop/i }));

		await waitFor(() =>
			expect(fetchMock).toHaveBeenCalledWith(
				expect.stringContaining(`/organizations/${organizationId}/stores`),
				expect.objectContaining({ method: "POST" }),
			),
		);
	});

	it("activates a draft store", async () => {
		const fetchMock = vi.fn((url: string, _init?: RequestInit) => {
			if (url.includes("/status")) return Promise.resolve(jsonResponse({ ...draftStore, status: "ACTIVE" }));
			return Promise.resolve(jsonResponse({ items: [draftStore], page: 0, size: 20, totalElements: 1 }));
		});
		vi.stubGlobal("fetch", fetchMock);
		const user = userEvent.setup();

		renderWithProviders(<StoreList organizationId={organizationId} />);
		await screen.findByText(/spring store/i);

		await user.click(screen.getByRole("button", { name: /activate/i }));

		await waitFor(() =>
			expect(fetchMock).toHaveBeenCalledWith(
				expect.stringContaining(`/stores/${draftStore.id}/status`),
				expect.objectContaining({ method: "PATCH" }),
			),
		);
	});
});
