import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { renderWithProviders } from "../../../test/testUtils";
import { InvitationsPanel } from "../InvitationsPanel";

const organizationId = "11111111-1111-1111-1111-111111111111";

function emptyInvitationsResponse() {
	return new Response(JSON.stringify({ items: [], page: 0, size: 20, totalElements: 0 }), {
		status: 200,
		headers: { "content-type": "application/json" },
	});
}

describe("InvitationsPanel", () => {
	afterEach(() => {
		vi.unstubAllGlobals();
	});

	it("shows an empty state when there are no pending invitations", async () => {
		vi.stubGlobal("fetch", vi.fn().mockResolvedValue(emptyInvitationsResponse()));

		renderWithProviders(<InvitationsPanel organizationId={organizationId} />);

		expect(await screen.findByText(/no pending invitations/i)).toBeInTheDocument();
	});

	it("sends an invitation and clears the form", async () => {
		const fetchMock = vi.fn().mockImplementation((_url: string, options?: RequestInit) => {
			if (options?.method === "POST") {
				return Promise.resolve(
					new Response(
						JSON.stringify({
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
						}),
						{ status: 201, headers: { "content-type": "application/json" } },
					),
				);
			}
			return Promise.resolve(emptyInvitationsResponse());
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
