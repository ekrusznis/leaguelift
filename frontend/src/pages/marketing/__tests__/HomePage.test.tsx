import { screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { renderWithProviders } from "../../../test/testUtils";
import { HomePage } from "../HomePage";

describe("HomePage", () => {
	it("renders the hero headline and primary conversion action", () => {
		renderWithProviders(<HomePage />);

		expect(screen.getByRole("heading", { level: 1 })).toHaveTextContent(/more revenue/i);
		expect(screen.getAllByRole("link", { name: /get started/i }).length).toBeGreaterThan(0);
	});

	it("does not surface roadmap/availability language on the public landing page", () => {
		renderWithProviders(<HomePage />);

		expect(screen.queryByText(/planned for the full platform/i)).not.toBeInTheDocument();
		expect(screen.queryByText(/\(planned\)/i)).not.toBeInTheDocument();
	});
});
