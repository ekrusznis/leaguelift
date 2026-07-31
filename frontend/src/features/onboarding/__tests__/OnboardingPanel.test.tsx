import { screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { renderWithProviders } from "../../../test/testUtils";
import { OnboardingPanel } from "../OnboardingPanel";

describe("OnboardingPanel", () => {
	afterEach(() => vi.unstubAllGlobals());

	it("shows preview-first import and bulk onboarding controls", async () => {
		vi.stubGlobal(
			"fetch",
			vi.fn().mockImplementation((input: RequestInfo | URL) => {
				const url = String(input);
				const body = url.includes("/documents")
					? { items: [] }
					: { items: [], page: 0, size: 500, totalElements: 0 };
				return Promise.resolve(
					new Response(JSON.stringify(body), { status: 200, headers: { "content-type": "application/json" } }),
				);
			}),
		);

		renderWithProviders(<OnboardingPanel organizationId="org-1" />);

		expect(screen.getByRole("heading", { name: "CSV onboarding import" })).toBeInTheDocument();
		expect(screen.getByRole("button", { name: "Preview import" })).toBeDisabled();
		expect(screen.getByRole("heading", { name: "Bulk staff invitations" })).toBeInTheDocument();
		expect(screen.getByText(/guardian contacts imported from csv remain household shells/i)).toBeInTheDocument();
		expect(screen.getByRole("heading", { name: "Bulk assignments" })).toBeInTheDocument();
		await waitFor(() => expect(screen.getAllByText("No records available.")).toHaveLength(3));
	});
});
