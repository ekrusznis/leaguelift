import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { EmptyState } from "../EmptyState";
import { ErrorState } from "../ErrorState";
import { LoadingState } from "../LoadingState";
import { UnauthorizedState } from "../UnauthorizedState";

describe("shared UI states", () => {
	it("renders a loading state with an accessible status role", () => {
		render(<LoadingState label="Loading organizations…" />);
		expect(screen.getByRole("status")).toHaveTextContent("Loading organizations…");
	});

	it("renders an error state and invokes retry", async () => {
		const onRetry = vi.fn();
		render(<ErrorState message="Could not load data." onRetry={onRetry} />);
		expect(screen.getByRole("alert")).toHaveTextContent("Could not load data.");
		screen.getByRole("button", { name: /try again/i }).click();
		expect(onRetry).toHaveBeenCalledTimes(1);
	});

	it("renders an empty state with title and description", () => {
		render(<EmptyState title="No organizations yet" description="Create one to get started." />);
		expect(screen.getByText("No organizations yet")).toBeInTheDocument();
		expect(screen.getByText("Create one to get started.")).toBeInTheDocument();
	});

	it("renders an unauthorized state", () => {
		render(<UnauthorizedState />);
		expect(screen.getByRole("alert")).toHaveTextContent(/don't have access/i);
	});
});
