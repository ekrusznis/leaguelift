import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { renderWithProviders } from "../../../test/testUtils";
import { InvitationsPanel } from "../InvitationsPanel";

const organizationId = "11111111-1111-1111-1111-111111111111";

function jsonResponse(body: unknown, status = 200) {
	return new Response(JSON.stringify(body), { status, headers: { "content-type": "application/json" } });
}

function emptyInvitationsPage() {
	return { items: [], page: 0, size: 20, totalElements: 0 };
}

function emptyMembersPage() {
	return { items: [], page: 0, size: 25, totalElements: 0 };
}

function baseFetch(url: string) {
	if (url.includes("/members/search")) return Promise.resolve(jsonResponse(emptyMembersPage()));
	if (url.includes("/invitations")) return Promise.resolve(jsonResponse(emptyInvitationsPage()));
	return Promise.resolve(jsonResponse(null));
}

describe("InvitationsPanel", () => {
	afterEach(() => {
		vi.unstubAllGlobals();
	});

	it("shows an empty state when there are no pending invitations", async () => {
		vi.stubGlobal("fetch", vi.fn().mockImplementation((url: string) => baseFetch(url)));

		renderWithProviders(<InvitationsPanel organizationId={organizationId} />);

		expect(await screen.findByText(/no pending invitations/i)).toBeInTheDocument();
	});

	it("sends an invitation and clears the form", async () => {
		const fetchMock = vi.fn().mockImplementation((url: string, options?: RequestInit) => {
			if (options?.method === "POST" && url.includes("/invitations")) {
				return Promise.resolve(
					jsonResponse(
						{
							invitation: {
								id: "22222222-2222-2222-2222-222222222222",
								organizationId,
								email: "coach@example.com",
								role: "ADMINISTRATOR",
								status: "PENDING",
								expiresAt: new Date(Date.now() + 86400000).toISOString(),
								createdAt: new Date().toISOString(),
							},
							token: "raw-token",
						},
						201,
					),
				);
			}
			return baseFetch(url);
		});
		vi.stubGlobal("fetch", fetchMock);
		const user = userEvent.setup();

		renderWithProviders(<InvitationsPanel organizationId={organizationId} />);
		await screen.findByText(/no pending invitations/i);

		await user.type(screen.getByLabelText(/email/i), "coach@example.com");
		await user.click(screen.getByRole("button", { name: /send invitation/i }));

		await waitFor(() =>
			expect(fetchMock).toHaveBeenCalledWith(
				expect.stringContaining(`/organizations/${organizationId}/invitations`),
				expect.objectContaining({ method: "POST" }),
			),
		);
		await waitFor(() => expect(screen.getByLabelText(/email/i)).toHaveValue(""));
	});
});
