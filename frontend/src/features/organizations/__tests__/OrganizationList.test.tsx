import { screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { renderWithProviders } from "../../../test/testUtils";
import { OrganizationList } from "../OrganizationList";

describe("OrganizationList", () => {
	afterEach(() => {
		vi.unstubAllGlobals();
	});

	it("shows an empty state when there are no organizations", async () => {
		vi.stubGlobal(
			"fetch",
			vi.fn().mockResolvedValue(
				new Response(JSON.stringify({ items: [], page: 0, size: 20, totalElements: 0 }), {
					status: 200,
					headers: { "content-type": "application/json" },
				}),
			),
		);

		renderWithProviders(<OrganizationList />);

		expect(await screen.findByText(/no organizations yet/i)).toBeInTheDocument();
	});

	it("lists organizations returned by the API", async () => {
		vi.stubGlobal(
			"fetch",
			vi.fn().mockResolvedValue(
				new Response(
					JSON.stringify({
						items: [
							{
								id: "1",
								name: "Riverside Soccer",
								slug: "riverside-soccer",
								organizationType: "RECREATIONAL_LEAGUE",
								status: "ACTIVE",
								createdAt: new Date().toISOString(),
								updatedAt: new Date().toISOString(),
							},
						],
						page: 0,
						size: 20,
						totalElements: 1,
					}),
					{ status: 200, headers: { "content-type": "application/json" } },
				),
			),
		);

		renderWithProviders(<OrganizationList />);

		await waitFor(() => expect(screen.getByText("Riverside Soccer")).toBeInTheDocument());
		expect(screen.getByText("/riverside-soccer")).toBeInTheDocument();
	});

	it("shows an error state and can retry", async () => {
		const fetchMock = vi.fn().mockResolvedValue(
			new Response(
				JSON.stringify({ code: "INTERNAL_ERROR", message: "boom", requestId: "req_1", fieldErrors: [] }),
				{ status: 500, headers: { "content-type": "application/json" } },
			),
		);
		vi.stubGlobal("fetch", fetchMock);

		renderWithProviders(<OrganizationList />);

		expect(await screen.findByRole("alert")).toHaveTextContent(/could not load organizations/i);
	});
});
