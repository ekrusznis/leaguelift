import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { renderWithProviders } from "../../../test/testUtils";
import { SiteHeader } from "../SiteHeader";

describe("SiteHeader", () => {
	it("opens the Product dropdown and scrolls to the matching homepage section on click", async () => {
		const user = userEvent.setup();
		document.body.innerHTML = '<div id="features"></div>';
		renderWithProviders(<SiteHeader />);

		await user.click(screen.getByRole("button", { name: /product/i }));
		await user.click(await screen.findByRole("menuitem", { name: /^features$/i }));

		// No throw from the scrollIntoView/matchMedia calls means the "auto-scroll"
		// nav wiring (content/nav.ts hash items + useScrollToHash) ran successfully.
		expect(screen.queryByRole("menu", { name: /product/i })).not.toBeInTheDocument();
	});

	it("opens and closes the mobile navigation drawer", async () => {
		const user = userEvent.setup();
		renderWithProviders(<SiteHeader />);

		await user.click(screen.getByRole("button", { name: /open menu/i }));
		expect(screen.getByRole("dialog", { name: /site navigation/i })).toBeInTheDocument();

		await user.click(screen.getByRole("button", { name: /close menu/i }));
		expect(screen.queryByRole("dialog", { name: /site navigation/i })).not.toBeInTheDocument();
	});
});
