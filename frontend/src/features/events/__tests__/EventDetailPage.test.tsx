import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";
import { AuthProvider } from "../../../auth/AuthContext";
import type { AuthorizationContext } from "../../../authorization/types";
import { EventDetailPage } from "../EventDetailPage";
import type { Rally26Event } from "../types";

const organizationId = "11111111-1111-1111-1111-111111111111";
const eventId = "22222222-2222-2222-2222-222222222222";
const teamId = "33333333-3333-3333-3333-333333333333";

function jsonResponse(body: unknown, status = 200) {
	return new Response(JSON.stringify(body), { status, headers: { "content-type": "application/json" } });
}

function renderPage() {
	const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
	return render(
		<QueryClientProvider client={queryClient}>
			<AuthProvider>
				<MemoryRouter initialEntries={[`/app/organizations/${organizationId}/events/${eventId}`]}>
					<Routes>
						<Route path="/app/organizations/:organizationId/events/:eventId" element={<EventDetailPage />} />
					</Routes>
				</MemoryRouter>
			</AuthProvider>
		</QueryClientProvider>,
	);
}

const organizationContext: AuthorizationContext = {
	contextType: "ORGANIZATION",
	resourceId: organizationId,
	organizationId,
	label: "My Club",
	role: "ADMINISTRATOR",
	capabilities: ["organization.event.manage"],
};

const baseEvent: Rally26Event = {
	id: eventId,
	organizationId,
	teamId,
	tournamentId: null,
	opponentTeamId: null,
	opponentName: null,
	eventType: "PRACTICE",
	displayTitle: "Varsity Practice",
	description: null,
	status: "TENTATIVE",
	startAt: "2026-09-05T19:30:00Z",
	endAt: null,
	arrivalAt: null,
	meetingAt: null,
	timezone: "America/New_York",
	venueName: "Old venue",
	address: null,
	latitude: null,
	longitude: null,
	area: null,
	meetingPoint: null,
	directionsNotes: null,
	visibility: "TEAM",
	sourceType: "ICS_FEED",
	createdAt: "2026-08-01T00:00:00Z",
	updatedAt: "2026-08-01T00:00:00Z",
	allDayDate: null,
	pendingSourceChanges: null,
};

function stubFetch(event: Rally26Event): ReturnType<typeof vi.fn> {
	const fetchMock = vi.fn().mockImplementation((url: string, options?: RequestInit) => {
		if (url.includes("/me/contexts")) return Promise.resolve(jsonResponse([organizationContext]));
		if (url.includes("/apply-source-update") && options?.method === "POST") {
			return Promise.resolve(jsonResponse({ ...event, venueName: "New venue", pendingSourceChanges: null }));
		}
		if (url.includes(`/events/${eventId}/rsvps`)) return Promise.resolve(jsonResponse({ summary: { attending: 0, notAttending: 0, maybe: 0, noResponse: 0 }, responses: [] }));
		if (url.includes("/directions")) return Promise.resolve(jsonResponse({ url: null }));
		if (url.endsWith(`/events/${eventId}`)) return Promise.resolve(jsonResponse(event));
		return Promise.resolve(jsonResponse(null));
	});
	vi.stubGlobal("fetch", fetchMock);
	return fetchMock;
}

describe("EventDetailPage — source update review", () => {
	afterEach(() => {
		vi.unstubAllGlobals();
	});

	it("shows no update banner when nothing is pending from the source", async () => {
		stubFetch(baseEvent);
		renderPage();

		expect(await screen.findByText("Varsity Practice")).toBeInTheDocument();
		expect(screen.queryByText(/an update is available from this event's source/i)).not.toBeInTheDocument();
	});

	it("shows the update banner, opens a diff dialog, and applies the update on confirm", async () => {
		const eventWithPendingUpdate: Rally26Event = {
			...baseEvent,
			pendingSourceChanges: [{ field: "venueName", oldValue: "Old venue", newValue: "New venue" }],
		};
		const fetchMock = stubFetch(eventWithPendingUpdate);
		const user = userEvent.setup();
		renderPage();

		expect(await screen.findByText(/an update is available from this event's source/i)).toBeInTheDocument();
		await user.click(screen.getByRole("button", { name: /review update/i }));

		const dialog = await screen.findByRole("dialog", { name: /update this event from its source/i });
		expect(within(dialog).getByText("Old venue")).toBeInTheDocument();
		expect(within(dialog).getByText("New venue")).toBeInTheDocument();

		await user.click(within(dialog).getByRole("button", { name: /apply update/i }));

		await waitFor(() => {
			const applyCall = fetchMock.mock.calls.find((c: unknown[]) => (c[0] as string).includes("/apply-source-update"));
			expect(applyCall).toBeDefined();
			expect((applyCall![1] as RequestInit).method).toBe("POST");
		});
		expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
	});
});
