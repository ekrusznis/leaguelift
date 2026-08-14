import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { ListToolbar } from "../lists/ListToolbar";

const sortOptions = [
	{ value: "name", label: "Name" },
	{ value: "newest", label: "Newest" },
];

describe("ListToolbar", () => {
	it("reports search and sort changes through the shared controls", async () => {
		const user = userEvent.setup();
		const onSearchChange = vi.fn();
		const onSortChange = vi.fn();
		render(<ListToolbar searchValue="" onSearchChange={onSearchChange} sortValue="name" sortOptions={sortOptions} onSortChange={onSortChange} />);

		await user.type(screen.getByRole("searchbox"), "wolves");
		await user.selectOptions(screen.getByRole("combobox", { name: "Sort by" }), "newest");

		expect(onSearchChange).toHaveBeenCalled();
		expect(onSortChange).toHaveBeenCalledWith("newest");
	});

	it("shows a result count and clear action when filters are active", async () => {
		const user = userEvent.setup();
		const onClear = vi.fn();
		render(<ListToolbar searchValue="coach" onSearchChange={() => {}} resultCount={3} hasActiveFilters onClear={onClear} />);

		expect(screen.getByText("3 results")).toBeInTheDocument();
		await user.click(screen.getByRole("button", { name: "Clear" }));
		expect(onClear).toHaveBeenCalledTimes(1);
	});
});
