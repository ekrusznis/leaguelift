import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { renderWithProviders } from "../../../test/testUtils";
import { TalkToSalesPage } from "../TalkToSalesPage";

function jsonResponse(body: unknown, status = 200) {
	return new Response(JSON.stringify(body), { status, headers: { "content-type": "application/json" } });
}

describe("TalkToSalesPage", () => {
	afterEach(() => {
		vi.unstubAllGlobals();
	});

	it("blocks submission with validation errors when required fields are missing", async () => {
		const user = userEvent.setup();
		renderWithProviders(<TalkToSalesPage />);

		await user.click(screen.getByRole("button", { name: /submit request/i }));

		expect(await screen.findByText(/name must be at least 2 characters/i)).toBeInTheDocument();
		expect(screen.getByText(/organization name is required/i)).toBeInTheDocument();
	});

	it("submits through the real public support-case endpoint and shows a success confirmation", async () => {
		const fetchMock = vi.fn((url: string, init?: RequestInit) => {
			if (init?.method === "POST" && url.includes("/public/support-cases")) {
				return Promise.resolve(jsonResponse({ id: "case-1", status: "OPEN" }));
			}
			return Promise.resolve(jsonResponse({}));
		});
		vi.stubGlobal("fetch", fetchMock);
		const user = userEvent.setup();

		renderWithProviders(<TalkToSalesPage />);

		await user.type(screen.getByLabelText(/your name/i), "Jamie Rivera");
		await user.type(screen.getByLabelText(/work email/i), "jamie@example.com");
		await user.type(screen.getByLabelText(/organization name/i), "Riverside Soccer Club");
		await user.type(screen.getByLabelText(/what are you looking for/i), "About 6 teams, currently using spreadsheets for dues.");
		await user.click(screen.getByRole("button", { name: /submit request/i }));

		await waitFor(() =>
			expect(fetchMock).toHaveBeenCalledWith(expect.stringContaining("/public/support-cases"), expect.objectContaining({ method: "POST" })),
		);
		const call = fetchMock.mock.calls.find(([, init]) => (init as RequestInit | undefined)?.method === "POST");
		expect(call).toBeDefined();
		const body = JSON.parse((call![1] as RequestInit).body as string);
		expect(body.requesterEmail).toBe("jamie@example.com");
		expect(body.subject).toContain("Riverside Soccer Club");

		expect(await screen.findByText(/your request has been received/i)).toBeInTheDocument();
	});
});
