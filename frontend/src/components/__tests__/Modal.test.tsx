import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { Modal } from "../Modal";

describe("Modal", () => {
	it("renders nothing when closed", () => {
		render(
			<Modal open={false} onClose={() => {}} title="Hidden">
				content
			</Modal>,
		);

		expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
	});

	it("renders the title and content when open", () => {
		render(
			<Modal open onClose={() => {}} title="Release publicly?">
				This makes the item visible outside the household.
			</Modal>,
		);

		expect(screen.getByRole("dialog", { name: "Release publicly?" })).toBeInTheDocument();
		expect(screen.getByText(/makes the item visible outside the household/i)).toBeInTheDocument();
	});

	it("calls onClose when Escape is pressed", async () => {
		const onClose = vi.fn();
		const user = userEvent.setup();
		render(
			<Modal open onClose={onClose} title="Confirm">
				content
			</Modal>,
		);

		await user.keyboard("{Escape}");

		expect(onClose).toHaveBeenCalledTimes(1);
	});

	it("calls onClose when the backdrop is clicked", async () => {
		const onClose = vi.fn();
		const user = userEvent.setup();
		const { container } = render(
			<Modal open onClose={onClose} title="Confirm">
				content
			</Modal>,
		);

		const backdrop = container.querySelector('[aria-hidden="true"]');
		if (!backdrop) throw new Error("backdrop not found");
		await user.click(backdrop);

		expect(onClose).toHaveBeenCalledTimes(1);
	});

	it("renders provided actions", () => {
		render(
			<Modal open onClose={() => {}} title="Confirm" actions={<button type="button">Confirm release</button>}>
				content
			</Modal>,
		);

		expect(screen.getByRole("button", { name: /confirm release/i })).toBeInTheDocument();
	});
});
