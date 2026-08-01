import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { SupportMarkdown } from "../SupportMarkdown";

describe("SupportMarkdown", () => {
	it("renders the supported formatting subset without interpreting raw HTML", () => {
		render(<SupportMarkdown body={"## Setup\n\n- First step\n- **Second step**\n\n<script>alert('x')</script>"} />);

		expect(screen.getByRole("heading", { name: "Setup" })).toBeInTheDocument();
		expect(screen.getByText("Second step").tagName).toBe("STRONG");
		expect(screen.getByText("<script>alert('x')</script>")).toBeInTheDocument();
		expect(document.querySelector("script")).toBeNull();
	});

	it("neutralizes unsupported link protocols", () => {
		render(<SupportMarkdown body={"[Unsafe](javascript:alert('x')) and [Safe](/help)"} />);

		expect(screen.getByRole("link", { name: "Unsafe" })).toHaveAttribute("href", "#");
		expect(screen.getByRole("link", { name: "Safe" })).toHaveAttribute("href", "/help");
	});
});
