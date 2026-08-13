import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";
import { PlatformRosterPage } from "../PlatformRosterPage";
import type { PlatformAthleteListItem, PlatformCoachListItem } from "../types";

function jsonResponse(body: unknown, status = 200) {
	return new Response(JSON.stringify(body), { status, headers: { "content-type": "application/json" } });
}

function renderPage() {
	const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
	return render(
		<QueryClientProvider client={queryClient}>
			<MemoryRouter>
				<PlatformRosterPage />
			</MemoryRouter>
		</QueryClientProvider>,
	);
}

const athlete: PlatformAthleteListItem = {
	participantId: "11111111-1111-1111-1111-111111111111",
	firstName: "Jane",
	lastName: "Doe",
	dateOfBirth: "2014-05-01",
	householdId: "22222222-2222-2222-2222-222222222222",
	householdName: "The Doe Household",
	organizationId: "33333333-3333-3333-3333-333333333333",
	organizationName: "North Jersey Volleyball Club",
	teamNames: ["U12 Sharks"],
	eligibilityStatus: "INELIGIBLE",
};

const coach: PlatformCoachListItem = {
	roleAssignmentId: "44444444-4444-4444-4444-444444444444",
	userId: "55555555-5555-5555-5555-555555555555",
	displayName: "Coach Smith",
	email: "smith@example.com",
	role: "TEAM_MANAGER",
	teamId: "66666666-6666-6666-6666-666666666666",
	teamName: "U12 Sharks",
	organizationId: "33333333-3333-3333-3333-333333333333",
	organizationName: "North Jersey Volleyball Club",
};

function stubFetch(athletes: PlatformAthleteListItem[] = [], coaches: PlatformCoachListItem[] = []): ReturnType<typeof vi.fn> {
	const fetchMock = vi.fn().mockImplementation((url: string) => {
		if (url.includes("/platform/admin/athletes")) {
			return Promise.resolve(jsonResponse({ items: athletes, page: 0, size: 25, totalElements: athletes.length }));
		}
		if (url.includes("/platform/admin/coaches")) {
			return Promise.resolve(jsonResponse({ items: coaches, page: 0, size: 25, totalElements: coaches.length }));
		}
		return Promise.resolve(jsonResponse(null));
	});
	vi.stubGlobal("fetch", fetchMock);
	return fetchMock;
}

describe("PlatformRosterPage", () => {
	afterEach(() => {
		vi.unstubAllGlobals();
		vi.restoreAllMocks();
	});

	it("renders athletes with their eligibility status by default", async () => {
		stubFetch([athlete]);
		renderPage();

		const row = (await screen.findByText("Jane Doe")).closest("tr");
		if (!row) throw new Error("row not found");
		expect(within(row).getByText("The Doe Household")).toBeInTheDocument();
		expect(within(row).getByText("U12 Sharks")).toBeInTheDocument();
		expect(within(row).getByText(/ineligible/i)).toBeInTheDocument();
	});

	it("shows an empty state when no athletes match", async () => {
		stubFetch([]);
		renderPage();

		expect(await screen.findByText(/no athletes match these filters/i)).toBeInTheDocument();
	});

	it("switches to coaches and renders coach rows", async () => {
		stubFetch([], [coach]);
		const user = userEvent.setup();
		renderPage();

		await screen.findByText(/no athletes match these filters/i);
		await user.click(screen.getByRole("tab", { name: "Coaches" }));

		const row = (await screen.findByText("Coach Smith")).closest("tr");
		if (!row) throw new Error("row not found");
		expect(within(row).getByText("smith@example.com")).toBeInTheDocument();
		expect(within(row).getByText("Team manager")).toBeInTheDocument();
	});

	it("switches to card view and still renders the same athlete data", async () => {
		stubFetch([athlete]);
		const user = userEvent.setup();
		renderPage();

		await screen.findByText("Jane Doe");
		await user.click(screen.getByRole("tab", { name: "Cards" }));

		expect(screen.queryByRole("table")).not.toBeInTheDocument();
		expect(screen.getByText("Jane Doe")).toBeInTheDocument();
		expect(screen.getByText(/household: the doe household/i)).toBeInTheDocument();
	});

	it("re-queries athletes with search and eligibility filters", async () => {
		const fetchMock = stubFetch([]);
		const user = userEvent.setup();
		renderPage();

		await screen.findByText(/no athletes match these filters/i);
		await user.type(screen.getByLabelText(/search/i), "jane");
		await user.selectOptions(screen.getByLabelText(/eligibility/i), "INELIGIBLE");
		await user.click(screen.getByRole("button", { name: /^search$/i }));

		await waitFor(() => {
			const call = fetchMock.mock.calls.find((c: unknown[]) => (c[0] as string).includes("query=jane"));
			expect(call).toBeDefined();
			expect(call![0]).toContain("eligibilityStatus=INELIGIBLE");
		});
	});

	it("links each athlete row to its organization console", async () => {
		stubFetch([athlete]);
		renderPage();

		const row = (await screen.findByText("Jane Doe")).closest("tr");
		if (!row) throw new Error("row not found");
		expect(within(row).getByRole("link", { name: /open organization/i })).toHaveAttribute(
			"href",
			`/app/platform/organizations/${athlete.organizationId}`,
		);
	});
});
