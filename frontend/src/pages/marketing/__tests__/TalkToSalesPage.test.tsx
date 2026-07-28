import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { renderWithProviders } from "../../../test/testUtils";
import { TalkToSalesPage } from "../TalkToSalesPage";

describe("TalkToSalesPage", () => {
	it("blocks submission with validation errors when required fields are missing", async () => {
		const user = userEvent.setup();
		renderWithProviders(<TalkToSalesPage />);

		await user.click(screen.getByRole("button", { name: /submit request/i }));

		expect(await screen.findByText(/first name is required/i)).toBeInTheDocument();
		expect(screen.getByText(/select at least one area of interest/i)).toBeInTheDocument();
		expect(screen.queryByText(/^reference:/i)).not.toBeInTheDocument();
	});

	it("shows a success confirmation once all required fields are valid", async () => {
		const user = userEvent.setup();
		renderWithProviders(<TalkToSalesPage />);

		await user.type(screen.getByLabelText(/first name/i), "Jamie");
		await user.type(screen.getByLabelText(/last name/i), "Rivera");
		await user.type(screen.getByLabelText(/work email/i), "jamie@example.com");
		await user.type(screen.getByLabelText(/phone number/i), "555-0100");
		await user.selectOptions(screen.getByLabelText(/your role/i), "ORGANIZATION_OWNER");
		await user.type(screen.getByLabelText(/organization name/i), "Riverside Soccer Club");
		await user.selectOptions(screen.getByLabelText(/organization type/i), "RECREATIONAL_LEAGUE");
		await user.type(screen.getByLabelText(/^city/i), "Riverside");
		await user.type(screen.getByLabelText(/^state/i), "CA");
		await user.type(screen.getByLabelText(/sport or sports/i), "Soccer");
		await user.click(screen.getByRole("checkbox", { name: /team pages/i }));
		await user.click(screen.getByRole("checkbox", { name: /i consent to be contacted/i }));
		await user.click(screen.getByRole("checkbox", { name: /at least 18 years old/i }));
		await user.click(screen.getByRole("checkbox", { name: /agree to the privacy policy/i }));

		await user.click(screen.getByRole("button", { name: /submit request/i }));

		expect(await screen.findByText(/thank you, riverside soccer club/i)).toBeInTheDocument();
		expect(screen.getByText(/^reference:/i)).toBeInTheDocument();
	});
});
