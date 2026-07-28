import { screen } from "@testing-library/react";
import { Route, Routes } from "react-router-dom";
import { describe, expect, it } from "vitest";
import { renderWithProviders } from "../../../test/testUtils";
import { NotFoundPage } from "../../NotFoundPage";
import { SolutionDetailPage } from "../SolutionDetailPage";

function renderAt(slug: string) {
	return renderWithProviders(
		<Routes>
			<Route path="/solutions/:slug" element={<SolutionDetailPage />} />
			<Route path="/404" element={<NotFoundPage />} />
		</Routes>,
		{ route: `/solutions/${slug}` },
	);
}

describe("SolutionDetailPage", () => {
	it("renders known solution content", async () => {
		renderAt("dues-and-fees");
		expect(await screen.findByRole("heading", { level: 1, name: /clear fees, without the spreadsheet/i })).toBeInTheDocument();
	});

	it("falls back to the 404 page for an unknown slug", async () => {
		renderAt("not-a-real-solution");
		expect(await screen.findByText(/that page is out of bounds/i)).toBeInTheDocument();
	});
});
