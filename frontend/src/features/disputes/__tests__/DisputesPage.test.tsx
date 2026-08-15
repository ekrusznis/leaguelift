import { screen, within } from "@testing-library/react";
import { Route, Routes } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";
import { renderWithProviders } from "../../../test/testUtils";
import { DisputesPage } from "../DisputesPage";
import type { Dispute } from "../types";

const organizationId = "11111111-1111-1111-1111-111111111111";

function jsonResponse(body: unknown, status = 200) {
	return new Response(JSON.stringify(body), { status, headers: { "content-type": "application/json" } });
}

function disputePage(items: Dispute[]) {
	return {
		items,
		page: 0,
		size: 20,
		totalElements: items.length,
	};
}

function renderPage() {
	return renderWithProviders(
		<Routes>
			<Route path="/app/organizations/:organizationId/disputes" element={<DisputesPage />} />
		</Routes>,
		{ route: `/app/organizations/${organizationId}/disputes` },
	);
}

const openDispute: Dispute = {
	id: "22222222-2222-2222-2222-222222222222",
	sourceType: "ORDER",
	sourceId: "33333333-3333-3333-3333-333333333333",
	amountMinor: 5_000,
	currency: "usd",
	reason: "fraudulent",
	status: "NEEDS_RESPONSE",
	evidenceDueBy: new Date().toISOString(),
	openedAt: new Date().toISOString(),
	resolvedAt: null,
};

describe("DisputesPage", () => {
	afterEach(() => {
		vi.unstubAllGlobals();
	});

	it("shows an empty state when there are no disputes", async () => {
		vi.stubGlobal("fetch", vi.fn().mockImplementation(() => Promise.resolve(jsonResponse(disputePage([])))));

		renderPage();

		expect(await screen.findByText(/no disputes/i)).toBeInTheDocument();
	});

	it("lists a dispute with its status and source", async () => {
		vi.stubGlobal("fetch", vi.fn().mockImplementation(() => Promise.resolve(jsonResponse(disputePage([openDispute])))));

		renderPage();

		const reason = await screen.findByText("fraudulent");
		const row = reason.closest("tr");
		expect(row).not.toBeNull();
		expect(within(row as HTMLTableRowElement).getByText("Store order")).toBeInTheDocument();
		expect(within(row as HTMLTableRowElement).getByText("Needs response")).toBeInTheDocument();
		expect(within(row as HTMLTableRowElement).getByText("$50.00")).toBeInTheDocument();
	});
});
