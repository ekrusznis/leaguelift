import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { Pagination } from "../lists/Pagination";

describe("Pagination", () => {
	it("renders a human-readable range and navigates pages", async () => {
		const user = userEvent.setup();
		const onPageChange = vi.fn();
		render(<Pagination page={1} size={25} totalElements={83} onPageChange={onPageChange} />);

		expect(screen.getByText("Showing 26–50 of 83")).toBeInTheDocument();
		await user.click(screen.getByRole("button", { name: "Next" }));
		expect(onPageChange).toHaveBeenCalledWith(2);
	});

	it("does not render pagination for an empty result", () => {
		const { container } = render(<Pagination page={0} size={25} totalElements={0} onPageChange={() => {}} />);
		expect(container).toBeEmptyDOMElement();
	});
});
