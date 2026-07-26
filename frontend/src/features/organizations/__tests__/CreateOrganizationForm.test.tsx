import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { renderWithProviders } from "../../../test/testUtils";
import { CreateOrganizationForm } from "../CreateOrganizationForm";

describe("CreateOrganizationForm", () => {
	afterEach(() => {
		vi.unstubAllGlobals();
	});

	it("shows validation errors instead of submitting when the slug is invalid", async () => {
		const fetchMock = vi.fn();
		vi.stubGlobal("fetch", fetchMock);
		const user = userEvent.setup();

		renderWithProviders(<CreateOrganizationForm />);

		await user.type(screen.getByLabelText(/organization name/i), "Riverside Soccer");
		await user.type(screen.getByLabelText(/public url slug/i), "Not A Valid Slug!");
		await user.click(screen.getByRole("button", { name: /create organization/i }));

		expect(await screen.findByText(/lowercase letters, numbers, and hyphens/i)).toBeInTheDocument();
		expect(fetchMock).not.toHaveBeenCalled();
	});

	it("submits and calls onCreated when the backend accepts the organization", async () => {
		const fetchMock = vi.fn().mockResolvedValue(
			new Response(
				JSON.stringify({
					id: "11111111-1111-1111-1111-111111111111",
					name: "Riverside Soccer",
					slug: "riverside-soccer",
					organizationType: "RECREATIONAL_LEAGUE",
					status: "ACTIVE",
					createdAt: new Date().toISOString(),
					updatedAt: new Date().toISOString(),
				}),
				{ status: 201, headers: { "content-type": "application/json" } },
			),
		);
		vi.stubGlobal("fetch", fetchMock);
		const user = userEvent.setup();
		const onCreated = vi.fn();

		renderWithProviders(<CreateOrganizationForm onCreated={onCreated} />);

		await user.type(screen.getByLabelText(/organization name/i), "Riverside Soccer");
		await user.type(screen.getByLabelText(/public url slug/i), "riverside-soccer");
		await user.click(screen.getByRole("button", { name: /create organization/i }));

		await waitFor(() => expect(onCreated).toHaveBeenCalledTimes(1));
		expect(fetchMock).toHaveBeenCalledWith(
			expect.stringContaining("/organizations"),
			expect.objectContaining({ method: "POST" }),
		);
	});
});
