import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { renderWithProviders } from "../../../test/testUtils";
import { CsvImportSection } from "../CsvImportSection";

describe("CsvImportSection", () => {
	afterEach(() => {
		vi.unstubAllGlobals();
	});

	it("requires a timezone before submitting", async () => {
		vi.stubGlobal(
			"fetch",
			vi.fn().mockResolvedValue(
				new Response(JSON.stringify({ items: [], page: 0, size: 20, totalElements: 0 }), {
					status: 200,
					headers: { "content-type": "application/json" },
				}),
			),
		);
		const user = userEvent.setup();

		renderWithProviders(<CsvImportSection organizationId="org-1" />);
		await user.click(screen.getByRole("button", { name: /import a schedule/i }));

		const file = new File(["external_id,event_type\nrow-1,PRACTICE\n"], "schedule.csv", { type: "text/csv" });
		await user.upload(screen.getByLabelText(/csv file/i), file);
		await user.click(screen.getByRole("button", { name: /^import$/i }));

		expect(await screen.findByRole("alert")).toHaveTextContent(/timezone is required/i);
	});

	it("submits and shows the import summary", async () => {
		const fetchMock = vi.fn().mockImplementation((url: string) => {
			if (typeof url === "string" && url.includes("csv-import")) {
				return Promise.resolve(
					new Response(
						JSON.stringify({ createdCount: 2, stagedCount: 0, unchangedCount: 0, errors: [] }),
						{ status: 200, headers: { "content-type": "application/json" } },
					),
				);
			}
			return Promise.resolve(
				new Response(JSON.stringify({ items: [], page: 0, size: 20, totalElements: 0 }), {
					status: 200,
					headers: { "content-type": "application/json" },
				}),
			);
		});
		vi.stubGlobal("fetch", fetchMock);
		const user = userEvent.setup();

		renderWithProviders(<CsvImportSection organizationId="org-1" />);
		await user.click(screen.getByRole("button", { name: /import a schedule/i }));
		await user.type(screen.getByLabelText(/timezone/i), "America/New_York");
		const file = new File(["external_id,event_type\nrow-1,PRACTICE\nrow-2,PRACTICE\n"], "schedule.csv", { type: "text/csv" });
		await user.upload(screen.getByLabelText(/csv file/i), file);
		await user.click(screen.getByRole("button", { name: /^import$/i }));

		await waitFor(() => expect(screen.getByText(/created 2, staged 0 for review, unchanged 0/i)).toBeInTheDocument());
	});
});
