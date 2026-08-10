import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import App from "../../App";

function mockFetchResponse(status: number, body: unknown) {
	return {
		status,
		ok: status >= 200 && status < 300,
		headers: { get: () => "application/json" },
		json: async () => body,
	};
}

describe("App", () => {
	it("renders the marketing homepage at the root route", async () => {
		window.history.pushState({}, "", "/");
		render(<App />);
		expect(await screen.findByRole("heading", { level: 1 })).toHaveTextContent(/more revenue/i);
	});

	describe("authenticated app shell", () => {
		it("redirects to sign-in when there is no session", async () => {
			window.history.pushState({}, "", "/app");
			render(<App />);
			expect(await screen.findByRole("heading", { name: /welcome back/i })).toBeInTheDocument();
		});

		describe("once signed in", () => {
			beforeEach(() => {
				vi.stubGlobal("fetch", vi.fn());
			});

			afterEach(() => {
				vi.unstubAllGlobals();
			});

			it("routes to the real dashboard for the signed-in user's role after sign-in", async () => {
				vi.mocked(fetch).mockImplementation((input: RequestInfo | URL) => {
					const url = typeof input === "string" ? input : input.toString();
					if (url.includes("/auth/login")) {
						return Promise.resolve(
							mockFetchResponse(200, {
								accessToken: "fake-token",
								tokenType: "Bearer",
								expiresIn: 3600,
								user: { id: "1", email: "owner@example.com", displayName: "Owner", status: "ACTIVE" },
							}) as unknown as Response,
						);
					}
					if (url.includes("/me/dashboard-context")) {
						return Promise.resolve(
							mockFetchResponse(200, { role: "OWNER", organizationId: "org-1", householdId: null }) as unknown as Response,
						);
					}
					// Every dashboard card endpoint is intentionally unmocked here — each card
					// renders its own isolated error state rather than needing every one stubbed.
					return Promise.resolve(mockFetchResponse(404, { code: "NOT_FOUND", message: "not mocked", requestId: "req_1", fieldErrors: [] }) as unknown as Response);
				});
				const user = userEvent.setup();
				window.history.pushState({}, "", "/auth/sign-in");
				render(<App />);

				await user.type(screen.getByLabelText(/email address/i), "owner@example.com");
				await user.type(screen.getByLabelText(/^password/i), "hunter2");
				await user.click(screen.getByRole("button", { name: /sign in/i }));

				expect(await screen.findByRole("heading", { name: /organization overview/i })).toBeInTheDocument();
			});
		});
	});
});
