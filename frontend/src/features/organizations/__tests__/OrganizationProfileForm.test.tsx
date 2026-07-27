import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { renderWithProviders } from "../../../test/testUtils";
import { OrganizationProfileForm } from "../OrganizationProfileForm";
import type { Organization } from "../types";

const organization: Organization = {
	id: "11111111-1111-1111-1111-111111111111",
	name: "Riverside Soccer",
	slug: "riverside-soccer",
	organizationType: "RECREATIONAL_LEAGUE",
	status: "ACTIVE",
	sports: ["Soccer"],
	contactEmail: "ops@example.com",
	contactPhone: null,
	createdAt: new Date().toISOString(),
	updatedAt: new Date().toISOString(),
};

describe("OrganizationProfileForm", () => {
	afterEach(() => {
		vi.unstubAllGlobals();
	});

	it("pre-fills from the given organization and saves changes", async () => {
		const fetchMock = vi.fn().mockResolvedValue(
			new Response(JSON.stringify({ ...organization, contactPhone: "555-0100" }), {
				status: 200,
				headers: { "content-type": "application/json" },
			}),
		);
		vi.stubGlobal("fetch", fetchMock);
		const user = userEvent.setup();

		renderWithProviders(<OrganizationProfileForm organization={organization} />);

		expect(screen.getByLabelText(/organization name/i)).toHaveValue("Riverside Soccer");
		expect(screen.getByLabelText(/contact email/i)).toHaveValue("ops@example.com");

		await user.type(screen.getByLabelText(/contact phone/i), "555-0100");
		await user.click(screen.getByRole("button", { name: /save profile/i }));

		await waitFor(() =>
			expect(fetchMock).toHaveBeenCalledWith(
				expect.stringContaining(`/organizations/${organization.id}`),
				expect.objectContaining({ method: "PATCH" }),
			),
		);
	});

	it("requires at least one sport to be selected", async () => {
		const fetchMock = vi.fn();
		vi.stubGlobal("fetch", fetchMock);
		const user = userEvent.setup();

		renderWithProviders(<OrganizationProfileForm organization={{ ...organization, sports: [] }} />);
		await user.click(screen.getByRole("button", { name: /save profile/i }));

		expect(await screen.findByText(/select at least one sport/i)).toBeInTheDocument();
		expect(fetchMock).not.toHaveBeenCalled();
	});
});
