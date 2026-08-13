import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";
import { PlatformReportsPage } from "../PlatformReportsPage";
import type { PlatformReport } from "../types";

function jsonResponse(body: unknown) {
	return new Response(JSON.stringify(body), { status: 200, headers: { "content-type": "application/json" } });
}

function renderPage() {
	const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
	return render(
		<QueryClientProvider client={queryClient}>
			<MemoryRouter>
				<PlatformReportsPage />
			</MemoryRouter>
		</QueryClientProvider>,
	);
}

const report: PlatformReport = {
	from: "2026-07-01",
	to: "2026-08-01",
	newOrganizations: 3,
	activeOrganizations: 12,
	newCustomers: 5,
	grossTransactionVolumeMinor: 3_000_000,
	refundedMinor: 0,
	refundRatePercent: null,
	webhookProcessed: 100,
	webhookFailed: 0,
	outboxPending: 0,
	outboxDeadLetter: 0,
	platformFeeRevenueMinor: 150_000,
	stripeProcessingFeesMinor: 87_360,
	netMarginAfterStripeFeesMinor: 62_640,
};

describe("PlatformReportsPage", () => {
	afterEach(() => {
		vi.unstubAllGlobals();
	});

	it("shows platform fee revenue, Stripe's real processing fee, and the net margin between them", async () => {
		vi.stubGlobal("fetch", vi.fn().mockResolvedValue(jsonResponse(report)));
		renderPage();

		expect(await screen.findByText("Platform fee revenue")).toBeInTheDocument();
		expect(screen.getByText("$1,500.00")).toBeInTheDocument();
		expect(screen.getByText("Stripe processing fees")).toBeInTheDocument();
		expect(screen.getByText("$873.60")).toBeInTheDocument();
		expect(screen.getByText("Net margin after Stripe fees")).toBeInTheDocument();
		expect(screen.getByText("$626.40")).toBeInTheDocument();
	});
});
