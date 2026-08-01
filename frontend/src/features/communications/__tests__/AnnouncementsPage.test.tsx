import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { renderWithProviders } from "../../../test/testUtils";
import { AnnouncementsPage } from "../AnnouncementsPage";

function jsonResponse(body: unknown, status = 200) {
	return new Response(JSON.stringify(body), { status, headers: { "content-type": "application/json" } });
}

describe("AnnouncementsPage", () => {
	afterEach(() => vi.unstubAllGlobals());

	it("shows an unread announcement and marks it read", async () => {
		const fetchMock = vi.fn((url: string, init?: RequestInit) => {
			if (url.includes("/me/contexts")) return Promise.resolve(jsonResponse([]));
			if (url.includes("/read") && init?.method === "POST") return Promise.resolve(new Response(null, { status: 204 }));
			return Promise.resolve(jsonResponse({
				items: [{ announcement: {
					id: "announcement-1", organizationId: "org-1", scopeType: "TEAM", scopeId: "team-1", scopeName: "15U Volleyball",
					kind: "GENERAL", relatedEntityType: null, relatedEntityId: null, title: "Practice moved", body: "Practice is on Court 2 tonight.",
					audience: "ALL", status: "PUBLISHED", emailEnabled: true, smsEnabled: false, publishedAt: "2026-08-01T15:00:00Z",
					recipientCount: 10, emailSentCount: 8, emailFailedCount: 0, smsSentCount: 0, smsFailedCount: 0,
					createdAt: "2026-08-01T14:00:00Z", updatedAt: "2026-08-01T15:00:00Z",
				}, readAt: null }], page: 0, size: 50, totalElements: 1,
			}));
		});
		vi.stubGlobal("fetch", fetchMock);
		const user = userEvent.setup();

		renderWithProviders(<AnnouncementsPage />, { route: "/app/announcements" });

		expect(await screen.findByRole("heading", { name: "Practice moved" })).toBeInTheDocument();
		await user.click(screen.getByRole("button", { name: "Mark read" }));
		expect(fetchMock).toHaveBeenCalledWith(expect.stringContaining("/me/announcements/announcement-1/read"), expect.objectContaining({ method: "POST" }));
	});
});
