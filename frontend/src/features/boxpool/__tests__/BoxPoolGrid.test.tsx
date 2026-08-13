import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { BoxPoolGrid } from "../BoxPoolGrid";
import type { BoxPoolBox } from "../types";

const boxes: BoxPoolBox[] = [
	{ id: "1", rowIndex: 0, colIndex: 0, status: "OPEN", claimantName: null },
	{ id: "2", rowIndex: 0, colIndex: 1, status: "CLAIMED", claimantName: "Jamie Rivera" },
	{ id: "3", rowIndex: 1, colIndex: 0, status: "RESERVED", claimantName: "Pat Lee" },
];

describe("BoxPoolGrid", () => {
	it("renders every cell in the grid, labeled with its status", () => {
		render(<BoxPoolGrid rows={2} cols={2} boxes={boxes} />);

		expect(screen.getAllByRole("gridcell")).toHaveLength(4);
		expect(screen.getByRole("gridcell", { name: /row 1, column 1, open/i })).toBeInTheDocument();
		expect(screen.getByRole("gridcell", { name: /claimed by jamie rivera/i })).toBeInTheDocument();
	});

	it("only lets an open box be selected when onSelectBox is provided", async () => {
		const onSelectBox = vi.fn();
		const user = userEvent.setup();
		render(<BoxPoolGrid rows={2} cols={2} boxes={boxes} onSelectBox={onSelectBox} />);

		await user.click(screen.getByRole("gridcell", { name: /row 1, column 1, open/i }));
		expect(onSelectBox).toHaveBeenCalledWith(boxes[0]);

		expect(screen.getByRole("gridcell", { name: /claimed by jamie rivera/i })).toBeDisabled();
		expect(screen.getByRole("gridcell", { name: /row 2, column 1, reserved/i })).toBeDisabled();
	});

	it("boxes are not clickable at all without onSelectBox", () => {
		render(<BoxPoolGrid rows={1} cols={1} boxes={boxes} />);

		expect(screen.getByRole("gridcell", { name: /open/i })).toBeDisabled();
	});
});
