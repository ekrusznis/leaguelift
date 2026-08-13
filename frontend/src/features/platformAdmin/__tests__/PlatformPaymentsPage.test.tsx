import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";
import { PlatformPaymentsPage } from "../PlatformPaymentsPage";
import type { PlatformPaymentListItem } from "../types";

function jsonResponse(body: unknown, status = 200) {
	return new Response(JSON.stringify(body), { status, headers: { "content-type": "application/json" } });
}

function renderPage() {
	const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
	return render(
		<QueryClientProvider client={queryClient}>
			<MemoryRouter>
				<PlatformPaymentsPage />
			</MemoryRouter>
		</QueryClientProvider>,
	);
}

const orderPayment: PlatformPaymentListItem = {
	type: "ORDER",
	id: "11111111-1111-1111-1111-111111111111",
	organizationId: "22222222-2222-2222-2222-222222222222",
	organizationName: "North Jersey Volleyball Club",
	teamId: null,
	teamName: null,
	parentId: null,
	payerName: "Jane Doe",
	payerEmail: "jane@example.com",
	amountMinor: 2500,
	currency: "USD",
	status: "CONFIRMED",
	createdAt: "2026-06-01T00:00:00Z",
	confirmedAt: "2026-06-01T00:05:00Z",
	closedAt: null,
	canRefundOrVoid: true,
};

function stubFetch(items: PlatformPaymentListItem[] = []): ReturnType<typeof vi.fn> {
	const fetchMock = vi.fn().mockImplementation((url: string, options?: RequestInit) => {
		if (url.includes("/platform/admin/payments") && (!options?.method || options.method === "GET")) {
			return Promise.resolve(jsonResponse({ items, page: 0, size: 25, totalElements: items.length }));
		}
		if (url.includes("/refund")) return Promise.resolve(jsonResponse({}));
		return Promise.resolve(jsonResponse(null));
	});
	vi.stubGlobal("fetch", fetchMock);
	return fetchMock;
}

describe("PlatformPaymentsPage", () => {
	afterEach(() => {
		vi.unstubAllGlobals();
		vi.restoreAllMocks();
	});

	it("renders payments from every type", async () => {
		stubFetch([orderPayment]);
		renderPage();

		const row = (await screen.findByText("North Jersey Volleyball Club")).closest("tr");
		if (!row) throw new Error("row not found");
		expect(within(row).getByText("Jane Doe")).toBeInTheDocument();
		expect(within(row).getByText("Swag Shop order")).toBeInTheDocument();
	});

	it("shows an empty state when no payments match", async () => {
		stubFetch([]);
		renderPage();

		expect(await screen.findByText(/no payments match these filters/i)).toBeInTheDocument();
	});

	it("re-queries with search params when the filter form is submitted", async () => {
		const fetchMock = stubFetch([]);
		const user = userEvent.setup();
		renderPage();

		await screen.findByText(/no payments match these filters/i);
		await user.type(screen.getByLabelText(/search/i), "jane");
		await user.selectOptions(screen.getByLabelText(/^type$/i), "ORDER");
		await user.click(screen.getByRole("button", { name: /^search$/i }));

		await waitFor(() => {
			const call = fetchMock.mock.calls.find((c: unknown[]) => (c[0] as string).includes("query=jane"));
			expect(call).toBeDefined();
			expect(call![0]).toContain("type=ORDER");
		});
	});

	it("confirms before refunding and calls the order's own refund endpoint", async () => {
		const confirmSpy = vi.spyOn(window, "confirm").mockReturnValue(true);
		const fetchMock = stubFetch([orderPayment]);
		const user = userEvent.setup();
		renderPage();

		const row = (await screen.findByText("Jane Doe")).closest("tr");
		if (!row) throw new Error("row not found");
		await user.click(within(row).getByRole("button", { name: /refund/i }));

		expect(confirmSpy).toHaveBeenCalled();
		await waitFor(() => {
			const refundCall = fetchMock.mock.calls.find((c: unknown[]) => (c[0] as string).includes("/refund"));
			expect(refundCall).toBeDefined();
			expect(refundCall![0]).toContain(`/organizations/${orderPayment.organizationId}/orders/${orderPayment.id}/refund`);
		});
	});

	it("does not call refund when the confirmation is declined", async () => {
		vi.spyOn(window, "confirm").mockReturnValue(false);
		const fetchMock = stubFetch([orderPayment]);
		const user = userEvent.setup();
		renderPage();

		const row = (await screen.findByText("Jane Doe")).closest("tr");
		if (!row) throw new Error("row not found");
		await user.click(within(row).getByRole("button", { name: /refund/i }));

		expect(fetchMock.mock.calls.some((c: unknown[]) => (c[0] as string).includes("/refund"))).toBe(false);
	});
});
