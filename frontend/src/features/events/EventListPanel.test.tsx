import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { renderWithProviders } from "../../test/testUtils";
import { EventListPanel } from "./EventListPanel";
import type { Rally26Event } from "./types";

const organizationId = "11111111-1111-1111-1111-111111111111";
const now = new Date().toISOString();

function jsonResponse(body: unknown, status = 200) {
	return new Response(JSON.stringify(body), { status, headers: { "content-type": "application/json" } });
}

function sampleEvent(overrides: Partial<Rally26Event> = {}): Rally26Event {
	return {
		id: "22222222-2222-2222-2222-222222222222",
		organizationId,
		teamId: null,
		tournamentId: null,
		opponentTeamId: null,
		opponentName: null,
		eventType: "PRACTICE",
		displayTitle: "Practice",
		description: null,
		status: "SCHEDULED",
		startAt: "2026-08-15T18:00:00Z",
		endAt: null,
		arrivalAt: null,
		meetingAt: null,
		timezone: "America/New_York",
		venueName: null,
		address: null,
		latitude: null,
		longitude: null,
		area: null,
		meetingPoint: null,
		directionsNotes: null,
		visibility: "TEAM",
		sourceType: "MANUAL",
		createdAt: now,
		updatedAt: now,
		allDayDate: null,
		...overrides,
	};
}

describe("EventListPanel", () => {
	afterEach(() => {
		vi.unstubAllGlobals();
	});

	it("shows an all-day event's fixed-format date with no time, instead of a timed timestamp", async () => {
		const event = sampleEvent({ startAt: null, allDayDate: "2026-07-04" });
		const fetchMock = vi.fn(() => Promise.resolve(jsonResponse([event])));
		vi.stubGlobal("fetch", fetchMock);

		renderWithProviders(<EventListPanel scope={{ type: "organization", organizationId }} />);

		expect(await screen.findByText(/04\/07\/2026 \(all day\)/)).toBeInTheDocument();
	});

	it("checking the all-day box swaps the timed inputs for a single date field and submits allDayDate with null instants", async () => {
		const fetchMock = vi.fn((url: string, init?: RequestInit) => {
			if (init?.method === "POST" && url.includes("/events")) return Promise.resolve(jsonResponse(sampleEvent(), 200));
			if (url.includes("/events/timezone-default")) return Promise.resolve(jsonResponse({ timezone: "America/Chicago" }));
			if (url.includes("/event-templates")) return Promise.resolve(jsonResponse([]));
			if (url.includes("/events")) return Promise.resolve(jsonResponse([]));
			return Promise.resolve(jsonResponse(null));
		});
		vi.stubGlobal("fetch", fetchMock);
		const user = userEvent.setup();

		renderWithProviders(<EventListPanel scope={{ type: "organization", organizationId }} canManage />);

		await user.click(await screen.findByRole("button", { name: /create event/i }));
		await user.click(screen.getByLabelText(/all-day event/i));
		expect(screen.queryByLabelText(/^start time$/i)).not.toBeInTheDocument();
		await user.type(screen.getByLabelText(/^date$/i), "2026-07-04");
		await user.click(screen.getByRole("button", { name: /^create event$/i }));

		await waitFor(() => {
			const call = fetchMock.mock.calls.find(
				(entry: unknown[]) => (entry[1] as RequestInit | undefined)?.method === "POST" && (entry[0] as string).includes("/events"),
			);
			expect(call).toBeDefined();
			const body = JSON.parse((call![1] as { body: string }).body);
			expect(body.allDayDate).toBe("2026-07-04");
			expect(body.startAt).toBeNull();
			expect(body.endAt).toBeNull();
			expect(body.arrivalAt).toBeNull();
			expect(body.meetingAt).toBeNull();
		});
	});

	it("pre-fills the timezone field from the resolved effective-zone default, not just the browser zone", async () => {
		const fetchMock = vi.fn((url: string) => {
			if (url.includes("/events/timezone-default")) return Promise.resolve(jsonResponse({ timezone: "America/Denver" }));
			if (url.includes("/event-templates")) return Promise.resolve(jsonResponse([]));
			if (url.includes("/events")) return Promise.resolve(jsonResponse([]));
			return Promise.resolve(jsonResponse(null));
		});
		vi.stubGlobal("fetch", fetchMock);
		const user = userEvent.setup();

		renderWithProviders(<EventListPanel scope={{ type: "organization", organizationId }} canManage />);

		await user.click(await screen.findByRole("button", { name: /create event/i }));

		await waitFor(() => {
			expect(screen.getByLabelText(/timezone/i)).toHaveValue("America/Denver");
		});
	});
});
