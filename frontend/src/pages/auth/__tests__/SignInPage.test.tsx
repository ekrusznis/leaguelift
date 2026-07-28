import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { Route, Routes } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { renderWithProviders } from "../../../test/testUtils";
import { SignInPage } from "../SignInPage";

function mockFetchResponse(status: number, body: unknown) {
	return {
		status,
		ok: status >= 200 && status < 300,
		headers: { get: () => "application/json" },
		json: async () => body,
	};
}

describe("SignInPage", () => {
	beforeEach(() => {
		vi.stubGlobal("fetch", vi.fn());
	});

	afterEach(() => {
		vi.unstubAllGlobals();
	});

	it("calls the real login endpoint and only navigates once it succeeds", async () => {
		vi.mocked(fetch).mockResolvedValue(
			mockFetchResponse(200, {
				accessToken: "fake-token",
				tokenType: "Bearer",
				expiresIn: 3600,
				user: { id: "1", email: "owner@example.com", displayName: "Owner", status: "ACTIVE" },
			}) as unknown as Response,
		);
		const user = userEvent.setup();

		renderWithProviders(
			<Routes>
				<Route path="/auth/sign-in" element={<SignInPage />} />
				<Route path="/app" element={<div>Dashboard placeholder</div>} />
			</Routes>,
			{ route: "/auth/sign-in" },
		);

		await user.type(screen.getByLabelText(/email address/i), "owner@example.com");
		await user.type(screen.getByLabelText(/^password/i), "hunter2");
		await user.click(screen.getByRole("button", { name: /sign in/i }));

		expect(await screen.findByText(/dashboard placeholder/i)).toBeInTheDocument();
		expect(fetch).toHaveBeenCalledWith(
			expect.stringContaining("/auth/login"),
			expect.objectContaining({
				method: "POST",
				body: JSON.stringify({ email: "owner@example.com", password: "hunter2" }),
			}),
		);
	});

	it("does not navigate and shows an error when the backend rejects the credentials", async () => {
		vi.mocked(fetch).mockResolvedValue(
			mockFetchResponse(401, {
				code: "INVALID_CREDENTIALS",
				message: "Invalid email or password.",
				requestId: "req_test",
				fieldErrors: [],
			}) as unknown as Response,
		);
		const user = userEvent.setup();

		renderWithProviders(
			<Routes>
				<Route path="/auth/sign-in" element={<SignInPage />} />
				<Route path="/app" element={<div>Dashboard placeholder</div>} />
			</Routes>,
			{ route: "/auth/sign-in" },
		);

		await user.type(screen.getByLabelText(/email address/i), "owner@example.com");
		await user.type(screen.getByLabelText(/^password/i), "wrong-password");
		await user.click(screen.getByRole("button", { name: /sign in/i }));

		expect(await screen.findByText(/incorrect email or password/i)).toBeInTheDocument();
		expect(screen.queryByText(/dashboard placeholder/i)).not.toBeInTheDocument();
	});

	it("blocks submission and shows validation errors when fields are empty, without calling the backend", async () => {
		const user = userEvent.setup();

		renderWithProviders(
			<Routes>
				<Route path="/auth/sign-in" element={<SignInPage />} />
				<Route path="/app" element={<div>Dashboard placeholder</div>} />
			</Routes>,
			{ route: "/auth/sign-in" },
		);

		await user.click(screen.getByRole("button", { name: /sign in/i }));

		expect(await screen.findByText(/enter your email address/i)).toBeInTheDocument();
		expect(screen.queryByText(/dashboard placeholder/i)).not.toBeInTheDocument();
		expect(fetch).not.toHaveBeenCalled();
	});
});
