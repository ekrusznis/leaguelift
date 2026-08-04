import { screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { renderWithProviders } from "../../../test/testUtils";
import { AppFooter } from "../AppFooter";

describe("AppFooter", () => {
	it("shows help, support, legal, copyright, and a public home logo", () => {
		renderWithProviders(<AppFooter authenticated={false} />);

		expect(screen.getByRole("link", { name: "Rally26 home" })).toHaveAttribute("href", "/");
		expect(screen.getByRole("link", { name: "Help Center" })).toHaveAttribute("href", "/help");
		expect(screen.getByRole("link", { name: "Support" })).toHaveAttribute("href", "/help/support");
		expect(screen.getByRole("link", { name: /privacy/i })).toBeInTheDocument();
		expect(screen.getByRole("link", { name: /terms/i })).toBeInTheDocument();
		expect(screen.getByText(/Rally26\. All rights reserved\./)).toBeInTheDocument();
	});

	it("uses authenticated help routes inside the app", () => {
		renderWithProviders(<AppFooter authenticated />);

		expect(screen.getByRole("link", { name: "Rally26 home" })).toHaveAttribute("href", "/app");
		expect(screen.getByRole("link", { name: "Help Center" })).toHaveAttribute("href", "/app/help");
		expect(screen.getByRole("link", { name: "Support" })).toHaveAttribute("href", "/app/help/support");
	});
});
