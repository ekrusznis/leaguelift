import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { renderWithProviders } from "../../../test/testUtils";
import { OrganizationProfileForm } from "../OrganizationProfileForm";
import type { Organization } from "../types";

function jsonResponse(body: unknown, status = 200) {
	return new Response(JSON.stringify(body), { status, headers: { "content-type": "application/json" } });
}

const organization: Organization = {
	id: "11111111-1111-1111-1111-111111111111",
	name: "Riverside Soccer",
	slug: "riverside-soccer",
	organizationType: "RECREATIONAL_LEAGUE",
	status: "ACTIVE",
	sports: ["Soccer"],
	contactEmail: "ops@example.com",
	contactPhone: null,
	addressLine1: null,
	addressLine2: null,
	addressCity: null,
	addressState: null,
	addressPostalCode: null,
	addressCountry: null,
	timezone: null,
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

	it("shows a suggested timezone banner and fills the field only when the owner clicks Use this", async () => {
		const fetchMock = vi.fn((url: string) => {
			if (url.includes("/timezone-suggestion")) return Promise.resolve(jsonResponse({ timezone: "America/Chicago" }));
			return Promise.resolve(jsonResponse(organization));
		});
		vi.stubGlobal("fetch", fetchMock);
		const user = userEvent.setup();

		renderWithProviders(<OrganizationProfileForm organization={organization} />);

		expect(screen.getByLabelText(/^timezone$/i)).toHaveValue("");
		expect(await screen.findByText(/suggested timezone based on your address/i)).toBeInTheDocument();
		expect(screen.getByText("America/Chicago")).toBeInTheDocument();

		await user.click(screen.getByRole("button", { name: /use this/i }));

		expect(screen.getByLabelText(/^timezone$/i)).toHaveValue("America/Chicago");
	});

	it("does not show a suggestion banner once the organization already has a confirmed timezone", async () => {
		const fetchMock = vi.fn().mockResolvedValue(jsonResponse(organization));
		vi.stubGlobal("fetch", fetchMock);

		renderWithProviders(<OrganizationProfileForm organization={{ ...organization, timezone: "America/New_York" }} />);

		expect(screen.getByLabelText(/^timezone$/i)).toHaveValue("America/New_York");
		await waitFor(() => expect(screen.queryByText(/suggested timezone based on your address/i)).not.toBeInTheDocument());
		expect(fetchMock).not.toHaveBeenCalledWith(expect.stringContaining("/timezone-suggestion"));
	});

	it("requires at least one sport to be selected", async () => {
		const fetchMock = vi.fn().mockResolvedValue(
			new Response(JSON.stringify({ timezone: null }), { status: 200, headers: { "content-type": "application/json" } }),
		);
		vi.stubGlobal("fetch", fetchMock);
		const user = userEvent.setup();

		renderWithProviders(<OrganizationProfileForm organization={{ ...organization, sports: [] }} />);
		await user.click(screen.getByRole("button", { name: /save profile/i }));

		expect(await screen.findByText(/select at least one sport/i)).toBeInTheDocument();
		expect(fetchMock).not.toHaveBeenCalledWith(expect.anything(), expect.objectContaining({ method: "PATCH" }));
	});
});
