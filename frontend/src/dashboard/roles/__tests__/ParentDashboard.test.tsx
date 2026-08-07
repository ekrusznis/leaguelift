import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";
import { AuthProvider } from "../../../auth/AuthContext";
import type { OrderSummary } from "../../types";
import { ParentDashboard } from "../ParentDashboard";

const organizationId = "11111111-1111-1111-1111-111111111111";
const householdId = "22222222-2222-2222-2222-222222222222";

function jsonResponse(body: unknown, status = 200) {
	return new Response(JSON.stringify(body), { status, headers: { "content-type": "application/json" } });
}

function renderDashboard() {
	const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
	return render(
		<QueryClientProvider client={queryClient}>
			<AuthProvider>
				<MemoryRouter initialEntries={["/app"]}>
					<ParentDashboard organizationId={organizationId} householdId={householdId} />
				</MemoryRouter>
			</AuthProvider>
		</QueryClientProvider>,
	);
}

/** Every other dashboard card endpoint is intentionally unmocked — each card renders its own isolated state, matching the convention in test/__tests__/App.test.tsx. */
function stubFetch(recentOrdersResponse: OrderSummary[]) {
	vi.stubGlobal(
		"fetch",
		vi.fn((url: string) => {
			if (url.includes("/dashboard/parent/recent-orders")) return Promise.resolve(jsonResponse(recentOrdersResponse));
			return Promise.resolve(jsonResponse({ code: "NOT_FOUND", message: "not mocked", requestId: "req_1", fieldErrors: [] }, 404));
		}),
	);
}

const order: OrderSummary = {
	id: "33333333-3333-3333-3333-333333333333",
	productName: "Youth Hoodie - Youth M",
	orderNumber: "#33333333",
	orderedAt: "2026-08-01",
	status: "SHIPPED",
	creditGrantedMinor: 250,
	creditStatus: "AVAILABLE",
};

describe("ParentDashboard Recent Orders card (Phase 24 slice 24.3)", () => {
	afterEach(() => {
		vi.unstubAllGlobals();
	});

	it("shows real household-attributed storefront orders with fulfillment status and credit amount", async () => {
		stubFetch([order]);

		renderDashboard();

		expect(await screen.findByText(/recent orders/i)).toBeInTheDocument();
		expect(await screen.findByText(/youth hoodie - youth m/i)).toBeInTheDocument();
		expect(screen.getByText("SHIPPED")).toBeInTheDocument();
		expect(screen.getByText(/\+\$2\.50 credit/i)).toBeInTheDocument();
	});

	it("shows an empty state when this household has no storefront-attributed orders yet", async () => {
		stubFetch([]);

		renderDashboard();

		expect(await screen.findByText(/no storefront orders yet/i)).toBeInTheDocument();
	});

	it("shows a reversed-credit label instead of a granted amount once the grant is revoked", async () => {
		stubFetch([{ ...order, creditStatus: "REVOKED" }]);

		renderDashboard();

		expect(await screen.findByText(/credit reversed/i)).toBeInTheDocument();
		expect(screen.queryByText(/\+\$2\.50 credit/i)).not.toBeInTheDocument();
	});
});
