import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import App from "../../App";

describe("App", () => {
	it("renders the dashboard shell for an authenticated dev-mode session", async () => {
		render(<App />);
		expect(await screen.findByText(/welcome/i)).toBeInTheDocument();
		expect(screen.getByRole("link", { name: /organizations/i })).toBeInTheDocument();
	});
});
